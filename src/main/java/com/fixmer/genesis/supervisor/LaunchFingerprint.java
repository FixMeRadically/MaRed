package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Bounded build/runtime input manifests. Never fingerprints a game directory recursively
 * merely because it occurs in java.library.path. No launcher arguments enter diagnostics. */
final class LaunchFingerprint {
    record Checked(List<String> changes) { }
    private record Root(String key,Path path,boolean nativeOnly) { }
    private final long deadline=System.nanoTime()+2_000_000_000L;
    private final Map<Path,String> files=new HashMap<>();
    private final Set<Path> entries=new HashSet<>();
    private final Path project;
    private final boolean metadata;
    private long bytes;
    private final byte[] buffer=new byte[32768];
    private LaunchFingerprint(Properties p,boolean metadata)throws IOException {
        this.project=Path.of(required(p,"projectDir")).toAbsolutePath().normalize();this.metadata=metadata;
    }
    static String metadata(Properties p)throws IOException {
        var scan=new LaunchFingerprint(p,true);var md=digest();
        for(Root root:roots(p,false)){add(md,root.path().toString());add(md,scan.root(root));}
        return hex(md);
    }
    // Use the same content policy as replay. Timestamp-only rewrites of project
    // outputs do not change their meaning; native extraction is not a build input.
    static void captureBaseline(Properties p)throws IOException {
        var scan=new LaunchFingerprint(p,false);
        for(Root root:roots(p,false))p.setProperty(root.key()+".baselineHash",scan.root(root));
        p.setProperty("baseline.version","content-21");
    }
    static Checked checkBaseline(Properties p)throws IOException {
        var scan=new LaunchFingerprint(p,false);List<String> changes=new ArrayList<>();
        for(Root root:roots(p,false))if(!scan.root(root).equals(required(p,root.key()+".baselineHash"))&&changes.size()<8&&!changes.contains(label(scan.project,root)))
            changes.add(label(scan.project,root));
        return new Checked(List.copyOf(changes));
    }
    static void capture(Properties p)throws IOException {
        var scan=new LaunchFingerprint(p,false);
        for(Root root:roots(p,true))p.setProperty(root.key()+".hash",scan.root(root));
        p.setProperty("fingerprint.version","15");
    }
    static Checked check(Properties p)throws IOException {
        var scan=new LaunchFingerprint(p,false);List<String> changes=new ArrayList<>();
        // Still scan all inputs: limits and linked-file rejection remain enforced on a mismatch.
        for(Root root:roots(p,true))if(!scan.root(root).equals(required(p,root.key()+".hash"))&&changes.size()<8&&!changes.contains(label(scan.project,root)))
            changes.add(label(scan.project,root));
        return new Checked(List.copyOf(changes));
    }
    private static List<Root> roots(Properties p,boolean natives)throws IOException {
        List<Root> out=new ArrayList<>();
        for(String prefix:natives?List.of("input","native"):List.of("input")){
            int count;
            try{count=Integer.parseInt(p.getProperty(prefix+".count",prefix.equals("native")?"0":"-1"));}
            catch(NumberFormatException e){throw new IOException("Invalid count");}
            if(count<0||count>4096)throw new IOException("Invalid count");
            for(int i=0;i<count;i++)out.add(new Root(prefix+"."+i,Path.of(required(p,prefix+"."+i)).toAbsolutePath().normalize(),prefix.equals("native")));
        }
        // Stable even when Gradle input collection order changes.
        out.sort(Comparator.comparing((Root r)->r.path().toString()).thenComparing(Root::key));return out;
    }
    private String root(Root root)throws IOException {
        tick();RuntimeFiles.safe(root.path());var md=digest();add(md,root.nativeOnly()?"native":"build");add(md,root.path().toString());
        if(!Files.exists(root.path(),LinkOption.NOFOLLOW_LINKS)){add(md,"missing");return hex(md);}
        SortedSet<Path> paths=new TreeSet<>(Comparator.comparing(Path::toString));
        if(root.nativeOnly()&&Files.isDirectory(root.path(),LinkOption.NOFOLLOW_LINKS)){
            // The JVM searches these directories for native binaries; game saves/config/logs
            // and nested script projects are not native library search inputs.
            add(md,"native-directory");register(root.path());
            try(var children=Files.newDirectoryStream(root.path())){
                for(Path path:children){tick();register(path);if(nativeName(path))paths.add(path);}
            }
        }else if(root.nativeOnly()){
            add(md,"not-directory");paths.add(root.path());register(root.path());
        }else{
            try(var walk=Files.walk(root.path())){
                var it=walk.iterator();while(it.hasNext()){tick();Path path=it.next();register(path);paths.add(path);}
            }
        }
        for(Path path:paths){add(md,path.toString());add(md,file(path));}
        return hex(md);
    }
    private void register(Path path)throws IOException {if(entries.add(path)&&entries.size()>30000)throw new IOException("FINGERPRINT_FILE_LIMIT");}
    private String file(Path path)throws IOException {
        tick();String cached=files.get(path);if(cached!=null)return cached;
        if(Files.isSymbolicLink(path))throw new IOException("LINKED_INPUT");
        var md=digest();
        if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))add(md,"dir");
        else{
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid input type");
            long size=Files.size(path);add(md,Long.toString(size));
            // Project archives are often rewritten identically by Gradle. Hash their bytes;
            // dependency archives outside the project retain the previous metadata policy.
            boolean jar=path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
            if(metadata||(jar&&!path.startsWith(project)))add(md,Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toString());
            else{
                bytes+=size;if(bytes>96L*1024*1024)throw new IOException("FINGERPRINT_BYTE_LIMIT");
                try(InputStream in=Files.newInputStream(path)){int n;while((n=in.read(buffer))!=-1){tick();md.update(buffer,0,n);}}
            }
        }
        String value=hex(md);files.put(path,value);return value;
    }
    private void tick()throws IOException {if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("FINGERPRINT_TIME_LIMIT");}
    private static boolean nativeName(Path path){String n=path.getFileName().toString().toLowerCase(Locale.ROOT);return n.endsWith(".dll")||n.endsWith(".so")||n.contains(".so.")||n.endsWith(".dylib")||n.endsWith(".jnilib");}
    private static String label(Path project,Root root){
        Path path=root.path();String value=(root.nativeOnly()?"native: ":"")+(path.startsWith(project)?project.relativize(path).toString():"external/"+path.getFileName());
        value=value.replace('\\','/').replaceAll("[\\p{Cntrl}]","?");return value.length()>230?value.substring(0,227)+"...":value;
    }
    private static String required(Properties p,String key)throws IOException {String value=p.getProperty(key);if(value==null||value.isBlank())throw new IOException("Missing cache field");return value;}
    private static MessageDigest digest()throws IOException {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}}
    private static String hex(MessageDigest md){return HexFormat.of().formatHex(md.digest());}
    private static void add(MessageDigest md,String value){md.update(value.getBytes(StandardCharsets.UTF_8));md.update((byte)0);}
}
