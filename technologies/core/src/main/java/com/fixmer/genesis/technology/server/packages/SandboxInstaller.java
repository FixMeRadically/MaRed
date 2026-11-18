package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;
import static com.fixmer.genesis.technology.server.packages.InstallJournal.Event.*;

/** Diagnostic transaction engine. Public handles can only be created for NEW profiles; no live-game apply. */
public final class SandboxInstaller implements AutoCloseable {
    /** A handle exists only for a freshly created diagnostic SERVER profile. Never opens an existing game for apply. */
    public static final class DiagnosticSession implements AutoCloseable {
        private final SandboxInstaller installer;
        private DiagnosticSession(SandboxInstaller installer){this.installer=installer;}
        public UUID profileId(){return installer.id;}
        public synchronized InstalledReceipt installed()throws IOException{
            if(installer.closed)throw new IOException("DIAGNOSTIC_SESSION_CLOSED");
            return installer.receipt();
        }
        public synchronized void install(PackageStaging.Prepared next,String expectedPreviousArchive)throws IOException{
            if(installer.closed||installer.poisoned)throw new IOException("DIAGNOSTIC_SESSION_CLOSED_OR_RECOVERY_REQUIRED");
            InstalledReceipt current=installer.receipt();
            String actual=current==null?null:current.verified().archiveSha256();
            if(!Objects.equals(expectedPreviousArchive,actual))throw new IOException("DIAGNOSTIC_INSTALLED_RECEIPT_PRECONDITION");
            installer.apply(next);
        }
        @Override public synchronized void close()throws IOException{installer.close();}
    }
    public static DiagnosticSession createDiagnostic(Path NEWprofile,Path cache,Map<String,PublicKey> trust,Target target)throws IOException{
        diagnosticPaths(NEWprofile,cache,target);
        if(Files.exists(NEWprofile,LinkOption.NOFOLLOW_LINKS))throw new IOException("DIAGNOSTIC_NEW_PROFILE_REQUIRED");
        return new DiagnosticSession(create(NEWprofile,cache,trust,target));
    }
    /** Explicit recovery only. Does not return an apply handle and never rolls an unfinished update forward. */
    public static Map<String,Object> recoverDiagnostic(Path profile,Path cache,Map<String,PublicKey> trust,Target target)throws IOException{
        diagnosticPaths(profile,cache,target);
        try(var session=new SandboxInstaller(profile,cache,trust,target,InstallIo.NONE)){
            InstalledReceipt receipt=session.receipt();
            var result=new LinkedHashMap<String,Object>();
            result.put("diagnosticOnly",true);result.put("profile",session.id);
            result.put("recovery",session.recovery);result.put("recordedArchive",receipt==null?null:receipt.verified().archiveSha256());
            result.put("ownerEpoch",session.admission().epoch());result.put("productionLaunchAllowed",false);result.put("installAuthority",false);
            return result;
        }
    }
    private static void diagnosticPaths(Path profile,Path cache,Target target)throws IOException{
        Objects.requireNonNull(target);
        if(target.side()!=Side.SERVER)throw new IOException("DIAGNOSTIC_SERVER_TARGET_REQUIRED");
        profile=profile.toAbsolutePath().normalize();cache=cache.toAbsolutePath().normalize();
        PackageFiles.safe(profile);PackageFiles.safe(cache);
        if(profile.startsWith(cache)||cache.startsWith(profile))throw new IOException("DIAGNOSTIC_CACHE_PROFILE_OVERLAP");
        Path parent=profile.getParent();
        if(parent==null||!parent.equals(parent.toRealPath())||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))throw new IOException("DIAGNOSTIC_CANONICAL_PARENT_REQUIRED");
        if(Files.exists(profile,LinkOption.NOFOLLOW_LINKS)&&!profile.equals(profile.toRealPath()))throw new IOException("DIAGNOSTIC_PROFILE_ALIAS");
        if(!cache.equals(cache.toRealPath()))throw new IOException("DIAGNOSTIC_CACHE_ALIAS");
    }
    record Task(String path,String before,String after){}
    record Descriptor(UUID profile,List<Task> tasks,byte[] before,byte[] after){}
    final Path profile,meta,cache;final UUID id;final Target target;final Map<String,PublicKey> trust;final InstallIo.Lease lease;final InstallIo.Fault fault;
    private final InstallIo.Fault delegate;
    private ProfileCoordinator coordinator;private ProfileCoordinator.InstallPermit installPermit;private ProfileCoordinator.RecoveryPermit recoveryPermit;
    boolean poisoned,closed;String recovery="none";
    private void guardedFault(String point)throws IOException{validateAdmission();delegate.at(point);validateAdmission();}
    private void validateAdmission()throws IOException{if(installPermit!=null)coordinator.validateInstall(installPermit);if(recoveryPermit!=null)coordinator.validateRecovery(recoveryPermit);}
    ProfileCoordinator.Snapshot admission(){return coordinator.snapshot();}
    static SandboxInstaller create(Path profile,Path cache,Map<String,PublicKey> trust,Target target)throws IOException{return create(profile,cache,trust,target,InstallIo.NONE);}
    static SandboxInstaller create(Path profile,Path cache,Map<String,PublicKey> trust,Target target,InstallIo.Fault fault)throws IOException{
        profile=profile.toAbsolutePath().normalize();PackageFiles.safe(profile);Files.createDirectory(profile);PackageFiles.privateMode(profile,true);Path meta=profile.resolve(".genesis-install");InstallIo.directory(meta);UUID id=UUID.randomUUID();
        InstallIo.atomic(meta.resolve("sandbox"),InstallIo.framed("GENESIS-INSTALL-SANDBOX-1\n"+id+"\n"+target.distribution()+"\n"+target.side()+"\n"+target.minecraft()+"\n"),InstallIo.NONE,"sandbox");InstallIo.directory(meta.resolve("transactions"));InstallIo.directory(profile.resolve("mods"));return new SandboxInstaller(profile,cache,trust,target,fault);
    }
    // Package-private recovery is exercised only by our fixture harness. There is deliberately no CLI apply/recover <game-folder>.
    SandboxInstaller(Path profile,Path cache,Map<String,PublicKey> trust,Target target,InstallIo.Fault fault)throws IOException{
        this.profile=profile.toAbsolutePath().normalize();this.meta=this.profile.resolve(".genesis-install");this.cache=cache.toAbsolutePath().normalize();this.target=target;this.trust=Map.copyOf(trust);this.delegate=fault;this.fault=this::guardedFault;PackageFiles.safe(this.profile);
        String[] marker=InstallIo.unframe(PackageFiles.read(meta.resolve("sandbox"),1024),5).split("\n");
        try{if(!marker[0].equals("GENESIS-INSTALL-SANDBOX-1")||!marker[2].equals(target.distribution().toString())||!marker[3].equals(target.side().name())||!marker[4].equals(target.minecraft()))throw new IOException("NOT_MATCHING_DIAGNOSTIC_PROFILE");id=UUID.fromString(marker[1]);nonzero(id);}catch(IllegalArgumentException bad){throw new IOException("INVALID_PROFILE_ID",bad);}
        lease=new InstallIo.Lease(meta.resolve("profile.lock"));try{
            coordinator=ProfileCoordinator.openDiagnostic(this.profile,id,target.distribution(),target.side());UUID active=null;Path pointer=meta.resolve("active");if(Files.exists(pointer,LinkOption.NOFOLLOW_LINKS)){String[] f=InstallIo.unframe(PackageFiles.read(pointer,512),3).split("\n");if(!f[0].equals("GENESIS-ACTIVE-1"))throw new IOException("ACTIVE_POINTER_CORRUPT");active=AdmissionStore.uuid(f[1]);}
            recoveryPermit=coordinator.beginDiagnosticRecovery(active);recover();receipt();if(recoveryPermit!=null){coordinator.completeDiagnosticRecovery(recoveryPermit);recoveryPermit=null;}
        }catch(IOException|RuntimeException bad){if(coordinator!=null)try{coordinator.close();}catch(IOException extra){bad.addSuppressed(extra);}lease.close();throw bad;}
    }
    synchronized InstalledReceipt receipt()throws IOException{if(Files.exists(meta.resolve("active"),LinkOption.NOFOLLOW_LINKS))throw new IOException("INSTALLATION_OUTCOME_PENDING");return Files.exists(meta.resolve("installed"),LinkOption.NOFOLLOW_LINKS)?InstalledReceipt.read(meta.resolve("installed"),cache,trust,target,id):null;}
    synchronized void apply(PackageStaging.Prepared next)throws IOException{
        if(closed||poisoned)throw new IOException("SESSION_CLOSED_OR_RECOVERY_REQUIRED");if(!next.cache().equals(cache)||!next.target().equals(target))throw new IOException("PREPARED_TARGET_OR_CACHE_MISMATCH");
        try{
            next.recheck();InstalledReceipt current=receipt();var plan=PackagePlanner.plan(current==null?null:current.verified(),next.verified(),target,profile);if(!plan.ready())throw new IOException("PROFILE_CONFLICT");
            var tasks=new ArrayList<Task>();for(var c:plan.changes())if(Set.of(PackagePlanner.Action.ADD,PackagePlanner.Action.RESTORE_MISSING,PackagePlanner.Action.REPLACE,PackagePlanner.Action.REMOVE).contains(c.action()))tasks.add(new Task(c.path(),c.observedBefore(),c.expectedAfter()));
            byte[] before=Files.exists(meta.resolve("installed"))?PackageFiles.read(meta.resolve("installed"),2048):null,after=InstalledReceipt.encode(id,next);if(tasks.isEmpty()&&Arrays.equals(before,after))return;var descriptor=new Descriptor(id,List.copyOf(tasks),before,after);UUID transactionId=UUID.randomUUID();installPermit=coordinator.reserveDiagnosticInstall(transactionId,next.verified().archiveSha256());
            Path transaction=meta.resolve("transactions").resolve(transactionId.toString());InstallIo.directory(transaction);InstallIo.directory(transaction.resolve("backup"));
            for(int i=0;i<tasks.size();i++){Task task=tasks.get(i);noAlias(task.path());InstallIo.same(task.before(),InstallIo.observed(profile.resolve(task.path())));if(task.before()!=null)InstallIo.copy(profile.resolve(task.path()),backup(transaction,i),task.before(),MAX_FILE,fault,"backup."+i);}
            byte[] encoded=encode(descriptor);String hash=PackageFiles.digest(encoded);InstallIo.atomic(transaction.resolve("descriptor"),encoded,fault,"descriptor");
            try(var journal=new InstallJournal(transaction.resolve("journal"),hash,tasks.size(),fault)){
                journal.append(PREPARED,-1);InstallIo.atomic(meta.resolve("active"),InstallIo.framed("GENESIS-ACTIVE-1\n"+transaction.getFileName()+"\n"+hash+"\n"),fault,"active");coordinator.markInstalling(installPermit);
                for(int i=0;i<tasks.size();i++){
                    Task task=tasks.get(i);noAlias(task.path());InstallIo.same(task.before(),InstallIo.observed(profile.resolve(task.path())));journal.append(INTENT,i);noAlias(task.path());InstallIo.same(task.before(),InstallIo.observed(profile.resolve(task.path())));
                    if(task.after()==null){Files.delete(profile.resolve(task.path()));fault.at("apply."+i+".delete");}
                    else{Entry entry=next.verified().manifest().forSide(target.side()).stream().filter(e->e.path().equals(task.path())).findFirst().orElseThrow();InstallIo.copyChecked(next.object(entry),profile.resolve(task.path()),task.after(),MAX_FILE,fault,"apply."+i,task.before(),transaction.resolve("incoming"));}
                    InstallIo.same(task.after(),InstallIo.observed(profile.resolve(task.path())));journal.append(DONE,i);
                }
                journal.append(COMMIT_INTENT,tasks.size());InstallIo.atomic(meta.resolve("installed"),after,fault,"receipt");journal.append(COMMITTED,tasks.size());
            }
            Files.delete(meta.resolve("active"));fault.at("active.clear");coordinator.completeInstall(installPermit);installPermit=null;
        }catch(IOException|RuntimeException bad){poisoned=true;throw bad;}
    }
    private void recover()throws IOException{
        Path pointer=meta.resolve("active");if(!Files.exists(pointer,LinkOption.NOFOLLOW_LINKS))return;String[] active=InstallIo.unframe(PackageFiles.read(pointer,512),3).split("\n");
        if(!active[0].equals("GENESIS-ACTIVE-1"))throw new IOException("ACTIVE_POINTER_CORRUPT");UUID tx;try{tx=UUID.fromString(active[1]);if(!tx.toString().equals(active[1]))throw new IllegalArgumentException();nonzero(tx);PackageModel.hash(active[2]);}catch(IllegalArgumentException bad){throw new IOException("ACTIVE_POINTER_CORRUPT",bad);}
        Path transaction=meta.resolve("transactions").resolve(tx.toString());byte[] encoded=PackageFiles.read(transaction.resolve("descriptor"),MAX_MANIFEST);if(!PackageFiles.digest(encoded).equals(active[2]))throw new IOException("DESCRIPTOR_CHECKSUM");Descriptor descriptor=decode(encoded);if(!descriptor.profile().equals(id))throw new IOException("TRANSACTION_PROFILE_MISMATCH");InstalledReceipt.validateLocal(descriptor.after(),id,target.side());if(descriptor.before()!=null)InstalledReceipt.validateLocal(descriptor.before(),id,target.side());
        try(var journal=new InstallJournal(transaction.resolve("journal"),active[2],descriptor.tasks().size(),fault)){
            var state=journal.state();if(state==null)throw new IOException("MISSING_PREPARED_JOURNAL");
            if(state.event()==COMMITTED){verifyFiles(descriptor,true);sameBytes(descriptor.after(),localReceipt());recovery="committed";}
            else if(state.event()==ROLLED_BACK){verifyFiles(descriptor,false);sameBytes(descriptor.before(),localReceipt());recovery="rolled_back";}
            else{
                int rollbackCode=state.event()==COMMIT_INTENT?descriptor.tasks().size()+1:state.index();boolean mayHaveReceipt=rollbackCode==descriptor.tasks().size()+1;int attempted=mayHaveReceipt?descriptor.tasks().size()-1:rollbackCode;
                // Validate every affected path before the first recovery write; preserve unknown edits.
                byte[] receipt=localReceipt();if(!Arrays.equals(receipt,descriptor.before())&&!(mayHaveReceipt&&Arrays.equals(receipt,descriptor.after())))throw new IOException("RECEIPT_CHANGED_OUTSIDE_TRANSACTION");
                for(int i=0;i<descriptor.tasks().size();i++){Task t=descriptor.tasks().get(i);noAlias(t.path());String actual=InstallIo.observed(profile.resolve(t.path()));if(!Objects.equals(actual,t.before())&&!(i<=attempted&&Objects.equals(actual,t.after())))throw new IOException("RECOVERY_FILE_CHANGED_OUTSIDE_TRANSACTION");if(t.before()!=null)InstallIo.same(t.before(),InstallIo.observed(backup(transaction,i)));}
                if(state.event()!=ROLLBACK_BEGIN)journal.append(ROLLBACK_BEGIN,rollbackCode);
                for(int i=descriptor.tasks().size()-1;i>=0;i--){Task t=descriptor.tasks().get(i);String actual=InstallIo.observed(profile.resolve(t.path()));if(Objects.equals(actual,t.before()))continue;if(i>attempted||!Objects.equals(actual,t.after()))throw new IOException("RECOVERY_CHANGED_DURING_ROLLBACK");
                    if(t.before()==null){Files.delete(profile.resolve(t.path()));fault.at("rollback."+i+".delete");}else InstallIo.copyChecked(backup(transaction,i),profile.resolve(t.path()),t.before(),MAX_FILE,fault,"rollback."+i,t.after(),transaction.resolve("incoming"));
                }
                sameBytes(receipt,localReceipt());if(descriptor.before()==null){Files.deleteIfExists(meta.resolve("installed"));fault.at("rollback.receipt.delete");}else InstallIo.atomic(meta.resolve("installed"),descriptor.before(),fault,"rollback.receipt");
                verifyFiles(descriptor,false);journal.append(ROLLED_BACK,rollbackCode);recovery="rolled_back";
            }
        }
        Files.delete(pointer);fault.at("recovery.clear");
    }
    private byte[] localReceipt()throws IOException{return Files.exists(meta.resolve("installed"),LinkOption.NOFOLLOW_LINKS)?PackageFiles.read(meta.resolve("installed"),2048):null;}
    private void verifyFiles(Descriptor descriptor,boolean after)throws IOException{for(Task task:descriptor.tasks()){noAlias(task.path());InstallIo.same(after?task.after():task.before(),InstallIo.observed(profile.resolve(task.path())));}}
    private void noAlias(String path)throws IOException{
        Path mods=profile.resolve("mods");PackageFiles.safe(mods);if(!Files.isDirectory(mods,LinkOption.NOFOLLOW_LINKS))throw new IOException("MODS_DIRECTORY_CHANGED");int n=0;try(var files=Files.list(mods)){var it=files.iterator();while(it.hasNext()){if(++n>4096)throw new IOException("PROFILE_ENTRY_LIMIT");String actual="mods/"+it.next().getFileName();if(actual.equalsIgnoreCase(path)&&!actual.equals(path))throw new IOException("PROFILE_CASE_ALIAS");}}
    }
    private static Path backup(Path transaction,int i){return transaction.resolve("backup").resolve(i+".blob");}
    private static byte[] encode(Descriptor d){StringBuilder text=new StringBuilder("GENESIS-INSTALL-TX-1\n").append(d.profile()).append('\n').append(d.tasks().size()).append('\n').append(b64(d.before())).append('\n').append(b64(d.after())).append('\n');for(Task task:d.tasks())text.append(task.path()).append('|').append(nullable(task.before())).append('|').append(nullable(task.after())).append('\n');return InstallIo.framed(text.toString());}
    private static Descriptor decode(byte[] bytes)throws IOException{
        String[] fields=new String(bytes,StandardCharsets.UTF_8).split("\n",-1);if(fields.length<7)throw new IOException("DESCRIPTOR_FORMAT");try{int count=Integer.parseInt(fields[2]);if(count<0||count>MAX_FILES*2)throw new IOException("DESCRIPTOR_TASK_LIMIT");InstallIo.unframe(bytes,5+count);if(!fields[0].equals("GENESIS-INSTALL-TX-1"))throw new IOException("DESCRIPTOR_SCHEMA");UUID profile=UUID.fromString(fields[1]);nonzero(profile);byte[] before=unb64(fields[3]),after=unb64(fields[4]);if(after==null)throw new IOException("MISSING_NEXT_RECEIPT");var tasks=new ArrayList<Task>();String previous="";var names=new HashSet<String>();for(int i=0;i<count;i++){String[] f=fields[5+i].split("\\|",-1);if(f.length!=3)throw new IOException("DESCRIPTOR_TASK");String path=modPath(f[0]);if(path.compareTo(previous)<=0||!names.add(path.toLowerCase(Locale.ROOT)))throw new IOException("DESCRIPTOR_PATH_ORDER");previous=path;String old=hashOrNull(f[1]),next=hashOrNull(f[2]);if(Objects.equals(old,next))throw new IOException("DESCRIPTOR_REDUNDANT_TASK");tasks.add(new Task(path,old,next));}return new Descriptor(profile,List.copyOf(tasks),before,after);}catch(IllegalArgumentException bad){throw new IOException("DESCRIPTOR_FORMAT",bad);}
    }
    private static String nullable(String value){return value==null?"-":value;}
    private static String hashOrNull(String text){return text.equals("-")?null:PackageModel.hash(text);}
    private static String b64(byte[] bytes){return bytes==null?"-":Base64.getEncoder().encodeToString(bytes);}
    private static byte[] unb64(String value)throws IOException{if(value.equals("-"))return null;byte[] bytes=Base64.getDecoder().decode(value);if(bytes.length>2048||!Base64.getEncoder().encodeToString(bytes).equals(value))throw new IOException("DESCRIPTOR_RECEIPT_ENCODING");return bytes;}
    private static void sameBytes(byte[] a,byte[] b)throws IOException{if(!Arrays.equals(a,b))throw new IOException("RECEIPT_PRECONDITION_CHANGED");}
    public synchronized void close()throws IOException{if(closed)return;closed=true;try{if(coordinator!=null)coordinator.close();}finally{lease.close();}}
}
