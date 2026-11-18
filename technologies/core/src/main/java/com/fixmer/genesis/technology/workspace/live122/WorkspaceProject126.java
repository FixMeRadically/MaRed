package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121;
import com.fixmer.genesis.technology.scripts.NeoForgeProjectTemplate;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/** Bounded text-only import. No executable is launched and no existing source is replaced. */
public final class WorkspaceProject126 {
    public static final int MAX_PACKED=20000,MAX_RAW=2*1024*1024;
    private static final Set<String> OUTPUTS=Set.of("build","out","run","runs","target","node_modules","world","logs","crash-reports");
    private static final Set<String> EXTENSIONS=Set.of("mr","mcfunction","java","json","toml","properties","gradle","kts","mcmeta","txt","md","cfg","xml","csv","yml","yaml","lang");
    private WorkspaceProject126(){}
    public static Path wrapper(Path root)throws IOException{
        root=FirstLaunchFiles.safe(root);Files.createDirectories(root);FirstLaunchFiles.privateMode(root);
        var files=Map.of("gradle-wrapper.jar",NeoForgeProjectTemplate.wrapperJar(),"gradle-wrapper.properties",NeoForgeProjectTemplate.wrapperProperties().getBytes(StandardCharsets.UTF_8));
        for(var e:files.entrySet()){Path p=FirstLaunchFiles.safe(root.resolve(e.getKey()));if(!Files.exists(p))FirstLaunchFiles.write(p,e.getValue(),false);if(!Arrays.equals(e.getValue(),FirstLaunchFiles.read(p,1024*1024)))throw new IOException("WRAPPER126_CHANGED");}
        Path jar=root.resolve("gradle-wrapper.jar");com.fixmer.genesis.technology.scripts.BuildTools.validateWrapper(jar);return jar;
    }
    public record Scan(Map<String,String> files,List<String> skipped){public Scan{files=Map.copyOf(files);skipped=List.copyOf(skipped);}}
    public static Map<String,String> template(String id){
        var result=new TreeMap<String,String>();for(var e:NeoForgeProjectTemplate.files(id).entrySet()){
            if(e.getKey().equals("gradlew")||e.getKey().equals("gradlew.bat"))continue;
            try{WorkspacePolicy121.path(e.getKey());}catch(IllegalArgumentException ignored){continue;}
            if(e.getValue().codePointCount(0,e.getValue().length())>16384)continue;
            result.put(e.getKey(),e.getValue().replace("neo_version=21.1.235","neo_version=21.1.264").replace("21.1.235, matching MaRed","21.1.264, matching MaRed"));
        }result.put("GENESIS-MDK.md","# Shared NeoForge mod\n\nCreated from the bundled official NeoForge MDK snapshot.\nUse Workspace review to approve files, then Build / publish. Leave Gradle Wrapper blank to use the bundled wrapper. JDK 21 is required; the first build may download dependencies.\n\nThis is a text-only shared project. Shell launchers and the binary wrapper are provided separately by the client build tools; they are not shared source files. NeoForge baseline: 21.1.264.\n");return Map.copyOf(result);
    }
    private static void valid(Map<String,String> files){
        if(files.size()>64)throw new IllegalArgumentException("LIMIT");var seen=new HashSet<String>();long total=0;
        for(var e:new TreeMap<>(files).entrySet()){
            String p=WorkspacePolicy121.path(e.getKey()).toLowerCase(Locale.ROOT);if(!seen.add(p))throw new IllegalArgumentException("PATH_COLLISION");
            for(String prior:seen)if(!prior.equals(p)&&(p.startsWith(prior+"/")||prior.startsWith(p+"/")))throw new IllegalArgumentException("PATH_COLLISION");
            String text=e.getValue();if(text==null||text.indexOf(0)>=0||text.codePointCount(0,text.length())>16384)throw new IllegalArgumentException("TEXT_LIMIT");
            // Also reject unpaired UTF-16 surrogates before canonical UTF-8 encoding.
            try{StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));}catch(CharacterCodingException bad){throw new IllegalArgumentException("UTF8",bad);}
            total+=text.getBytes(StandardCharsets.UTF_8).length;if(total>MAX_RAW)throw new IllegalArgumentException("LIMIT");
        }
    }
    public static String pack(Map<String,String> files)throws IOException{
        valid(files);var raw=new ByteArrayOutputStream();try(var out=new DataOutputStream(raw)){out.writeInt(files.size());for(var e:new TreeMap<>(files).entrySet()){out.writeUTF(e.getKey());byte[] text=e.getValue().getBytes(StandardCharsets.UTF_8);out.writeInt(text.length);out.write(text);}}
        if(raw.size()>MAX_RAW)throw new IOException("LIMIT");var packed=new ByteArrayOutputStream();try(var z=new DeflaterOutputStream(packed)){z.write(raw.toByteArray());}
        if(packed.size()>MAX_PACKED)throw new IOException("IMPORT126_PACKET_LIMIT");return Base64.getEncoder().encodeToString(packed.toByteArray());
    }
    public static Map<String,String> unpack(String data)throws IOException{
        if(data==null||data.length()>4*((MAX_PACKED+2)/3))throw new IOException("LIMIT");byte[] packed;
        try{packed=Base64.getDecoder().decode(data);}catch(IllegalArgumentException bad){throw new IOException("FORMAT",bad);}if(packed.length>MAX_PACKED||!Base64.getEncoder().encodeToString(packed).equals(data))throw new IOException("FORMAT");
        byte[] raw=WorkspaceCodec122.inflate142(packed,MAX_RAW,"LIMIT");
        var result=new TreeMap<String,String>();try(var in=new DataInputStream(new ByteArrayInputStream(raw))){int count=in.readInt();if(count<0||count>64)throw new IOException("LIMIT");for(int n=0;n<count;n++){String path=in.readUTF();int length=in.readInt();if(length<0||length>65536)throw new IOException("TEXT_LIMIT");byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException();String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();if(result.put(path,text)!=null)throw new IOException("FORMAT");}if(in.read()!=-1)throw new IOException("FORMAT");}valid(result);return Map.copyOf(result);
    }
    public static Scan scan(Path source)throws IOException{
        source=FirstLaunchFiles.safe(source);if(!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("DIRECTORY");final Path root=source;var files=new TreeMap<String,String>();var skipped=new ArrayList<String>();var visited=new int[1];
        Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),24,new SimpleFileVisitor<>(){
            private String relative(Path p){return root.relativize(p).toString().replace('\\','/');}
            private void skip(String path){if(skipped.size()<128)skipped.add(path);}
            @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException{
                if(++visited[0]>4096)throw new IOException("IMPORT126_SCAN_LIMIT");if(p.equals(root))return FileVisitResult.CONTINUE;
                String name=p.getFileName().toString();if(name.startsWith(".")||OUTPUTS.contains(name.toLowerCase(Locale.ROOT))){skip(relative(p)+"/");return FileVisitResult.SKIP_SUBTREE;}FirstLaunchFiles.safe(p);return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException{
                if(++visited[0]>4096)throw new IOException("IMPORT126_SCAN_LIMIT");String path=relative(p);String name=p.getFileName().toString();int dot=name.lastIndexOf('.');
                if(!a.isRegularFile()||a.isSymbolicLink()||dot<0||!EXTENSIONS.contains(name.substring(dot+1).toLowerCase(Locale.ROOT))){skip(path);return FileVisitResult.CONTINUE;}
                try{WorkspacePolicy121.path(path);}catch(IllegalArgumentException bad){skip(path);return FileVisitResult.CONTINUE;}
                FirstLaunchFiles.safe(p);if(a.size()>65536)throw new IOException("TEXT_LIMIT: "+path);byte[] bytes=FirstLaunchFiles.read(p,65536);
                String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException bad){throw new IOException("UTF8: "+path,bad);}
                if(files.put(path,text)!=null||files.size()>64)throw new IOException("LIMIT");return FileVisitResult.CONTINUE;
            }
        });valid(files);pack(files);return new Scan(files,skipped);
    }
}
