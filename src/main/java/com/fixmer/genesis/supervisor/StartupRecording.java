package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Explicit one-shot recording of the actual direct game JVM. Never instruments Gradle. */
final class StartupRecording {
    static final String REQUEST="startup-profile.request", MARKER="genesis-startup-profile-v1\n";
    record Session(Path request,Path claim,Path settings,Path info,Path recording) {
        static Session none(){return new Session(null,null,null,null,null);}
        void spawned(SupervisorLog log){if(claim!=null)try{Files.deleteIfExists(claim);log.info("[startup-profile] One-shot JFR requested; normal restart timings must be measured separately");}catch(IOException e){log.warn("[startup-profile] Claim cleanup unavailable");}}
        void failed(SupervisorLog log){
            if(claim==null)return;
            try{if(!Files.exists(request,LinkOption.NOFOLLOW_LINKS))Files.move(claim,request);else Files.deleteIfExists(claim);}catch(IOException e){log.warn("[startup-profile] Request restore unavailable; re-arm if needed");}
            for(Path path:List.of(settings,info,recording))try{Files.deleteIfExists(path);}catch(IOException ignored){}
        }
    }
    static Session prepare(Path game,String mode,List<String> command,String launch,SupervisorLog log){return prepare(game,mode,command,launch,30,log);}
    static Session prepare(Path game,String mode,List<String> command,String launch,int seconds,SupervisorLog log){
        Path request=game.resolve("config/mared/dev").resolve(REQUEST),claim=null,settings=null,info=null,recording=null;
        if(!"direct_cache".equals(mode)||!Files.isRegularFile(request,LinkOption.NOFOLLOW_LINKS))return Session.none();
        try{
            RuntimeFiles.safe(request);
            if(Files.size(request)>128||!Files.readString(request,StandardCharsets.UTF_8).equals(MARKER))throw new IOException("Unsupported request");
            if(seconds<1||seconds>30||!launch.matches("[0-9a-f-]{36}"))throw new IOException("Invalid profile settings");
            Path executable=Path.of(command.get(0));
            if(!executable.isAbsolute()||!Files.isRegularFile(executable)||!Set.of("java","java.exe","javaw.exe").contains(executable.getFileName().toString().toLowerCase(Locale.ROOT)))throw new IOException("Unsupported launcher");
            // Opaque argument files and existing/inherited recording options belong to their owner.
            if(command.stream().anyMatch(v->v.startsWith("@")||v.startsWith("-XX:StartFlightRecording")||v.startsWith("-XX:FlightRecorderOptions")||v.equals("-XX:-FlightRecorder")))throw new IOException("Existing or opaque JFR options");
            if(java.util.stream.Stream.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS").map(System::getenv).filter(Objects::nonNull).anyMatch(v->v.contains("FlightRecorder")))throw new IOException("Inherited JFR options");
            Path profiles=request.getParent().resolve("profiles");RuntimeFiles.safe(profiles);Files.createDirectories(profiles);
            try{Files.setPosixFilePermissions(profiles,PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException ignored){}
            int count=0;try(var files=Files.newDirectoryStream(profiles,"startup-*.json")){for(Path file:files)if(++count>=8)throw new IOException("Profile retention limit");}
            String name="startup-"+launch;settings=profiles.resolve(name+".jfc");info=profiles.resolve(name+".json");recording=profiles.resolve(name+".jfr");
            for(Path target:List.of(settings,info,recording))if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("Profile generation already exists");
            String option="-XX:StartFlightRecording=name=GenesisStartup,settings="+quoted(settings)+",filename="+quoted(recording)+",duration="+seconds+"s,maxsize=64m,maxage=30s,disk=true,dumponexit=true";
            claim=profiles.resolve(name+".request");RuntimeFiles.safe(claim);
            Files.move(request,claim);
            Session session=new Session(request,claim,settings,info,recording);
            try{
                privateWrite(settings,configuration());
                String helper=Path.of(StartupRecording.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                String json="{\"schema\":1,\"launch\":"+SupervisorHttpServer.quote(launch)+",\"java\":"+SupervisorHttpServer.quote(executable.toString())+",\"helper\":"+SupervisorHttpServer.quote(helper)+",\"recording\":"+SupervisorHttpServer.quote(recording.getFileName().toString())+",\"duration_s\":"+seconds+"}\n";
                privateWrite(info,json);
                command.add(1,option);
                return session;
            }catch(Exception e){session.failed(log);throw e;}
        }catch(Exception e){log.warn("[startup-profile] Request retained or restored; preparation declined ("+e.getClass().getSimpleName()+"); ordinary launch continues");return Session.none();}
    }
    private static String quoted(Path path)throws IOException {
        String value=path.toAbsolutePath().normalize().toString();
        if(value.indexOf('"')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IOException("Unsupported profile path");
        return '"'+value+'"';
    }
    private static void privateWrite(Path path,String text)throws IOException {
        RuntimeFiles.safe(path);
        try{Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}catch(UnsupportedOperationException e){Files.createFile(path);}
        Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
    }
    static String configuration(){return """
        <?xml version="1.0" encoding="UTF-8"?>
        <configuration version="2.0" label="Genesis startup" description="One-shot startup diagnosis" provider="Genesis">
          <event name="jdk.JVMInformation"><setting name="enabled">true</setting><setting name="period">beginChunk</setting></event>
          <event name="jdk.ExecutionSample"><setting name="enabled">true</setting><setting name="period">10 ms</setting></event>
          <event name="jdk.NativeMethodSample"><setting name="enabled">true</setting><setting name="period">20 ms</setting></event>
          <event name="jdk.ClassLoad"><setting name="enabled">true</setting><setting name="stackTrace">false</setting><setting name="threshold">0 ms</setting></event>
          <event name="jdk.FileRead"><setting name="enabled">true</setting><setting name="stackTrace">false</setting><setting name="threshold">5 ms</setting></event>
          <event name="jdk.GarbageCollection"><setting name="enabled">true</setting><setting name="threshold">0 ms</setting></event>
          <event name="jdk.GCPhasePause"><setting name="enabled">true</setting><setting name="threshold">0 ms</setting></event>
        </configuration>
        """;}
}
