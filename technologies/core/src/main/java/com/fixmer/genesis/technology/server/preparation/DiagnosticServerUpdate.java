package com.fixmer.genesis.technology.server.preparation;

import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** End-to-end sequential transaction in a NEW diagnostic profile. No game, stop proof or LIVE admission. */
public final class DiagnosticServerUpdate {
    private static final Set<String> FIELDS=Set.of("schema","scope","root","profile","profileId","distribution","minecraft","loader","previousLoader","signer","previousArchive","nextArchive","intentSha256","sealSha256","beforeSha256","afterSha256","sandboxSha256");
    public record Request(Path previous,Path next,Path publicKey,UUID distribution,String minecraft,String previousLoader,String nextLoader){
        public Request{
            Objects.requireNonNull(previous);Objects.requireNonNull(next);Objects.requireNonNull(publicKey);
            new PackageModel.Target(distribution,PackageModel.Side.SERVER,minecraft,previousLoader,1);
            new PackageModel.Target(distribution,PackageModel.Side.SERVER,minecraft,nextLoader,1);
        }
    }
    private DiagnosticServerUpdate(){}

    /** All writes are confined to a fresh directory; source archives and the key are read only. */
    public static Map<String,Object> execute(Request request,Path NEWroot)throws IOException{
        Path root=NEWroot.toAbsolutePath().normalize();
        PreparationFiles.safe(root);PreparationFiles.existing(root.getParent());
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("DIAGNOSTIC_NEW_DIRECTORY_REQUIRED");
        byte[] keyBytes=PreparationFiles.read(request.publicKey(),4096);PublicKey key=key(keyBytes);
        var trust=Map.of(PreparationFiles.digest(key.getEncoded()),key);
        var target=new PackageModel.Target(request.distribution(),PackageModel.Side.SERVER,request.minecraft(),request.nextLoader(),1);
        var oldTarget=new PackageModel.Target(request.distribution(),PackageModel.Side.SERVER,request.minecraft(),request.previousLoader(),1);
        Path previous=PreparationFiles.existing(request.previous()),next=PreparationFiles.existing(request.next());
        var oldVerified=PackageArchive.verify(previous,trust,oldTarget);var nextVerified=PackageArchive.verify(next,trust,target);
        if(oldVerified.manifest().parent()!=null)throw new IOException("DIAGNOSTIC_INITIAL_PACKAGE_MUST_HAVE_NO_PARENT");
        if(!oldVerified.manifest().release().equals(nextVerified.manifest().parent())||oldVerified.manifest().release().equals(nextVerified.manifest().release()))throw new IOException("DIAGNOSTIC_DISTINCT_PARENT_CHAIN_REQUIRED");
        if(!oldTarget.equals(target))throw new IOException("DIAGNOSTIC_LOADER_MIGRATION_NOT_IMPLEMENTED");
        PreparationFiles.newDirectory(root);
        try(var lease=lease(root,true)){
            PreparationFiles.write(root.resolve("public.der"),keyBytes);
            Path cache=root.resolve("cache"),profile=root.resolve("profile");
            var old=PackageStaging.prepare(cache,previous,trust,oldTarget);var staged=PackageStaging.prepare(cache,next,trust,target);
            try(var session=SandboxInstaller.createDiagnostic(profile,cache,trust,target)){
                session.install(old,null);
                /* VERIFY53_BASELINE */
                var installed=session.installed();
                if(installed==null||!installed.verified().archiveSha256().equals(old.verified().archiveSha256()))throw new IOException("DIAGNOSTIC_BASELINE_RECEIPT_REQUIRED");
                var prepared=ServerUpdatePreparation.prepare(new ServerUpdatePreparation.Request(profile,cache,next,previous,root.resolve("public.der"),request.distribution(),request.minecraft(),request.nextLoader(),request.previousLoader()),root.resolve("preparation"));
                String pin=(String)prepared.get("intentSha256");
                if(!Boolean.TRUE.equals(prepared.get("readyForReview")))throw new IOException("DIAGNOSTIC_PREPARATION_NOT_READY");
                var before=PreparationFiles.inventory(profile);var after=expected(before,old,staged);
                PreparationFiles.write(root.resolve("before.bin"),PreparationCodec.encode(before));
                PreparationFiles.write(root.resolve("after.bin"),PreparationCodec.encode(after));
                var cycle=new TreeMap<String,String>();
                cycle.put("schema","53");cycle.put("scope","NEW_DIAGNOSTIC_SERVER_ONLY");cycle.put("root",root.toString());cycle.put("profile",profile.toString());
                cycle.put("sandboxSha256",PreparationFiles.hash(profile.resolve(".genesis-install/sandbox"),1024));
                cycle.put("profileId",session.profileId().toString());cycle.put("distribution",request.distribution().toString());cycle.put("minecraft",request.minecraft());cycle.put("loader",request.nextLoader());cycle.put("previousLoader",request.previousLoader());
                cycle.put("signer",PreparationFiles.digest(key.getEncoded()));cycle.put("previousArchive",old.verified().archiveSha256());cycle.put("nextArchive",staged.verified().archiveSha256());
                cycle.put("intentSha256",pin);cycle.put("sealSha256",PreparationFiles.hash(root.resolve("preparation/seal.bin"),PreparationFiles.MAX_RECORD));
                cycle.put("beforeSha256",PreparationFiles.digest(PreparationCodec.encode(before)));cycle.put("afterSha256",PreparationFiles.digest(PreparationCodec.encode(after)));
                PreparationFiles.write(root.resolve("cycle.bin"),PreparationCodec.encode(cycle));
                /* VERIFY53_CYCLE */
                verifyCycle(root,digestCycle(root));
                if(!Boolean.TRUE.equals(ServerUpdatePreparation.recheck(root.resolve("preparation"),pin).get("readyForReview")))throw new IOException("DIAGNOSTIC_STALE_PREPARATION");
                // Re-read the real receipt immediately before the existing installer independently replans all managed paths.
                session.install(staged,cycle.get("previousArchive"));
                /* VERIFY53_APPLIED */
            }
            var report=recheck(root,digestCycle(root));
            if(!report.get("outcome").equals("COMMITTED_VERIFIED"))throw new IOException("DIAGNOSTIC_COMMIT_NOT_VERIFIED");
            publish(root,report);
            /* VERIFY53_REPORTED */
            report.put("readOnly",false);report.put("diagnosticStorageModified",true);
            return report;
        }
    }

