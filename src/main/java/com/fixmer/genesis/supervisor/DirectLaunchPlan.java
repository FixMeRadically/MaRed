package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;

/** Gradle records the effective JavaExec invocation, never a parsed sun.java.command.
 * A bounded content fingerprint rejects stale classes, source edits and changed build inputs.
 * This is a local launch cache and can contain launcher credentials; never print or ship it. */
public final class DirectLaunchPlan {
    record Snapshot(List<String> command, Map<String,String> environment, Path workDir) { }
    private static final long MAX_BYTES=96L*1024*1024;
    private static final int MAX_FILES=30000;
    record Decision(Snapshot snapshot,String reason,List<String> changes) { }
    private static final String FILE="direct-launch.properties";
    private DirectLaunchPlan() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IOException("Expected local launch cache path");
        Path target=Path.of(args[0]).toAbsolutePath().normalize();
        Properties p=read(target);
        save(p,target);
    }
    static void save(Properties p,Path target)throws IOException {
        Properties original=(Properties)p.clone();
        boolean confirmed="2".equals(p.getProperty("schema"));
        if(confirmed)checkBaseline(original);
        List<Path> copies=new ArrayList<>();boolean committed=false;
        try {
        validate(p);
        cacheArgumentFiles(p,target,copies);
        LaunchFingerprint.capture(p);
        p.setProperty("created",Long.toString(System.currentTimeMillis()));
        if(confirmed)checkBaseline(original);
        RuntimeFiles.safe(target);Files.createDirectories(target.getParent());
        Path temp=Files.createTempFile(target.getParent(),"launch-",".tmp");
        try {
            try {Files.setPosixFilePermissions(temp,PosixFilePermissions.fromString("rw-------"));}
            catch(UnsupportedOperationException ignored) { }
            try(Writer out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Genesis local JVM launch cache; do not share");}
            try {Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
        committed=true;
        } finally {if(!committed)for(Path copy:copies)try{Files.deleteIfExists(copy);}catch(IOException ignored){}}
        // Only expired generations: a recent prepared launch may still refer to an older @file.
        try(var old=Files.newDirectoryStream(target.getParent(),"direct-launch-arg-*.txt")){
            int count=0;long expired=System.currentTimeMillis()-48L*60*60*1000;
            for(Path file:old){if(++count>512)break;if(!Files.isSymbolicLink(file)&&Files.getLastModifiedTime(file).toMillis()<expired)try{Files.deleteIfExists(file);}catch(IOException ignored){}}
        }catch(IOException ignored) { }
    }
    static final class CaptureChanged extends IOException {
        private final List<String> changes;
        CaptureChanged(List<String> changes){super("INPUT_CHANGED_DURING_CAPTURE");this.changes=List.copyOf(changes);}
        List<String> changes(){return changes;}
    }
    private static void checkBaseline(Properties original)throws IOException {
        String version=original.getProperty("baseline.version","");
        if(version.equals("content-21")) {
            var changes=LaunchFingerprint.checkBaseline(original).changes();
            if(!changes.isEmpty())throw new CaptureChanged(changes);
        } else if(version.isEmpty()) {
            // Old seeds remain subject to their original, stricter timestamp check.
            if(!metadataStamp(original).equals(required(original,"baseline")))throw new CaptureChanged(List.of());
        } else throw new IOException("CACHE_UPGRADE_REQUIRED");
    }
    /** Gradle captures build content before exec; native binaries are captured after extraction. */
    public static void captureBaseline(Properties p)throws IOException {LaunchFingerprint.captureBaseline(p);}
    static Snapshot find(SupervisorConfig config, SupervisorLog log) {return evaluate(config,log).snapshot();}
    static Decision evaluate(SupervisorConfig config, SupervisorLog log) {
        if(!config.fastLaunch())return decision(null,"DISABLED",log);
        if(!defaultWrapper(config.command()))return decision(null,"CUSTOM_COMMAND",log);
        Path file=config.gameDir().resolve("config/mared/dev").resolve(FILE);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return decision(null,"NO_CACHE",log);
        try {
            Properties p=read(file);validate(p);
            Path project=Path.of(required(p,"projectDir")).toAbsolutePath().normalize();
            if(!project.equals(config.workDir().toAbsolutePath().normalize()))throw new IOException("PROJECT_CHANGED");
            long age=System.currentTimeMillis()-Long.parseLong(required(p,"created"));
            if(age<0||age>24L*60*60*1000)throw new IOException("EXPIRED");
            if(!"15".equals(p.getProperty("fingerprint.version")))throw new IOException("CACHE_UPGRADE_REQUIRED");
            var checked=LaunchFingerprint.check(p);
            if(!checked.changes().isEmpty())return decision(null,"STALE_INPUTS",checked.changes(),log);
            List<String> command=numbered(p,"command");
            Map<String,String> env=new LinkedHashMap<>();
            for(String k:p.stringPropertyNames())if(k.startsWith("env.")&&!k.substring(4).startsWith("GENESIS_"))env.put(k.substring(4),p.getProperty(k));
            return decision(new Snapshot(command,env,Path.of(required(p,"workDir"))),"HIT",log);
        } catch(Exception e) {return decision(null,reason(e),log);}
    }
    static String reason(Exception error){
        String value=Objects.toString(error.getMessage(),"");
        return (LaunchCacheClient.confirmationReason(value)||Set.of("STALE_INPUTS","EXPIRED","PROJECT_CHANGED","FINGERPRINT_TIME_LIMIT","FINGERPRINT_FILE_LIMIT","FINGERPRINT_BYTE_LIMIT","LINKED_INPUT","DEBUG_LAUNCH","LONG_OPAQUE_COMMAND","UNCONFIRMED","INPUT_CHANGED_DURING_CAPTURE","CACHE_UPGRADE_REQUIRED").contains(value))?value:"INVALID_OR_UNSUPPORTED";
    }
    private static Decision decision(Snapshot snapshot,String reason,SupervisorLog log){
        return decision(snapshot,reason,List.of(),log);
    }
    private static Decision decision(Snapshot snapshot,String reason,List<String> changes,SupervisorLog log){
        log.info("[fast-launch] "+reason+(snapshot==null?"; configured launcher retained":"; direct game JVM, Gradle skipped"));
        for(String change:changes)log.info("[fast-launch] changed input: "+change);
        return new Decision(snapshot,reason,List.copyOf(changes));
    }
    static boolean defaultWrapper(List<String> cmd) {
        if(cmd.isEmpty())return false;
        String h=cmd.get(0).replace('\\','/');h=h.substring(h.lastIndexOf('/')+1);
        if(!h.equalsIgnoreCase("gradlew")&&!h.equalsIgnoreCase("gradlew.bat"))return false;
        int task=0;
        for(int i=1;i<cmd.size();i++) {
            String a=cmd.get(i);
            if(a.equals("runClient")||a.equals(":runClient"))task++;
            else if(!Set.of("--no-daemon","--console=plain").contains(a))return false;
        }
        return task==1;
    }
    private static void cacheArgumentFiles(Properties p,Path target,List<Path> copies)throws IOException {
        List<String> command=numbered(p,"command"),inputs=new ArrayList<>(numbered(p,"input"));
        Path cwd=Path.of(required(p,"workDir"));
        String generation=UUID.randomUUID().toString();
        for(int i=1;i<command.size();i++)if(command.get(i).startsWith("@")) {
            Path original=cwd.resolve(command.get(i).substring(1)).normalize();RuntimeFiles.safe(original);
            if(!Files.isRegularFile(original)||Files.size(original)>1024*1024)throw new IOException("Argument file unavailable");
            // Launcher @files are not recursive. Preserve bytes and relative paths, keep the same cwd.
            byte[] bytes=Files.readAllBytes(original);
            String options=new String(bytes,StandardCharsets.UTF_8);
            if(options.contains("-agentlib:jdwp")||options.contains("-Xrunjdwp"))throw new IOException("DEBUG_LAUNCH");
            Path local=target.resolveSibling("direct-launch-arg-"+generation+"-"+i+".txt");RuntimeFiles.safe(local);
            copies.add(local);
            Files.write(local,bytes,StandardOpenOption.CREATE_NEW);
            try {Files.setPosixFilePermissions(local,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored) { }
            p.setProperty("command."+i,"@"+local);
            inputs.removeIf(value->cwd.resolve(value).toAbsolutePath().normalize().equals(original.toAbsolutePath().normalize()));inputs.add(local.toString());
        }
        p.setProperty("input.count",Integer.toString(inputs.size()));for(int i=0;i<inputs.size();i++)p.setProperty("input."+i,inputs.get(i));
    }
    private static void validate(Properties p)throws IOException {
        if(!Set.of("1","2").contains(p.getProperty("schema","")))throw new IOException("Unknown cache schema");
        if("2".equals(p.getProperty("schema"))&&!"true".equals(p.getProperty("runtimeConfirmed")))throw new IOException("UNCONFIRMED");
        List<String> command=numbered(p,"command");
        if(command.size()<2||command.size()>4096)throw new IOException("Invalid command size");
        Path java=Path.of(command.get(0));String name=java.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!java.isAbsolute()||!Set.of("java","java.exe","javaw.exe").contains(name)||!Files.isRegularFile(java))throw new IOException("Invalid Java executable");
        for(String a:command)if(a.indexOf('\0')>=0||a.startsWith("-agentlib:jdwp")||a.startsWith("-Xrunjdwp"))throw new IOException("DEBUG_LAUNCH");
        if(System.getProperty("os.name","").startsWith("Windows")&&command.stream().anyMatch(a->a.startsWith("@"))&&command.stream().mapToInt(a->a.length()+3).sum()>24000)throw new IOException("LONG_OPAQUE_COMMAND");
        for(String key:List.of("projectDir","workDir")) {
            Path path=Path.of(required(p,key));if(!path.isAbsolute()||!Files.isDirectory(path))throw new IOException("Invalid launch directory");
        }
        if(numbered(p,"input").isEmpty())throw new IOException("Missing build inputs");
    }
    private static Properties read(Path file)throws IOException {
        RuntimeFiles.safe(file);
        if(Files.size(file)>2*1024*1024)throw new IOException("Launch cache too large");
        Properties p=new Properties();try(Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(in);}return p;
    }
    private static String required(Properties p,String key)throws IOException {
        String value=p.getProperty(key);if(value==null||value.isBlank())throw new IOException("Missing cache field");return value;
    }
    private static List<String> numbered(Properties p,String prefix)throws IOException {
        int count;
        try {count=Integer.parseInt(required(p,prefix+".count"));}
        catch(NumberFormatException e){throw new IOException("Invalid count");}
        if(count<0||count>4096)throw new IOException("Invalid count");
        List<String> out=new ArrayList<>();for(int i=0;i<count;i++){String key=prefix+"."+i;if(!p.containsKey(key))throw new IOException("Missing indexed value");out.add(p.getProperty(key));}return out;
    }
    /** Build inputs must remain stable from Gradle exec until background confirmation.
     * Native extraction is runtime work and has a separate final fingerprint. */
    public static String metadataStamp(Properties p)throws IOException {
        return LaunchFingerprint.metadata(p);
    }
    private static void add(MessageDigest md,String value){md.update(value.getBytes(StandardCharsets.UTF_8));md.update((byte)0);}
}
