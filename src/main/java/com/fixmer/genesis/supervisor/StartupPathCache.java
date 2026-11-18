package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** An opt-in, direct-launch-only experiment. Cache validation always runs first. */
final class StartupPathCache {
    static boolean apply(Path game,String mode,List<String> command,Map<String,String> environment,String launch,boolean customCds,SupervisorLog log) {
        return apply(game,mode,command,environment,launch,customCds,log,StartupCompatibility.read(game).experimental());
    }
    static boolean apply(Path game,String mode,List<String> command,Map<String,String> environment,String launch,boolean customCds,SupervisorLog log,boolean experimentsAllowed) {
        if(!experimentsAllowed){log.info("[startup-compat] Experimental startup agent suppressed");return false;}
        try {
            Path settings=game.resolve("config/mared/dev/startup.properties");
            if(!Files.isRegularFile(settings,LinkOption.NOFOLLOW_LINKS))return false;
            RuntimeFiles.safe(settings);if(Files.size(settings)>65536)return false;
            Properties options=new Properties();try(var reader=Files.newBufferedReader(settings)){options.load(reader);}
            boolean pathCache="true".equalsIgnoreCase(options.getProperty("pathFilterCache.enabled","false"));
            boolean accessIndex="true".equalsIgnoreCase(options.getProperty("accessIndex.enabled","false"));
            boolean fontSizing="true".equalsIgnoreCase(options.getProperty("fontSizing.enabled","false"));
            if(!pathCache&&!accessIndex&&!fontSizing)return false;
            if(!"direct_cache".equals(mode)||!Path.of("").getClass().getName().equals("sun.nio.fs.WindowsPath")||customCds)return false;
            if(!launch.matches("[0-9a-f-]{36}")||command.isEmpty())return false;
            Path executable=Path.of(command.get(0));
            if(!executable.isAbsolute()||!Files.isRegularFile(executable)||!Set.of("java.exe","javaw.exe").contains(executable.getFileName().toString().toLowerCase(Locale.ROOT)))return false;
            if(command.stream().anyMatch(StartupPathCache::conflict))return false;
            for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS")) {
                String value=environment.getOrDefault(key,System.getenv(key));
                if(value!=null&&(value.contains("javaagent:")||value.contains("agentlib:")||value.contains("agentpath:")||value.contains("SharedArchiveFile")||value.contains("ArchiveClassesAtExit")))return false;
            }
            byte[] bytes;try(InputStream in=StartupPathCache.class.getResourceAsStream("/mared/dev/startup-agent.jar")) {
                if(in==null)return false;bytes=in.readNBytes(1024*1024+1);if(bytes.length>1024*1024)return false;
            }
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            Path directory=settings.getParent().resolve("startup-agent");RuntimeFiles.safe(directory);Files.createDirectories(directory);
            Path jar=directory.resolve("path-cache-"+digest+".jar");RuntimeFiles.safe(jar);
            if(!Files.exists(jar,LinkOption.NOFOLLOW_LINKS)) {
                Path temporary=Files.createTempFile(directory,".agent-",".tmp");
                try {Files.write(temporary,bytes);try{Files.move(temporary,jar,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,jar);}}
                finally {Files.deleteIfExists(temporary);}
            }
            if(!Files.isRegularFile(jar,LinkOption.NOFOLLOW_LINKS)||Files.size(jar)!=bytes.length||!Arrays.equals(Files.readAllBytes(jar),bytes))return false;
            Path stats=settings.getParent().resolve("path-cache-last.properties");RuntimeFiles.safe(stats);
            // Agent presence is ephemeral: never inserted in the persistent launch-cache plan.
            String argument="-javaagent:"+jar.toAbsolutePath();
            if(argument.indexOf('\n')>=0||argument.indexOf('\r')>=0||argument.indexOf('=')>=0)return false;
            Files.deleteIfExists(stats);
            command.add(1,argument);command.add(2,"-Dgenesis.path.cache.stats="+stats.toAbsolutePath());
            command.add(3,"-Dgenesis.path.cache.launch="+launch);
            command.add(4,"-Dgenesis.path.cache.enabled="+pathCache);
            command.add(5,"-Dgenesis.path.cache.accessIndex="+accessIndex);
            command.add(6,"-Dgenesis.path.cache.fontSizing="+fontSizing);
            log.info("[startup-path-cache] Experimental startup agent requested; component counters available after 30 seconds");
            return true;
        } catch(Exception e) {log.warn("[startup-path-cache] Preparation declined; ordinary launch continues");return false;}
    }
    private static boolean conflict(String value) {
        if(ProfileLaunch93.transientArgument(value))return false; // Our admission capsule never transforms classes.
        return value.startsWith("@")||value.startsWith("-javaagent:")||value.startsWith("-agentlib:")||value.startsWith("-agentpath:")
                ||value.startsWith("-XX:SharedArchiveFile")||value.startsWith("-XX:ArchiveClassesAtExit")||value.startsWith("-Dgenesis.path.cache.");
    }
}