    /** Historical metadata only. Does not create a lease, recover, or assert current files. */
    public static Map<String,Object> status(Path root)throws IOException{
        root=PreparationFiles.existing(root);var cycle=readCycle(root,null);var report=base(cycle,digestCycle(root));
        report.put("historical",true);report.put("outcome","RECORDED_INTENT_RECHECK_REQUIRED");
        Path result=root.resolve("result.bin");
        if(Files.exists(result,LinkOption.NOFOLLOW_LINKS)){
            var recorded=PreparationCodec.decode(PreparationFiles.read(result,PreparationFiles.MAX_RECORD));
            if(!recorded.keySet().equals(Set.of("schema","cycleSha256","outcome"))||!recorded.get("schema").equals("53")||!recorded.get("cycleSha256").equals(report.get("cycleSha256"))||!Set.of("COMMITTED_VERIFIED","BASELINE_VERIFIED_NO_UPDATE").contains(recorded.get("outcome")))throw new IOException("DIAGNOSTIC_RESULT_CORRUPT_OR_REBOUND");
            report.put("recordedOutcome",recorded.get("outcome"));
        }
        if(!cycle.equals(readCycle(root,(String)report.get("cycleSha256"))))throw new IOException("DIAGNOSTIC_CHANGED_DURING_STATUS");
        return report;
    }

