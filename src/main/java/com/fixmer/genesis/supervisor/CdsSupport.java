package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
/** Default CDS is best-effort. Dynamic AppCDS is opt-in and only supports explicit JAR launch inputs. */
final class CdsSupport {
    record Session(Path recording,Path archive,boolean custom) {
        static Session none() {
            return new Session(null,null,false);
        }
        void finished(int exitCode,SupervisorLog log) {
            if(recording==null)return;
            try {
                RuntimeFiles.safe(recording);
                RuntimeFiles.safe(archive);
                if(exitCode==0&&Files.isRegularFile(recording)&&Files.size(recording)>0&&Files.size(recording)<=512L*1024*1024) {
                    try {
                        Files.move(recording,archive,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch(AtomicMoveNotSupportedException e) {
                        Files.move(recording,archive,StandardCopyOption.REPLACE_EXISTING);
                    }
                    log.info("[cds] Dynamic archive recorded; eligible classes only, game/mod coverage must be measured");
                }
                else log.info("[cds] No clean dynamic archive produced; continuing with default CDS next time");
                Files.deleteIfExists(recording);
            }
            catch(IOException e) {
                log.warn("CDS finalization skipped: "+e.getMessage());
            }
        }
    }
    static Session apply(SupervisorConfig config,List<String> command,Map<String,String> env,boolean bypass,SupervisorLog log)throws IOException {
        return apply(config,command,env,bypass,log,config.workDir());
    }
    static Session apply(SupervisorConfig config,List<String> command,Map<String,String> env,boolean bypass,SupervisorLog log,Path workDir)throws IOException {
        return apply(config,command,env,bypass,log,workDir,StartupCompatibility.read(config.gameDir()).experimental());
    }
    static Session apply(SupervisorConfig config,List<String> command,Map<String,String> env,boolean bypass,SupervisorLog log,Path workDir,boolean experimentsAllowed)throws IOException {
        env.put("GENESIS_CDS_MODE",config.cdsMode().equals("off")?"off":"default");
        String name=Path.of(command.get(0)).getFileName().toString().toLowerCase(Locale.ROOT);
        if(!name.equals("java")&&!name.equals("java.exe")&&!name.equals("javaw.exe")) {
            log.info("[cds] Wrapper launch: built-in CDS hint only; AppCDS flags are not injected into Gradle JVMs");
            return Session.none();
        }
        boolean inherited=java.util.stream.Stream.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS") .map(key->env.getOrDefault(key,System.getenv(key))).filter(Objects::nonNull).anyMatch(value->value.contains("-Xshare:")||value.contains("-XX:SharedArchiveFile=")||value.contains("-XX:ArchiveClassesAtExit="));
        if(inherited) {
            log.info("[cds] Inherited CDS tuning preserved");
            return Session.none();
        }
        if(command.stream().anyMatch(a->a.startsWith("-Xshare:")||a.startsWith("-XX:SharedArchiveFile=")||a.startsWith("-XX:ArchiveClassesAtExit="))) {
            log.info("[cds] User-supplied CDS flags preserved");
            return Session.none();
        }
        if(config.cdsMode().equals("off"))return Session.none();
        if(!experimentsAllowed){log.info("[startup-compat] Genesis CDS flags suppressed; launcher/JDK defaults retained");return Session.none();}
        command.add(1,"-Xshare:auto");
        if(!config.cdsMode().equals("dynamic")||bypass)return Session.none();
        try {
            // Keep caches out of opaque launcher argfiles and directory classpaths. The JDK cannot safely reuse them.
            for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS")) {
                String value=env.getOrDefault(key,System.getenv(key));
                if(value!=null&&(value.contains("javaagent:")||value.contains("agentlib:")||value.contains("agentpath:")))throw new IOException("inherited transforming JVM options");
            }
            List<Path> inputs=new ArrayList<>();
            boolean launch=false;
            for(int i=2;i<command.size();i++) {
                String a=command.get(i);
                if(a.startsWith("@")||a.startsWith("-javaagent:")||a.startsWith("-agentpath:")||a.startsWith("-agentlib:")||a.startsWith("-Djava.system.class.loader=")||a.startsWith("--patch-module"))throw new IOException("opaque/transforming JVM inputs");
                if(Set.of("-cp","-classpath","--class-path","-p","--module-path").contains(a)) {
                    if(++i>=command.size())throw new IOException("missing path argument");
                    for(String part:command.get(i).split(java.util.regex.Pattern.quote(File.pathSeparator),-1))inputs.add(jar(workDir.resolve(part)));
                }
                else if(a.equals("-jar")) {
                    if(++i>=command.size())throw new IOException("missing main JAR");
                    inputs.add(jar(workDir.resolve(command.get(i))));
                    launch=true;
                }
                else if(a.startsWith("--class-path=")||a.startsWith("--module-path="))throw new IOException("use explicit path arguments for dynamic CDS");
                else if(!a.startsWith("-")&&!launch) {
                    launch=true;
                }
            }
            if(inputs.isEmpty())throw new IOException("no explicit JAR classpath");
            Path executable=Path.of(command.get(0));
            if(!executable.isAbsolute())throw new IOException("dynamic CDS requires absolute Java executable");
            Path home=executable.getParent().getParent();
            inputs.add(home.resolve("release"));
            inputs.add(executable);
            Path mods=config.gameDir().resolve("mods");
            RuntimeFiles.safe(mods);
            if(Files.isDirectory(mods))try(var files=Files.newDirectoryStream(mods,"*.jar")) {
                for(Path p:files) {
                    if(inputs.size()>512)throw new IOException("archive input count limit");
                    inputs.add(jar(p));
                }
            }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(String arg:command) {
                digest.update(arg.getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);
            }
            inputs.sort(Comparator.comparing(Path::toString));
            long[] remaining= {
                512L*1024*1024
            }
            ;
            long deadline=System.nanoTime()+2_000_000_000L;
            for(Path input:inputs) {
                RuntimeFiles.safe(input);
                digest.update(input.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
                if(!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS))throw new IOException("missing CDS input");
                try(var in=Files.newInputStream(input)) {
                    byte[] buffer=new byte[65536];
                    int n;
                    while((n=in.read(buffer))!=-1) {
                        if((remaining[0]-=n)<0||System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("CDS fingerprint budget exceeded");
                        digest.update(buffer,0,n);
                    }
                }
                digest.update((byte)0);
            }
            String key=HexFormat.of().formatHex(digest.digest());
            Path root=config.gameDir().resolve("config/mared/dev/cds");
            RuntimeFiles.safe(root);
            Files.createDirectories(root);
            Path archive=root.resolve(key+".jsa"),recording=root.resolve(key+".recording.jsa");
            RuntimeFiles.safe(archive);
            RuntimeFiles.safe(recording);
            // Bound old cache growth, removing only our exact hash-named archives.
            try(var files=Files.list(root)) {
                var old=files.filter(p->p.getFileName().toString().matches("[0-9a-f]{64}(?:\\.recording)?\\.jsa")&&!p.equals(archive)&&!p.equals(recording)).sorted(Comparator.comparing(Path::toString)).toList();
                for(int i=0;i<Math.max(0,old.size()-3);i++) {
                    RuntimeFiles.safe(old.get(i));
                    Files.deleteIfExists(old.get(i));
                }
            }
            if(Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS)&&Files.size(archive)>0&&Files.size(archive)<=512L*1024*1024) {
                command.add(1,"-XX:SharedArchiveFile="+archive);
                log.info("[cds] Using matching dynamic archive");
                return new Session(null,archive,true);
            }
            Files.deleteIfExists(recording);
            command.add(1,"-XX:ArchiveClassesAtExit="+recording);
            log.info("[cds] Recording dynamic archive on clean exit (not a guarantee of Minecraft/mod class coverage)");
            return new Session(recording,archive,true);
        }
        catch(IOException|GeneralSecurityException e) {
            log.info("[cds] Dynamic archive skipped: "+e.getMessage()+"; default CDS remains available");
            return Session.none();
        }
    }
    private static Path jar(Path p)throws IOException {
        p=p.toAbsolutePath().normalize();
        RuntimeFiles.safe(p);
        if(!p.toString().toLowerCase(Locale.ROOT).endsWith(".jar")||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("non-JAR or directory classpath");
        return p;
    }
}
