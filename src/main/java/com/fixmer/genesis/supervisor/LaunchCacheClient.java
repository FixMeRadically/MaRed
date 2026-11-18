package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Confirms a late Gradle seed from inside the actual game; no ProcessHandle argv parsing. */
public final class LaunchCacheClient {
    private static volatile String status="no_seed";
    public static String status(){return status;}
    private static final AtomicBoolean STARTED=new AtomicBoolean();
    private LaunchCacheClient() { }
    public static void start(){
        if(System.getProperty("genesis.launch.seed")==null)return;
        Thread worker=new Thread(LaunchCacheClient::runPending,"genesis-launch-cache");worker.setDaemon(true);worker.start();
    }
    /** Synchronous entry for the bounded post-startup maintenance worker. */
    public static void runPending(){
        String raw=System.getProperty("genesis.launch.seed");
        if(raw==null||!STARTED.compareAndSet(false,true))return;
        Path cwd=Path.of("").toAbsolutePath().normalize();
        List<String> changes=List.of();
        try{status="consumed";confirm(Path.of(raw),cwd);}
        catch(Exception e){
            status=DirectLaunchPlan.reason(e).toLowerCase(Locale.ROOT);
            if(e instanceof DirectLaunchPlan.CaptureChanged changed)changes=changed.changes();
            System.out.println("[Genesis fast-launch] Background capture declined: "+DirectLaunchPlan.reason(e));
            for(String change:changes)System.out.println("[Genesis fast-launch] Changed during capture: "+change);
        } finally {writeDiagnostic(cwd,status,changes);}
    }
    /** Small allowlisted report; never includes commands, environment, seeds or their contents. */
    static void writeDiagnostic(Path cwd,String result,List<String> changes) {
        Path temporary=null;
        try {
            Path target=cwd.resolve("config/mared/dev/cache-capture-last.json");RuntimeFiles.safe(target);
            if(!result.matches("[a-z_]{1,80}"))return;
            String launch=Objects.toString(System.getenv("GENESIS_LAUNCH_ID"),"");
            if(!launch.matches("[0-9a-f-]{36}"))launch="unrecorded";
            List<String> labels=changes.stream().limit(8).map(s->s.replaceAll("[\\p{Cntrl}]","?")).map(s->DiagnosticText.prefix(s,230)).toList();
            String json="{\"schema\":1,\"launch\":"+SupervisorHttpServer.quote(launch)+",\"status\":"+SupervisorHttpServer.quote(result)+",\"changed_inputs\":["+
                    String.join(",",labels.stream().map(SupervisorHttpServer::quote).toList())+"]}\n";
            Files.createDirectories(target.getParent());temporary=Files.createTempFile(target.getParent(),".capture-report-",".tmp");
            try{Files.setPosixFilePermissions(temporary,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
            Files.writeString(temporary,json,StandardCharsets.UTF_8);
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        } catch(Exception ignored) { }
        finally {if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    private static final Set<String> CONFIRMATION_REASONS=Set.of("UNCONFIRMED_LOCATION","UNCONFIRMED_SEED_FILE","UNCONFIRMED_CAPTURE_ID","UNCONFIRMED_SCHEMA","UNCONFIRMED_WORK_DIR","UNCONFIRMED_COMMAND_COUNT","UNCONFIRMED_MARKER_MISSING","UNCONFIRMED_MARKER_PATH","UNCONFIRMED_JAVA_HOME","CAPTURE_SEED_MISSING","CAPTURE_FAILED_UNAVAILABLE","CAPTURE_FAILED_JAVA_ENTRY","CAPTURE_FAILED_JVM_OPTIONS","CAPTURE_FAILED_APPLICATION","CAPTURE_FAILED_ASSEMBLE","CAPTURE_FAILED_BUILD_INPUTS","CAPTURE_FAILED_NATIVE_INPUTS","CAPTURE_FAILED_BASELINE","CAPTURE_FAILED_WRITE_SEED");
    static boolean confirmationReason(String reason){return CONFIRMATION_REASONS.contains(reason);}
    private static boolean samePath(Path expected,String raw){
        if(raw==null||raw.isBlank())return false;
        try{return expected.equals(Path.of(raw).toAbsolutePath().normalize());}catch(InvalidPathException e){return false;}
    }
    static void confirm(Path seed,Path cwd)throws IOException {
        seed=seed.toAbsolutePath().normalize();cwd=cwd.toAbsolutePath().normalize();
        Path directory=cwd.resolve("config/mared/dev");
        if(!seed.getParent().equals(directory)||!seed.getFileName().toString().matches("launch-seed-[0-9a-f-]{36}\\.properties"))throw new IOException("UNCONFIRMED_LOCATION");
        RuntimeFiles.safe(seed);
        // Only replay of an already cached command may have a consumed seed. New Gradle
        // launches declare success/failure explicitly, outside the replay command.
        String capture=System.getProperty("genesis.launch.capture","");
        if(capture.startsWith("failed_")){
            String code="CAPTURE_"+capture.toUpperCase(Locale.ROOT);
            throw new IOException(confirmationReason(code)?code:"CAPTURE_FAILED_UNAVAILABLE");
        }
        if(!Files.exists(seed,LinkOption.NOFOLLOW_LINKS)){
            if(capture.equals("ready"))throw new IOException("CAPTURE_SEED_MISSING");
            if(!capture.isEmpty())throw new IOException("CAPTURE_FAILED_UNAVAILABLE");
            return;
        }
        if(!Files.isRegularFile(seed,LinkOption.NOFOLLOW_LINKS)||Files.size(seed)>2*1024*1024)throw new IOException("UNCONFIRMED_SEED_FILE");
        Properties p=new Properties();try(var in=Files.newBufferedReader(seed,StandardCharsets.UTF_8)){p.load(in);}
        String id=p.getProperty("captureId","");
        if(!seed.getFileName().toString().equals("launch-seed-"+id+".properties"))throw new IOException("UNCONFIRMED_CAPTURE_ID");
        if(!"2".equals(p.getProperty("schema")))throw new IOException("UNCONFIRMED_SCHEMA");
        if(!cwd.equals(Path.of(p.getProperty("workDir","")).toAbsolutePath().normalize()))throw new IOException("UNCONFIRMED_WORK_DIR");
        int count;try{count=Integer.parseInt(p.getProperty("command.count","0"));}catch(NumberFormatException e){throw new IOException("UNCONFIRMED_COMMAND_COUNT");}
        if(count<2||count>4096)throw new IOException("UNCONFIRMED_COMMAND_COUNT");
        String marker="-Dgenesis.launch.seed=";
        boolean found=false;
        for(int i=1;i<count;i++){
            String value=p.getProperty("command."+i,"");
            if(value.startsWith(marker)&&samePath(seed,value.substring(marker.length())))found=true;
        }
        if(!found)throw new IOException("UNCONFIRMED_MARKER_MISSING");
        if(!samePath(seed,System.getProperty("genesis.launch.seed")))throw new IOException("UNCONFIRMED_MARKER_PATH");
        Path executable=Path.of(p.getProperty("command.0","")).toAbsolutePath().normalize();
        if(executable.getParent()==null||!executable.getParent().equals(Path.of(System.getProperty("java.home"),"bin").toAbsolutePath().normalize()))throw new IOException("UNCONFIRMED_JAVA_HOME");
        p.setProperty("runtimeConfirmed","true");
        try{DirectLaunchPlan.save(p,directory.resolve("direct-launch.properties"));}
        finally{Files.deleteIfExists(seed);}
        status="cached";
        System.out.println("[Genesis fast-launch] Runtime-confirmed direct launch cached in background");
    }
}