    /** Full read-only receipt + signature + CAS + complete flat mods inventory verification. No process authority. */
    public static Map<String,Object> recheck(Path root,String expectedCycleSha256)throws IOException{
        requirePin(expectedCycleSha256);root=PreparationFiles.existing(root);var cycle=verifyCycle(root,expectedCycleSha256);
        var admission=ProfileCoordinator.inspect(root.resolve("profile"));
        if(!admission.profile().toString().equals(cycle.get("profileId"))||admission.scope()!=ProfileCoordinator.Scope.DIAGNOSTIC_NO_GAME)throw new IOException("DIAGNOSTIC_ADMISSION_IDENTITY");
        var report=base(cycle,expectedCycleSha256);report.put("historical",false);report.put("ownerEpoch",admission.epoch());report.put("admissionPhase",admission.phase());
        boolean pending=Files.exists(root.resolve("profile/.genesis-install/active"),LinkOption.NOFOLLOW_LINKS)||admission.phase()!=ProfileCoordinator.Phase.IDLE;
        if(pending){report.put("outcome","RECOVERY_REQUIRED");return report;}
        var target=target(cycle,false);var trust=trust(root,cycle);
        Path receiptFile=root.resolve("profile/.genesis-install/installed");byte[] receiptBefore=PreparationFiles.read(receiptFile,2048);
        var receipt=InstalledReceipt.read(receiptFile,root.resolve("cache"),trust,target,UUID.fromString(cycle.get("profileId")));
        /* VERIFY53_READ_RECEIPT */
        String archive=receipt.verified().archiveSha256();
        boolean committed=archive.equals(cycle.get("nextArchive"));
        if(!committed&&!archive.equals(cycle.get("previousArchive")))throw new IOException("DIAGNOSTIC_UNEXPECTED_INSTALLED_RECEIPT");
        var inventory=PreparationFiles.inventory(root.resolve("profile"));
        var expected=PreparationCodec.decode(PreparationFiles.read(root.resolve(committed?"after.bin":"before.bin"),PreparationFiles.MAX_RECORD));
        if(!inventory.equals(expected))throw new IOException("DIAGNOSTIC_PROFILE_CHANGED_REVIEW_REQUIRED");
        // Restoring a missing owned file is a write, not a successful verification.
        var exactTarget=target(cycle,!committed);
        var own=PackagePlanner.plan(receipt.verified(),receipt.verified(),exactTarget,root.resolve("profile"));
        if(!own.ready()||own.writesPlanned()!=0)throw new IOException("DIAGNOSTIC_OWNED_FILES_NOT_EXACT");
        if(!Arrays.equals(receiptBefore,PreparationFiles.read(receiptFile,2048))||!admission.equals(ProfileCoordinator.inspect(root.resolve("profile")))||Files.exists(root.resolve("profile/.genesis-install/active"),LinkOption.NOFOLLOW_LINKS)||!cycle.equals(verifyCycle(root,expectedCycleSha256)))throw new IOException("DIAGNOSTIC_CHANGED_DURING_RECHECK");
        report.put("outcome",committed?"COMMITTED_VERIFIED":"BASELINE_VERIFIED_NO_UPDATE");report.put("installedReceiptVerified",true);report.put("diagnosticFilesVerified",true);report.put("installedArchive",archive);
        return report;
    }

    /** Explicit, bounded recovery of this fresh diagnostic transaction; never replays apply after a crash. */
    public static Map<String,Object> recover(Path root,String expectedCycleSha256,boolean confirmed)throws IOException{
        if(!confirmed)throw new IOException("DIAGNOSTIC_RECOVERY_CONFIRMATION_REQUIRED");requirePin(expectedCycleSha256);root=PreparationFiles.existing(root);
        // Validate immutable pins before opening anything capable of recovery writes.
        var cycle=verifyCycle(root,expectedCycleSha256);
        try(var lease=lease(root,false)){
            if(!cycle.equals(verifyCycle(root,expectedCycleSha256)))throw new IOException("DIAGNOSTIC_CHANGED_BEFORE_RECOVERY");
            var recovered=SandboxInstaller.recoverDiagnostic(root.resolve("profile"),root.resolve("cache"),trust(root,cycle),target(cycle,false));
            /* VERIFY53_RECOVERED */
            var report=recheck(root,expectedCycleSha256);
            if(!Boolean.TRUE.equals(report.get("diagnosticFilesVerified")))throw new IOException("DIAGNOSTIC_RECOVERY_NOT_SETTLED");
            report.put("recovery",recovered.get("recovery"));publish(root,report);
            report.put("readOnly",false);report.put("diagnosticStorageModified",true);return report;
        }
    }

