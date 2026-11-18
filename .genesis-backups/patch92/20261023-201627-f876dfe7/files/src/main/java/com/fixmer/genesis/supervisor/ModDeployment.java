package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
/** Durable single-JAR transaction. Active mods are changed only by the supervisor after child exit. */
public final class ModDeployment {
    private final Path game,root,mods;
    public ModDeployment(Path game) {
        this.game=game.toAbsolutePath().normalize();
        root=this.game.resolve("config/mared/dev/deployments");
        mods=this.game.resolve("mods");
    }
    private Path pending() {
        return root.resolve("pending.properties");
    }
    private Path trial() {
        return root.resolve("trial.properties");
    }
    private Path catalog() {
        return root.resolve("catalog.properties");
    }
    private static void safe(Path path)throws IOException {
        for(Path p=path.toAbsolutePath();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked deployment paths are unsupported: "+p);
    }
    private void directories()throws IOException {
        safe(root);
        safe(mods);
        Files.createDirectories(root);
        Files.createDirectories(mods);
    }
    private final ThreadLocal<Integer> lockDepth=ThreadLocal.withInitial(()->0);
    private final class Guard implements AutoCloseable {
        private final java.nio.channels.FileChannel channel;
        private final java.nio.channels.FileLock lock;
        Guard(java.nio.channels.FileChannel channel,java.nio.channels.FileLock lock) {
            this.channel=channel;
            this.lock=lock;
        }
        @Override public void close()throws IOException {
            lockDepth.set(lockDepth.get()-1);
            if(channel!=null) {
                try {
                    lock.release();
                }
                finally {
                    channel.close();
                }
            }
        }
    }
    private Guard lock()throws IOException {
        if(lockDepth.get()>0) {
            lockDepth.set(lockDepth.get()+1);
            return new Guard(null,null);
        }
        directories();
        Path path=root.resolve("deployment.lock");
        safe(path);
        var channel=java.nio.channels.FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {
            var lock=channel.tryLock();
            if(lock==null)throw new IOException("Deployment is busy in another process");
            lockDepth.set(1);
            return new Guard(channel,lock);
        }
        catch(IOException|RuntimeException error) {
            channel.close();
            if(error instanceof IOException io)throw io;
            throw new IOException("Deployment is busy",error);
        }
    }
    private static Properties read(Path path)throws IOException {
        var p=new Properties();
        if(!Files.exists(path))return p;
        safe(path);
        if(Files.size(path)>65536)throw new IOException("Deployment journal is too large");
        try(var r=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }
    private static void write(Path file,Properties p)throws IOException {
        safe(file);
        Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"journal-",".tmp");
        try {
            try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)) {
                p.store(out,"MaRed mod deployment journal");
            }
            publish(temp,file);
        }
        finally {
            Files.deleteIfExists(temp);
        }
    }
    private static void publish(Path from,Path to)throws IOException {
        try {
            Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        catch(AtomicMoveNotSupportedException e) {
            Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    public static String hash(Path path)throws IOException {
        safe(path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>256L*1024*1024)throw new IOException("Invalid deployment file");
        MessageDigest digest;
        try {
            digest=MessageDigest.getInstance("SHA-256");
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try(var in=Files.newInputStream(path)) {
            byte[] b=new byte[65536];
            int n;
            long total=0;
            while((n=in.read(b))!=-1) {
                if((total+=n)>256L*1024*1024)throw new IOException("Deployment file grew beyond size limit");
                digest.update(b,0,n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String id(Properties p)throws IOException {
        String id=p.getProperty("modId","");
        if(!id.matches("[a-z][a-z0-9_]{1,63}"))throw new IOException("Invalid deployment mod id");
        return id;
    }
    private Path transaction(Properties p)throws IOException {
        String name=p.getProperty("transaction","");
        try {
            if(!UUID.fromString(name).toString().equals(name))throw new IllegalArgumentException();
        }
        catch(IllegalArgumentException e) {
            throw new IOException("Invalid transaction id");
        }
        return root.resolve("transactions").resolve(name);
    }
    private Path target(Properties p)throws IOException {
        return mods.resolve("mared-dev-"+id(p)+".jar");
    }
    private String currentHash(Path path)throws IOException {
        return Files.exists(path)?hash(path):"";
    }
    private void conflicts(String id,Path own)throws IOException {
        safe(mods);
        if(!Files.isDirectory(mods))return;
        int count=0;
        try(var paths=Files.newDirectoryStream(mods,"*.jar")) {
            for(Path jar:paths) {
                if(++count>512)throw new IOException("Mod directory scan limit");
                if(jar.equals(own))continue;
                // Unknown/foreign JARs are never overwritten. Read only their bounded metadata when available.
                try {
                    if(ModJarMetadata.read(jar).id().equals(id))throw new Conflict("Another JAR already provides "+id+": "+jar.getFileName());
                }
                catch(Conflict e) {
                    throw e;
                }
                catch(IOException ignored) {
                }
            }
        }
    }
    private static final class Conflict extends IOException {
        Conflict(String s) {
            super(s);
        }
    }
    public String stage(Path source,String expectedHash,Map<String,String> loaded)throws IOException {
        return stage(source,expectedHash,loaded,"");
    }
    public synchronized String stage(Path source,String expectedHash,Map<String,String> loaded,String project)throws IOException {
        try (Guard ignored = lock()) {
            directories();
            if(Files.exists(root.resolve("world-review91.properties")))throw new IOException("RECOVERY91_REVIEW_REQUIRED");
            if(Files.exists(pending())||Files.exists(trial()))throw new IOException("Another deployment is pending or awaiting game confirmation");
            var info=ModJarMetadata.read(source);
            ModJarMetadata.compatible(info,loaded);
            if(!hash(source).equals(expectedHash))throw new IOException("Built JAR changed before staging");
            var plan=new Properties();
            String txn=UUID.randomUUID().toString();
            plan.setProperty("transaction",txn);
            plan.setProperty("modId",info.id());
            plan.setProperty("version",info.version());
            plan.setProperty("sha256",expectedHash);
            plan.setProperty("project",project);
            plan.setProperty("createdAt",Long.toString(System.currentTimeMillis()));
            Path target=target(plan);
            var known=read(catalog());
            String prior=known.getProperty(info.id()+".sha256","");
            String previousId=known.getProperty("project."+project+".modId",info.id());
            if(!previousId.equals(info.id()))throw new IOException("This project already manages mod id "+previousId+"; changing its id requires a separate project");
            String owner=known.getProperty(info.id()+".project",project);
            if(!owner.equals(project))throw new IOException("This mod id belongs to another managed project");
            if(!currentHash(target).equals(prior))throw new IOException("Managed target differs from catalog; external edits are preserved");
            if(loaded.containsKey(info.id())&&prior.isEmpty())throw new IOException("This mod is already loaded outside the managed mods directory");
            conflicts(info.id(),target);
            plan.setProperty("priorHash",prior);
            plan.setProperty("priorVersion",known.getProperty(info.id()+".version",""));
            Path dir=transaction(plan);
            Files.createDirectories(dir);
            Path copy=dir.resolve("candidate.jar");
            try {
                Files.copy(source,copy);
                if(!hash(copy).equals(expectedHash)||!hash(source).equals(expectedHash))throw new IOException("Built JAR changed during staging");
                write(pending(),plan);
                return txn;
            }
            catch(IOException e) {
                Files.deleteIfExists(copy);
                Files.deleteIfExists(dir);
                throw e;
            }
        }
    }
    public synchronized void cancelPending(String txn)throws IOException {
        try (Guard ignored = lock()) {
            Properties p=read(pending());
            if(txn!=null&&txn.equals(p.getProperty("transaction"))) {
                Files.deleteIfExists(pending());
                cleanup(p);
            }
        }
    }
    /** Called only when no old game process/tree is alive. Returns the new trial token or empty. */
    public synchronized String beforeLaunch()throws IOException {
        try (Guard ignored = lock()) {
            directories();
            if(Files.exists(trial())) {
                var p=read(trial());
                if("COMMITTED".equals(p.getProperty("phase")))finishCommit(p);
                else if("RESTORE_REQUESTED".equals(p.getProperty("recovery91")))rollback("Explicit recovery request after game exit");
                else throw new IOException("RECOVERY91_CHOICE_REQUIRED");
            }
            if(!Files.exists(pending()))return "";
            Properties p=read(pending());
            Path dir=transaction(p),target=target(p),candidate=dir.resolve("candidate.jar");
            long age;
            try {
                age=System.currentTimeMillis()-Long.parseLong(p.getProperty("createdAt"));
            }
            catch(RuntimeException e) {
                throw new IOException("Invalid deployment time",e);
            }
            if(age<0||age>30*60*1000L) {
                report(p,"CANCELLED","Deployment intent expired; previous installation retained");
                Files.deleteIfExists(pending());
                cleanup(p);
                return "";
            }
            if(!hash(candidate).equals(p.getProperty("sha256"))||!ModJarMetadata.read(candidate).id().equals(id(p)))throw new IOException("Staged candidate differs from plan");
            if(!currentHash(target).equals(p.getProperty("priorHash")))throw new IOException("Managed JAR changed after staging");
            conflicts(id(p),target);
            if(Files.exists(target)) {
                Files.copy(target,dir.resolve("previous.jar"),StandardCopyOption.REPLACE_EXISTING);
                if(!hash(dir.resolve("previous.jar")).equals(p.getProperty("priorHash")))throw new IOException("Previous version backup failed");
            }
            Path resume=root.getParent().resolve("resume.properties");
            if(Files.isRegularFile(resume)&&Files.size(resume)<=65536)Files.copy(resume,dir.resolve("resume.properties"),StandardCopyOption.REPLACE_EXISTING);
            if(Files.isRegularFile(resume)&&"world".equals(read(resume).getProperty("kind")))p.setProperty("worldRisk91","true");
            p.setProperty("writer91","true");
            p.setProperty("phase","PREPARED");
            write(trial(),p);
            Files.delete(pending());
            try {
                Path temp=Files.createTempFile(mods,"mared-install-",".part");
                try {
                    Files.copy(candidate,temp,StandardCopyOption.REPLACE_EXISTING);
                    if(!hash(temp).equals(p.getProperty("sha256")))throw new IOException("Candidate copy failed");
                    publish(temp,target);
                }
                finally {
                    Files.deleteIfExists(temp);
                }
                p.setProperty("phase","APPLIED");
                write(trial(),p);
                return p.getProperty("transaction");
            }
            catch(IOException e) {
                rollback("Install failed: "+e.getMessage());
                throw e;
            }
        }
    }
    public synchronized String activeTrial()throws IOException {
        try (Guard ignored = lock()) {
            return read(trial()).getProperty("transaction","");
        }
    }
    public synchronized void confirm(String txn)throws IOException {
        try (Guard ignored = lock()) {
            Properties p=read(trial());
            if(!txn.equals(p.getProperty("transaction"))||!"APPLIED".equals(p.getProperty("phase")))throw new IOException("Trial token/state differs");
            if(!hash(target(p)).equals(p.getProperty("sha256")))throw new IOException("Trial JAR changed before confirmation");
            p.setProperty("phase","COMMITTED");
            write(trial(),p);
            finishCommit(p);
        }
    }
    private void finishCommit(Properties p)throws IOException {
        if(!currentHash(target(p)).equals(p.getProperty("sha256")))throw new IOException("Committed JAR changed");
        var known=read(catalog());
        known.setProperty(id(p)+".sha256",p.getProperty("sha256"));
        known.setProperty(id(p)+".version",p.getProperty("version"));
        known.setProperty(id(p)+".project",p.getProperty("project",""));
        known.setProperty("project."+p.getProperty("project","")+".modId",id(p));
        write(catalog(),known);
        Path previous=transaction(p).resolve("previous.jar");
        if(Files.exists(previous)) {
            Path backups=root.resolve("backups");
            Files.createDirectories(backups);
            Files.copy(previous,backups.resolve(id(p)+".jar"),StandardCopyOption.REPLACE_EXISTING);
        }
        report(p,"MANUAL".equals(p.getProperty("accept91"))?"MANUALLY_ACCEPTED91":"CONFIRMED","MANUAL".equals(p.getProperty("accept91"))?"User retained the current candidate; world compatibility is not verified":"World opened with the candidate mod");
        clearPendingFor(p);
        Files.deleteIfExists(trial());
        cleanup(p);
    }
    public synchronized boolean rollback(String reason)throws IOException {
        try (Guard ignored = lock()) {
            if(!Files.exists(trial()))return false;
            Properties p=read(trial());
            if("COMMITTED".equals(p.getProperty("phase"))) {
                finishCommit(p);
                return false;
            }
            if((!"true".equals(p.getProperty("writer91"))||"true".equals(p.getProperty("worldRisk91")))&&!"RESTORE_REQUESTED".equals(p.getProperty("recovery91")))throw new IOException("RECOVERY91_CHOICE_REQUIRED");
            Path target=target(p);
            String current=currentHash(target),prior=p.getProperty("priorHash","");
            if(!current.equals(prior)&&!current.equals(p.getProperty("sha256")))throw new IOException("Target was edited externally; rollback refuses to overwrite it");
            if("true".equals(p.getProperty("worldRisk91"))){
                Properties review=new Properties();for(String k:List.of("transaction","modId","version","priorVersion","priorHash"))review.setProperty(k,p.getProperty(k,""));review.setProperty("phase","RESTORED_REVIEW");write(root.resolve("world-review91.properties"),review);
                Properties hold=new Properties();hold.setProperty("id",UUID.randomUUID().toString());hold.setProperty("sourcePid",Long.toString(ProcessHandle.current().pid()));hold.setProperty("createdAt",Long.toString(System.currentTimeMillis()));hold.setProperty("kind","hold91");write(root.getParent().resolve("resume.properties"),hold);
            }
            if(!current.equals(prior)) {
                if(prior.isEmpty())Files.deleteIfExists(target);
                else {
                    Path old=transaction(p).resolve("previous.jar");
                    if(!hash(old).equals(prior))throw new IOException("Previous version backup differs");
                    Path tmp=Files.createTempFile(mods,"mared-restore-",".part");
                    try {
                        Files.copy(old,tmp,StandardCopyOption.REPLACE_EXISTING);
                        publish(tmp,target);
                    }
                    finally {
                        Files.deleteIfExists(tmp);
                    }
                }
            }
            Path resume=root.getParent().resolve("resume.properties"),savedResume=transaction(p).resolve("resume.properties");
            if(!Files.exists(resume)&&Files.isRegularFile(savedResume))Files.copy(savedResume,resume);
            report(p,"ROLLED_BACK",reason);
            clearPendingFor(p);
            Files.deleteIfExists(trial());
            cleanup(p);
            return true;
        }
    }
    /** Called before an integrated world is allowed to load; durable even if the supervisor disappears. */
    public synchronized void protectTrial91(String txn)throws IOException {
        try(Guard ignored=lock()){
            Properties p=read(trial());if(p.isEmpty()||"COMMITTED".equals(p.getProperty("phase")))return;if("RESTORE_REQUESTED".equals(p.getProperty("recovery91")))throw new IOException("RECOVERY91_RESTORE_PENDING");if(!txn.equals(p.getProperty("transaction"))||!"APPLIED".equals(p.getProperty("phase")))throw new IOException("RECOVERY91_TRIAL_CHANGED");
            if(!currentHash(target(p)).equals(p.getProperty("sha256")))throw new IOException("RECOVERY91_JAR_CHANGED");
            if(!"true".equals(p.getProperty("worldRisk91"))){p.setProperty("worldRisk91","true");write(trial(),p);}
        }
    }
    private String revision91(Properties p,String current)throws IOException{
        try{var digest=MessageDigest.getInstance("SHA-256");for(String key:new TreeSet<>(p.stringPropertyNames())){digest.update(key.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);digest.update(p.getProperty(key).getBytes(StandardCharsets.UTF_8));digest.update((byte)0);}digest.update(current.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(digest.digest());}catch(GeneralSecurityException e){throw new IOException(e);}
    }
    public synchronized RecoveryView91 recovery91()throws IOException {
        try(Guard ignored=lock()){
            Properties p=read(trial());
            if(!p.isEmpty()&&!"COMMITTED".equals(p.getProperty("phase"))){
                String current=currentHash(target(p));if(!Set.of("PREPARED","APPLIED").contains(p.getProperty("phase"))||!current.equals(p.getProperty("sha256"))&&!current.equals(p.getProperty("priorHash","")))throw new IOException("RECOVERY91_JAR_CHANGED");
                String phase="RESTORE_REQUESTED".equals(p.getProperty("recovery91"))?"RESTORE_REQUESTED":"CHOICE_REQUIRED";
                return new RecoveryView91(phase,revision91(p,current),p.getProperty("transaction"),id(p),p.getProperty("version",""),p.getProperty("priorVersion",""),"APPLIED".equals(p.getProperty("phase"))&&current.equals(p.getProperty("sha256"))&&!Files.exists(root.resolve("world-review91.properties")));
            }
            p=read(root.resolve("world-review91.properties"));if(!p.isEmpty()){
                transaction(p);String current=currentHash(target(p));if(!"RESTORED_REVIEW".equals(p.getProperty("phase"))||!current.equals(p.getProperty("priorHash")))throw new IOException("RECOVERY91_REVIEW_CHANGED");
                return new RecoveryView91("RESTORED_REVIEW",revision91(p,current),p.getProperty("transaction"),id(p),p.getProperty("version",""),p.getProperty("priorVersion",""),false);
            }
            return new RecoveryView91("NONE","","","","","",false);
        }
    }
    /** Journal decisions only. RESTORE never replaces a JAR in the calling game. */
    public synchronized void decide91(String revision,String action)throws IOException {
        try(Guard ignored=lock()){
            if(revision==null||!revision.matches("[0-9a-f]{64}")||action==null||!Set.of("ACK","KEEP","RESTORE").contains(action))throw new IOException("RECOVERY91_ACTION");
            RecoveryView91 view=recovery91();if(revision.isEmpty()||!view.revision().equals(revision))throw new IOException("RECOVERY91_STALE");
            if(action.equals("ACK")&&view.phase().equals("RESTORED_REVIEW")){Files.delete(root.resolve("world-review91.properties"));return;}
            if(!Set.of("CHOICE_REQUIRED","RESTORE_REQUESTED").contains(view.phase())||!Set.of("KEEP","RESTORE").contains(action))throw new IOException("RECOVERY91_ACTION");
            Properties p=read(trial());
            if(action.equals("KEEP")){
                if(!"APPLIED".equals(p.getProperty("phase"))||!currentHash(target(p)).equals(p.getProperty("sha256")))throw new IOException("RECOVERY91_JAR_CHANGED");
                if(Files.exists(root.resolve("world-review91.properties")))throw new IOException("RECOVERY91_RESTORE_STARTED");
                p.setProperty("accept91","MANUAL");p.setProperty("phase","COMMITTED");write(trial(),p);finishCommit(p);
            }else{p.setProperty("worldRisk91","true");p.setProperty("recovery91","RESTORE_REQUESTED");write(trial(),p);}
        }
    }
    private void report(Properties p,String status,String detail)throws IOException {
        var report=new Properties();
        report.putAll(p);
        report.setProperty("status",status);
        report.setProperty("detail",detail);
        write(root.resolve("last-result.properties"),report);
    }
    public synchronized String status(String project)throws IOException {
        try (Guard ignored = lock()) {
            var known=read(catalog());
            String mod=known.getProperty("project."+project+".modId","");
            StringBuilder text=new StringBuilder("Project: "+project+"\n");
            if(!mod.isEmpty())text.append("Confirmed: ").append(mod).append(" ").append(known.getProperty(mod+".version","")).append("\nJAR: ").append(mods.resolve("mared-dev-"+mod+".jar")).append('\n');
            else text.append("No confirmed mod version\n");
            for(Path file:List.of(pending(),trial(),root.resolve("last-result.properties"))) {
                var record=read(file);
                if(project.equals(record.getProperty("project")))text.append(file.getFileName()).append(": ").append(record.getProperty("status",record.getProperty("phase","PENDING"))).append(' ').append(record.getProperty("detail","")).append('\n');
            }
            return text.toString();
        }
    }
    public synchronized Path exportConfirmed(String project,Path destination)throws IOException {
        try (Guard ignored = lock()) {
            directories();
            var known=read(catalog());
            String mod=known.getProperty("project."+project+".modId","");
            var p=new Properties();
            p.setProperty("modId",mod);
            Path source=target(p);
            String expected=known.getProperty(mod+".sha256","");
            if(expected.isEmpty()||!currentHash(source).equals(expected))throw new IOException("No unchanged confirmed mod is installed for this project");
            safe(destination);
            Files.createDirectories(destination);
            Path folder=Files.createTempDirectory(destination,"mod-export-"),out=folder.resolve(mod+"-"+expected.substring(0,8)+".jar");
            try {
                Files.copy(source,out);
                if(!hash(out).equals(expected)||!hash(source).equals(expected))throw new IOException("Confirmed mod changed during export");
                return out;
            }
            catch(IOException e) {
                Files.deleteIfExists(out);
                Files.deleteIfExists(folder);
                throw e;
            }
        }
    }
    private void clearPendingFor(Properties p)throws IOException {
        if(p.getProperty("transaction").equals(read(pending()).getProperty("transaction")))Files.deleteIfExists(pending());
    }
    private void cleanup(Properties p)throws IOException {
        Path dir=transaction(p);
        Files.deleteIfExists(dir.resolve("candidate.jar"));
        Files.deleteIfExists(dir.resolve("previous.jar"));
        Files.deleteIfExists(dir.resolve("resume.properties"));
        Files.deleteIfExists(dir);
    }
}
