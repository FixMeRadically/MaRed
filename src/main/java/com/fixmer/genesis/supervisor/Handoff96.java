package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Initial game -> new owner: independently read final save/normal-exit evidence, never PID-only admission. */
public final class Handoff96 {
    private static final Set<String> BASE=Set.of("schema","id","instance","config_sha","config_path","game_path","request","epoch","resume","resume_sha","pid","start","identity","lease");
    private static final Set<String> PROGRESS=Set.of("exit","normal","hook","world","sequence","cancelled");
    private static Writer active;
    private Handoff96(){}

    public static synchronized String prepare(Path configPath,Path game,String instance,String token,String request,String epoch,String resume)throws IOException {
        if(active!=null)throw new IOException("HANDOFF96_BUSY");
        SupervisorConfig config=SupervisorConfig.load(new String[]{"--config="+configPath});
        if(!config.gameDir().equals(game.toAbsolutePath().normalize())||!config.instanceId().equals(instance)||!config.token().equals(token))throw new IOException("HANDOFF96_CONFIG_CHANGED");
        RestartPermit95.uuid(request);RestartPermit95.uuid(resume);if(!epoch.isEmpty())RestartPermit95.uuid(epoch);
        long pid=ProcessHandle.current().pid();var nativeStart=ProcessHandle.current().info().startInstant();long start=nativeStart.map(java.time.Instant::toEpochMilli).orElse(java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        if(start<=0)throw new IOException("HANDOFF96_PROCESS_IDENTITY");
        String id=UUID.randomUUID().toString();Path folder=folder(config,id);Files.createDirectories(folder.getParent());Files.createDirectory(folder);
        GameLease.Lease lease=GameLease.hold(folder.resolve("source.lease"),id,pid,start);
        if(lease==null)throw new IOException("HANDOFF96_LEASE");
        try{
            Properties base=new Properties();base.setProperty("schema","96");base.setProperty("id",id);base.setProperty("instance",instance);base.setProperty("config_sha",config.configSha94());base.setProperty("config_path",hash(config.configPath().toString()));base.setProperty("game_path",hash(config.gameDir().toString()));base.setProperty("request",request);base.setProperty("epoch",epoch.isEmpty()?"-":epoch);base.setProperty("resume",resume);base.setProperty("resume_sha",resume(config,resume,pid,!epoch.isEmpty()));base.setProperty("pid",Long.toString(pid));base.setProperty("start",Long.toString(start));base.setProperty("identity",nativeStart.isPresent()?"native":"held_lease");base.setProperty("lease",lease.token);
            Writer writer=new Writer(config,base,lease);write(folder.resolve("intent.properties"),base,token);writer.flush();
            writer.thread=new Thread(()->writer.hook(),"genesis-initial-exit96");Runtime.getRuntime().addShutdownHook(writer.thread);active=writer;return id;
        }catch(IOException|RuntimeException error){lease.close();throw error;}
    }

    /** Called inside the same synchronized permission transition that claims Minecraft.stop(). */
    public static synchronized void exitClaimed(String request){
        if(active==null||!active.base.getProperty("request").equals(request))return;
        try{active.exit=true;active.flush();}catch(IOException failure){active.exit=false;active.broken=true;throw new UncheckedIOException(failure);}
    }
    public static synchronized void cancel(String request){
        if(active==null||!active.base.getProperty("request").equals(request))return;
        Writer writer=active;if(writer.exit)return;writer.cancelled=true;
        try{writer.flush();}catch(IOException ignored){writer.broken=true;}
        try{writer.lease.close();}catch(IOException ignored){}try{Runtime.getRuntime().removeShutdownHook(writer.thread);}catch(IllegalStateException ignored){}active=null;
    }
    public static synchronized void world(String epoch,String phase){
        if(active==null)return;Writer w=active;
        if(w.world.equals("FAILED"))return;
        if(!w.base.getProperty("epoch").equals(epoch)){w.world="FAILED";}
        else if(!w.exit&&!phase.equals("ACTIVE")){w.world="FAILED";}
        else{
            try{w.state.accept(epoch,phase);w.world=w.state.phase();}catch(RuntimeException invalid){w.world="FAILED";}
        }
        w.safeFlush();
    }
    public static synchronized void normalClosing(){if(active!=null){active.normal=true;active.safeFlush();}}

    private static final class Writer {
        final SupervisorConfig config;final Properties base;final GameLease.Lease lease;final IntegratedState90 state=new IntegratedState90();
        volatile boolean exit,normal,hook,cancelled,broken;volatile String world;Thread thread;long sequence;
        Writer(SupervisorConfig config,Properties base,GameLease.Lease lease){this.config=config;this.base=base;this.lease=lease;String epoch=base.getProperty("epoch");world=epoch.equals("-")?"NONE":"ACTIVE";if(!epoch.equals("-"))state.accept(epoch,"ACTIVE");}
        synchronized void hook(){hook=true;safeFlush();} // Hooks alone are not normal shutdown or final world save.
        synchronized void safeFlush(){try{flush();}catch(IOException ignored){broken=true;}}
        synchronized void flush()throws IOException{
            if(broken)throw new IOException("HANDOFF96_REPORT_BROKEN");
            Properties p=new Properties();p.putAll(base);p.setProperty("exit",Boolean.toString(exit));p.setProperty("normal",Boolean.toString(normal));p.setProperty("hook",Boolean.toString(hook));p.setProperty("world",world);p.setProperty("sequence",Long.toString(++sequence));p.setProperty("cancelled",Boolean.toString(cancelled));write(folder(config,base.getProperty("id")).resolve("progress.properties"),p,config.token());
        }
    }

    static record Proof(Properties base,Path folder) {
        String id(){return base.getProperty("id");}String request(){return base.getProperty("request");}
        long pid(){return Long.parseLong(base.getProperty("pid"));}long start(){return Long.parseLong(base.getProperty("start"));}
    }
    static Proof admit(SupervisorConfig config,String id,long parent)throws IOException{
        ColdOwner94.configUnchanged(config);Path folder=folder(config,id);Properties p=read(folder.resolve("intent.properties"),config.token(),BASE);
        require(p.getProperty("schema").equals("96")&&p.getProperty("id").equals(id)&&p.getProperty("instance").equals(config.instanceId())&&p.getProperty("config_sha").equals(config.configSha94())&&p.getProperty("config_path").equals(hash(config.configPath().toString()))&&p.getProperty("game_path").equals(hash(config.gameDir().toString())),"BINDING");
        for(String key:List.of("id","instance","request","resume","lease"))try{RestartPermit95.uuid(p.getProperty(key));}catch(RuntimeException bad){throw new IOException("HANDOFF96_ID",bad);}
        if(!p.getProperty("epoch").equals("-"))try{RestartPermit95.uuid(p.getProperty("epoch"));}catch(RuntimeException bad){throw new IOException("HANDOFF96_EPOCH",bad);}
        Proof proof=new Proof(p,folder);try{require(proof.pid()==parent&&parent>0&&proof.start()>0,"PROCESS");}catch(NumberFormatException invalid){throw new IOException("HANDOFF96_PROCESS",invalid);}
        require(p.getProperty("resume_sha").equals(resume(config,p.getProperty("resume"),proof.pid(),!p.getProperty("epoch").equals("-"))),"RESUME_CHANGED");
        require(Set.of("native","held_lease").contains(p.getProperty("identity")),"IDENTITY_MODE");
        require(life(proof)!=GameSession.Life.DEAD,"SOURCE_NOT_ALIVE");
        require(GameLease.probe(folder.resolve("source.lease"),marker(proof))==GameLease.State.HELD,"SOURCE_LEASE");
        Properties progress=progress(config,proof);require(!flag(progress,"exit")&&!flag(progress,"cancelled")&&!flag(progress,"hook")&&!flag(progress,"normal")&&progress.getProperty("world").equals(p.getProperty("epoch").equals("-")?"NONE":"ACTIVE"),"NOT_PREPARED");return proof;
    }
    static void confirmed(SupervisorConfig config,Proof proof)throws IOException{
        ColdOwner94.configUnchanged(config);Properties current=read(proof.folder.resolve("intent.properties"),config.token(),BASE);require(current.equals(proof.base),"INTENT_CHANGED");
        require(proof.base.getProperty("resume_sha").equals(resume(config,proof.base.getProperty("resume"),proof.pid(),!proof.base.getProperty("epoch").equals("-"))),"RESUME_CHANGED");
        Properties p=progress(config,proof);require(flag(p,"exit")&&flag(p,"normal")&&flag(p,"hook")&&!flag(p,"cancelled"),"EXIT_EVIDENCE");
        require(p.getProperty("world").equals(proof.base.getProperty("epoch").equals("-")?"NONE":"STOPPED"),"FINAL_SAVE");
        require(life(proof)!=GameSession.Life.ALIVE,"SOURCE_STILL_ALIVE");
        require(GameLease.probe(proof.folder.resolve("source.lease"),marker(proof))==GameLease.State.RELEASED,"SOURCE_LEASE_NOT_RELEASED");
    }
    private static GameSession.Life life(Proof proof){return proof.base.getProperty("identity").equals("native")?GameSession.osLife(proof.pid(),proof.start()):GameSession.Life.UNKNOWN;}
    static boolean exited(Proof proof){return life(proof)!=GameSession.Life.ALIVE&&GameLease.probe(proof.folder.resolve("source.lease"),marker(proof))==GameLease.State.RELEASED;}
    static boolean cancelled(SupervisorConfig config,Proof proof)throws IOException{return flag(progress(config,proof),"cancelled");}
    private static String marker(Proof p){return GameLease.marker(p.id(),p.base.getProperty("lease"),p.pid(),p.start());}
    private static Properties progress(SupervisorConfig config,Proof proof)throws IOException{
        Set<String> keys=new HashSet<>(BASE);keys.addAll(PROGRESS);Properties p=read(proof.folder.resolve("progress.properties"),config.token(),keys);
        for(String key:BASE)require(Objects.equals(p.getProperty(key),proof.base.getProperty(key)),"PROGRESS_BINDING");
        for(String key:List.of("exit","normal","hook","cancelled"))flag(p,key);
        require(p.getProperty("sequence").matches("[1-9][0-9]{0,17}"),"SEQUENCE");require(Set.of("NONE","ACTIVE","SAVED","STOPPED","FAILED").contains(p.getProperty("world")),"WORLD");return p;
    }
    private static boolean flag(Properties p,String key)throws IOException{String value=p.getProperty(key);require("true".equals(value)||"false".equals(value),"BOOLEAN");return value.equals("true");}
    private static void require(boolean ok,String code)throws IOException{if(!ok)throw new IOException("HANDOFF96_"+code);}
    static Path folder(SupervisorConfig config,String id)throws IOException{try{RestartPermit95.uuid(id);}catch(RuntimeException bad){throw new IOException("HANDOFF96_ID",bad);}Path path=config.gameDir().resolve("config/mared/dev/handoff96").resolve(id);RuntimeFiles.safe(path);return path;}
    private static String resume(SupervisorConfig config,String id,long pid,boolean local)throws IOException{
        Path file=config.gameDir().resolve("config/mared/dev/resume.properties");RuntimeFiles.safe(file);require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<=65536,"RESUME_FILE");byte[] bytes=bounded(file,65536);Properties p=RecoveryConnection92.parse(new String(bytes,StandardCharsets.UTF_8));require(id.equals(p.getProperty("id"))&&Long.toString(pid).equals(p.getProperty("sourcePid"))&&(local?"world".equals(p.getProperty("kind")):Set.of("default","server").contains(p.getProperty("kind"))),"RESUME_BINDING");return hash(bytes);
    }
    private static String hash(String value){return hash(value.getBytes(StandardCharsets.UTF_8));}
    private static String hash(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(java.security.GeneralSecurityException impossible){throw new IllegalStateException(impossible);}}
    private static String signed(Properties p){StringBuilder text=new StringBuilder("GENESIS_INITIAL_HANDOFF96\n");for(String key:new TreeSet<>(p.stringPropertyNames()))if(!key.equals("mac")){String value=p.getProperty(key);text.append(key.length()).append(':').append(key).append(value.length()).append(':').append(value);}return text.toString();}
    static void write(Path file,Properties values,String token)throws IOException{
        RuntimeFiles.safe(file);Properties p=new Properties();p.putAll(values);p.setProperty("mac",RecoveryAuth92.mac(token,signed(p)));Path temp=Files.createTempFile(file.getParent(),".handoff96-",".tmp");
        try{try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Initial handoff96 evidence; not install authority by itself.");}try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException bad){throw new IOException("HANDOFF96_ATOMIC_REQUIRED",bad);}}finally{Files.deleteIfExists(temp);}
    }
    private static Properties read(Path file,String token,Set<String> keys)throws IOException{
        RuntimeFiles.safe(file);require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<=4096,"FILE");String text=new String(bounded(file,4096),StandardCharsets.UTF_8);Properties p=RecoveryConnection92.parse(text);String mac=p.getProperty("mac");require(RecoveryAuth92.equal(mac,RecoveryAuth92.mac(token,signed(p))),"SIGNATURE");p.remove("mac");require(p.stringPropertyNames().equals(keys),"FIELDS");return p;
    }
    private static byte[] bounded(Path file,int max)throws IOException{try(var in=Files.newInputStream(file)){byte[] bytes=in.readNBytes(max+1);require(bytes.length<=max,"LIMIT");return bytes;}}
    static void report(SupervisorConfig config,String id,String phase,String reason){
        try{Path path=config.gameDir().resolve("config/mared/dev/handoff96-last.properties");RuntimeFiles.safe(path);Properties p=new Properties();p.setProperty("schema","96");p.setProperty("id",id);p.setProperty("phase",phase);p.setProperty("reason",reason);write(path,p,config.token());RestartHistory97.recordAttempt(config.gameDir(),"handoff",id,phase,reason);}catch(IOException ignored){}
    }
}