    private static Map<String,String> verifyCycle(Path root,String pin)throws IOException{
        var cycle=readCycle(root,pin);
        var trust=trust(root,cycle);
        var old=PackageStaging.load(root.resolve("cache"),cycle.get("previousArchive"),trust,target(cycle,true));
        var next=PackageStaging.load(root.resolve("cache"),cycle.get("nextArchive"),trust,target(cycle,false));
        if(old.verified().manifest().parent()!=null||!old.verified().manifest().release().equals(next.verified().manifest().parent())||old.verified().manifest().release().equals(next.verified().manifest().release()))throw new IOException("DIAGNOSTIC_PARENT_CHAIN_CHANGED");
        for(var entry:Map.of("before.bin","beforeSha256","after.bin","afterSha256","preparation/intent.bin","intentSha256","preparation/seal.bin","sealSha256","profile/.genesis-install/sandbox","sandboxSha256").entrySet())
            if(!PreparationFiles.hash(root.resolve(entry.getKey()),PreparationFiles.MAX_RECORD).equals(cycle.get(entry.getValue())))throw new IOException("DIAGNOSTIC_FROZEN_METADATA_CHANGED");
        // Enforce that the prepared plan's own bindings still refer to this profile, cache and these packages.
        var intent=PreparationCodec.decode(PreparationFiles.read(root.resolve("preparation/intent.bin"),PreparationFiles.MAX_RECORD));
        if(!intent.get("profile").equals(cycle.get("profile"))||!intent.get("cache").equals(root.resolve("cache").toString())||!intent.get("nextSha256").equals(cycle.get("nextArchive"))||!intent.get("previousSha256").equals(cycle.get("previousArchive")))throw new IOException("DIAGNOSTIC_PREPARATION_BINDING");
        var admission=ProfileCoordinator.inspect(root.resolve("profile"));
        if(!admission.profile().toString().equals(cycle.get("profileId"))||admission.scope()!=ProfileCoordinator.Scope.DIAGNOSTIC_NO_GAME)throw new IOException("DIAGNOSTIC_ADMISSION_IDENTITY");
        return cycle;
    }
    private static Map<String,String> readCycle(Path root,String pin)throws IOException{
        byte[] bytes=PreparationFiles.read(root.resolve("cycle.bin"),PreparationFiles.MAX_RECORD);
        if(pin!=null&&!PreparationFiles.digest(bytes).equals(pin))throw new IOException("DIAGNOSTIC_CYCLE_PIN_MISMATCH");
        var cycle=PreparationCodec.decode(bytes);
        if(!cycle.keySet().equals(FIELDS)||!cycle.get("schema").equals("53")||!cycle.get("scope").equals("NEW_DIAGNOSTIC_SERVER_ONLY")||!cycle.get("root").equals(root.toString())||!cycle.get("profile").equals(root.resolve("profile").toString()))throw new IOException("DIAGNOSTIC_CYCLE_SCHEMA_OR_PATH");
        try{
            UUID profileId=UUID.fromString(cycle.get("profileId"));if(profileId.getMostSignificantBits()==0&&profileId.getLeastSignificantBits()==0)throw new IllegalArgumentException("ZERO_PROFILE_UUID");
            target(cycle,false);target(cycle,true);
            for(String field:List.of("signer","previousArchive","nextArchive","intentSha256","sealSha256","beforeSha256","afterSha256","sandboxSha256"))hash(cycle.get(field));
        }catch(IllegalArgumentException bad){throw new IOException("DIAGNOSTIC_CYCLE_INVALID",bad);}
        return cycle;
    }
    private static Map<String,PublicKey> trust(Path root,Map<String,String> cycle)throws IOException{
        PublicKey key=key(PreparationFiles.read(root.resolve("public.der"),4096));
        if(!PreparationFiles.digest(key.getEncoded()).equals(cycle.get("signer")))throw new IOException("DIAGNOSTIC_PINNED_KEY_CHANGED");
        return Map.of(cycle.get("signer"),key);
    }
    private static PublicKey key(byte[] bytes)throws IOException{
        try{return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes));}
        catch(GeneralSecurityException bad){throw new IOException("DIAGNOSTIC_ED25519_PUBLIC_KEY",bad);}
    }
    private static PackageModel.Target target(Map<String,String> cycle,boolean previous){return new PackageModel.Target(UUID.fromString(cycle.get("distribution")),PackageModel.Side.SERVER,cycle.get("minecraft"),cycle.get(previous?"previousLoader":"loader"),1);}
    private static String hash(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("HASH_REQUIRED");return value;}
    private static void requirePin(String pin)throws IOException{try{hash(pin);}catch(RuntimeException bad){throw new IOException("DIAGNOSTIC_EXPECTED_CYCLE_SHA256_REQUIRED",bad);}}
    private static String digestCycle(Path root)throws IOException{return PreparationFiles.hash(root.resolve("cycle.bin"),PreparationFiles.MAX_RECORD);}
    private static Map<String,Object> base(Map<String,String> cycle,String pin){
        var out=new LinkedHashMap<String,Object>();out.put("report","diagnostic_server_update_v53");out.put("diagnosticOnly",true);out.put("cycleSha256",pin);out.put("profile",cycle.get("profile"));out.put("profileId",cycle.get("profileId"));
        for(String field:List.of("distribution","minecraft","loader","previousArchive","nextArchive","intentSha256"))out.put(field,cycle.get(field));
        out.put("readOnly",true);out.put("diagnosticStorageModified",false);out.put("realGameFilesChanged",false);out.put("installedReceiptVerified",false);out.put("diagnosticFilesVerified",false);
        for(String field:List.of("serverStopped","nativeContainmentVerified","authorizationVerified","runtimeCompatibilityVerified","installAuthority","productionLaunchAllowed","minecraftStarted"))out.put(field,false);
        return out;
    }
    private static void publish(Path root,Map<String,Object> report)throws IOException{
        var record=Map.of("schema","53","cycleSha256",report.get("cycleSha256").toString(),"outcome",report.get("outcome").toString());
        Path result=root.resolve("result.bin");byte[] bytes=PreparationCodec.encode(record);
        if(Files.exists(result,LinkOption.NOFOLLOW_LINKS)&&!Arrays.equals(bytes,PreparationFiles.read(result,PreparationFiles.MAX_RECORD)))throw new IOException("DIAGNOSTIC_LATE_RESULT_EDIT_PRESERVED");
        if(!Files.exists(result,LinkOption.NOFOLLOW_LINKS))PreparationFiles.write(result,bytes);
    }
    private static CycleLease lease(Path root,boolean create)throws IOException{return new CycleLease(root,create);}
    private static final class CycleLease implements AutoCloseable{
        private final FileChannel channel;private final FileLock lock;
        CycleLease(Path root,boolean create)throws IOException{
            Path file=root.resolve("cycle.lock");PreparationFiles.safe(file);
            if(!create){PreparationFiles.existing(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("DIAGNOSTIC_LOCK_NOT_REGULAR");}
            channel=FileChannel.open(file,create?Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS):Set.of(StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS));
            FileLock acquired=null;
            try{if(create)PreparationFiles.privateMode(file,false);acquired=channel.tryLock();if(acquired==null)throw new IOException("DIAGNOSTIC_CYCLE_ALREADY_OWNED");}
            catch(IOException|RuntimeException bad){channel.close();if(bad instanceof OverlappingFileLockException)throw new IOException("DIAGNOSTIC_CYCLE_ALREADY_OWNED",bad);throw bad;}
            lock=acquired;
        }
        public void close()throws IOException{try{lock.release();}finally{channel.close();}}
    }
    private static Map<String,String> expected(Map<String,String> before,PackageStaging.Prepared old,PackageStaging.Prepared next){
        var names=new TreeMap<String,String[]>();int count=Integer.parseInt(before.get("count"));
        // Retain unrelated entries; remove only names actually owned by the verified baseline receipt.
        for(int i=0;i<count;i++){String p="file."+i+".";names.put(before.get(p+"name"),new String[]{before.get(p+"bytes"),before.get(p+"sha256")});}
        for(var entry:old.verified().manifest().forSide(PackageModel.Side.SERVER))names.remove(Path.of(entry.path()).getFileName().toString());
        for(var entry:next.verified().manifest().forSide(PackageModel.Side.SERVER))names.put(Path.of(entry.path()).getFileName().toString(),new String[]{Long.toString(entry.bytes()),entry.sha256()});
        var result=new TreeMap<String,String>();result.put("profileIdentity",before.get("profileIdentity"));result.put("modsIdentity",before.get("modsIdentity"));
        int index=0;long bytes=0;for(var entry:names.entrySet()){String p="file."+(index++)+".";result.put(p+"name",entry.getKey());result.put(p+"bytes",entry.getValue()[0]);result.put(p+"sha256",entry.getValue()[1]);bytes+=Long.parseLong(entry.getValue()[0]);}
        result.put("count",Integer.toString(index));result.put("bytes",Long.toString(bytes));return result;
    }
}
