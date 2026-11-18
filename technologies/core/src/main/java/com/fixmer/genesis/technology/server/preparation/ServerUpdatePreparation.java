package com.fixmer.genesis.technology.server.preparation;
import com.fixmer.genesis.technology.server.packages.*;
import com.fixmer.genesis.technology.server.update.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import static com.fixmer.genesis.technology.server.update.UpdateProtocol.*;
/** Signed server-package preparation, durable control and revalidation. No stop/install/launch API. */
public final class ServerUpdatePreparation {
    private static final Set<String> INTENT=Set.of("schema","scope","root","profile","cache","next","previous","publicKeySha256","signer","nextSha256","previousSha256","nextLoader","previousLoader","minecraft","distribution","update","baselineSha256");
    private static final Set<String> SEAL=Set.of("schema","scope","intentSha256","planSha256","update","packageSha256","recordedPhase");
    public record Request(Path profile,Path cache,Path next,Path previous,Path publicKey,UUID distribution,String minecraft,String nextLoader,String previousLoader){
        public Request{
            Objects.requireNonNull(profile);
            Objects.requireNonNull(cache);
            Objects.requireNonNull(next);
            Objects.requireNonNull(publicKey);
            new PackageModel.Target(distribution,PackageModel.Side.SERVER,minecraft,nextLoader,PackageModel.PROTOCOL);
            if(previous!=null)new PackageModel.Target(distribution,PackageModel.Side.SERVER,minecraft,previousLoader,PackageModel.PROTOCOL);
            else if(previousLoader!=null&&!previousLoader.isEmpty())throw new IllegalArgumentException("PREVIOUS_LOADER_WITHOUT_PACKAGE");
        }
    }
    private ServerUpdatePreparation(){
    }
    private static PackageModel.Target target(Map<String,String> intent,boolean previous){
        return new PackageModel.Target(UUID.fromString(intent.get("distribution")),PackageModel.Side.SERVER,intent.get("minecraft"),intent.get(previous?"previousLoader":"nextLoader"),PackageModel.PROTOCOL);
    }
    public static Map<String,Object> prepare(Request request,Path root)throws IOException {
        Path profile=PreparationFiles.existing(request.profile()),cache=request.cache().toAbsolutePath().normalize();
        root=root.toAbsolutePath().normalize();
        PreparationFiles.safe(cache);
        PreparationFiles.safe(root);
        PreparationFiles.disjoint(root,profile);
        PreparationFiles.disjoint(cache,profile);
        PreparationFiles.disjoint(root,cache);
        PreparationFiles.existing(cache.getParent());
        if(Files.exists(cache,LinkOption.NOFOLLOW_LINKS))PreparationFiles.existing(cache);
        PreparationFiles.existing(root.getParent());
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("PREPARATION_NEW_DIRECTORY_REQUIRED");
        byte[] keyBytes=PreparationFiles.read(request.publicKey(),4096);
        PublicKey key=key(keyBytes);
        var trust=Map.of(PreparationFiles.digest(key.getEncoded()),key);
        var nextTarget=new PackageModel.Target(request.distribution(),PackageModel.Side.SERVER,request.minecraft(),request.nextLoader(),PackageModel.PROTOCOL);
        Path source=PreparationFiles.existing(request.next());
        var next=PackageArchive.verify(source,trust,nextTarget);
        Path previous=request.previous()==null?null:PreparationFiles.existing(request.previous());
        PackageArchive.Verified old=previous==null?null:PackageArchive.verify(previous,trust,new PackageModel.Target(request.distribution(),PackageModel.Side.SERVER,request.minecraft(),request.previousLoader(),PackageModel.PROTOCOL));
        if(old==null&&next.manifest().parent()!=null)throw new IOException("PREPARATION_SIGNED_BASE_REQUIRED");
        PackagePlanner.plan(old,next,nextTarget,profile);
        byte[] baseline=PreparationCodec.encode(PreparationFiles.inventory(profile));
        var intent=new TreeMap<String,String>();
        intent.put("schema","52");
        intent.put("scope","SERVER_PACKAGE_PREPARATION_ONLY");
        intent.put("root",root.toString());
        intent.put("profile",profile.toString());
        intent.put("cache",cache.toString());
        intent.put("next",source.toString());
        intent.put("previous",previous==null?"":previous.toString());
        intent.put("publicKeySha256",PreparationFiles.digest(keyBytes));
        intent.put("signer",PreparationFiles.digest(key.getEncoded()));
        intent.put("nextSha256",next.archiveSha256());
        intent.put("previousSha256",old==null?"":old.archiveSha256());
        intent.put("nextLoader",request.nextLoader());
        intent.put("previousLoader",old==null?"":request.previousLoader());
        intent.put("minecraft",request.minecraft());
        intent.put("distribution",request.distribution().toString());
        intent.put("update",UUID.randomUUID().toString());
        intent.put("baselineSha256",PreparationFiles.digest(baseline));
        PreparationFiles.newDirectory(root);
        PreparationFiles.write(root.resolve("public.der"),keyBytes);
        PreparationFiles.write(root.resolve("baseline.bin"),baseline);
        PreparationFiles.write(root.resolve("intent.bin"),PreparationCodec.encode(intent));
        try(var journal=UpdateJournal.create(root.resolve("control"))){
            controlStarted(root,intent);
            begin(journal,intent);
            /* VERIFY52_BEGIN */complete(root,intent,journal);
        }
        return changed(recheck(root,PreparationFiles.digest(PreparationCodec.encode(intent))));
    }
    public static Map<String,Object> resume(Path root,String expectedIntentSha256)throws IOException {
        requirePin(expectedIntentSha256);
        root=PreparationFiles.existing(root);
        var intent=readIntent(root,expectedIntentSha256);
        Path control=root.resolve("control");
        if(!Files.exists(control,LinkOption.NOFOLLOW_LINKS)&&Files.exists(root.resolve("control.started"),LinkOption.NOFOLLOW_LINKS))throw new IOException("PREPARATION_CONTROL_MISSING_DO_NOT_RECREATE");
        if(Files.exists(control,LinkOption.NOFOLLOW_LINKS)){
            PreparationFiles.existing(control);
            var observed=UpdateJournal.inspect(control);
            bound(observed.state(),intent);
            if(Set.of(Phase.FAILED,Phase.ABORTED).contains(observed.state().phase()))throw new IOException("PREPARATION_TERMINAL_USE_NEW_DIRECTORY");
            if(observed.state().phase()==Phase.PREPARED&&observed.unfinishedTailBytes()==0)return recheck(root,expectedIntentSha256);
        }
        try(var journal=Files.exists(control,LinkOption.NOFOLLOW_LINKS)?UpdateJournal.open(control):UpdateJournal.create(control)){
            controlStarted(root,intent);
            begin(journal,intent);
            if(journal.state().phase()==Phase.PREPARED){
                compute(root,intent,false);
                sealed(root,intent);
            }
            else complete(root,intent,journal);
        }
        return changed(recheck(root,expectedIntentSha256));
    }
    /** Historical only: recorded PREPARED is never a claim about current game files. */
    public static Map<String,Object> status(Path root)throws IOException {
        root=PreparationFiles.existing(root);
        var intent=readIntent(root,null);
        byte[] before=PreparationFiles.read(root.resolve("intent.bin"),PreparationFiles.MAX_RECORD);
        if(!Files.exists(root.resolve("control"),LinkOption.NOFOLLOW_LINKS)){
            var initial=base(intent);
            initial.put("historical",true);
            initial.put("recordedPhase",Files.exists(root.resolve("control.started"),LinkOption.NOFOLLOW_LINKS)?"UNKNOWN_MISSING_CONTROL":"INITIALIZING");
            initial.put("recordedPrepared",false);
            initial.put("readyForReview",false);
            if(!intent.equals(readIntent(root,null)))throw new IOException("PREPARATION_HISTORY_CHANGED_DURING_READ");
            return initial;
        }
        PreparationFiles.existing(root.resolve("control"));
        var inspection=UpdateJournal.inspect(root.resolve("control"));
        bound(inspection.state(),intent);
        var out=base(intent);
        out.put("historical",true);
        out.put("recordedPhase",inspection.state().phase());
        out.put("controlEpoch",inspection.state().epoch());
        out.put("controlRevision",inspection.state().revision());
        out.put("unfinishedTailBytes",inspection.unfinishedTailBytes());
        out.put("recordedPrepared",false);
        out.put("readyForReview",false);
        if(inspection.unfinishedTailBytes()==0&&Set.of(Phase.PREPARED,Phase.FAILED).contains(inspection.state().phase())){
            var plan=sealed(root,intent);
            out.put("recordedPrepared",inspection.state().phase()==Phase.PREPARED);
            summary(out,plan);
        }
        if(!Arrays.equals(before,PreparationFiles.read(root.resolve("intent.bin"),PreparationFiles.MAX_RECORD))||!inspection.equals(UpdateJournal.inspect(root.resolve("control"))))throw new IOException("PREPARATION_HISTORY_CHANGED_DURING_READ");
        return out;
    }
    /** Read-only full archive/signature/CAS/inventory/plan validation; still no execution authority. */
    public static Map<String,Object> recheck(Path root,String expectedIntentSha256)throws IOException {
        requirePin(expectedIntentSha256);
        root=PreparationFiles.existing(root);
        var intent=readIntent(root,expectedIntentSha256);
        PreparationFiles.existing(root.resolve("control"));
        var before=UpdateJournal.inspect(root.resolve("control"));
        bound(before.state(),intent);
        if(before.unfinishedTailBytes()!=0)throw new IOException("PREPARATION_UNFINISHED_JOURNAL_RESUME_REQUIRED");
        if(!Set.of(Phase.PREPARED,Phase.FAILED,Phase.ABORTED).contains(before.state().phase()))throw new IOException("PREPARATION_NOT_COMPLETED_RESUME_REQUIRED");
        var recorded=Files.exists(root.resolve("seal.bin"),LinkOption.NOFOLLOW_LINKS)?sealed(root,intent):null;
        var out=base(intent);
        out.put("historical",false);
        out.put("recordedPhase",before.state().phase());
        out.put("controlEpoch",before.state().epoch());
        out.put("controlRevision",before.state().revision());
        out.put("recordedPrepared",before.state().phase()==Phase.PREPARED);
        out.put("readyForReview",false);
        if(before.state().phase()==Phase.PREPARED){
            requireControlStarted(root,intent);
            var current=compute(root,intent,false);
            if(!current.equals(recorded))throw new IOException("PREPARATION_INPUTS_CHANGED_USE_NEW_DIRECTORY");
            if(!current.get("conflicts").equals("0"))throw new IOException("PREPARATION_FALSE_READY");
            out.put("readyForReview",true);
            out.put("inputsChecked",true);
            summary(out,current);
        }
        else if(recorded!=null)summary(out,recorded);
        if(!before.equals(UpdateJournal.inspect(root.resolve("control")))||!intent.equals(readIntent(root,expectedIntentSha256))||recorded!=null&&!recorded.equals(sealed(root,intent)))throw new IOException("PREPARATION_CHANGED_DURING_RECHECK");
        return out;
    }
    public static Map<String,Object> cancel(Path root,String expectedIntentSha256,boolean confirmed)throws IOException {
        if(!confirmed)throw new IOException("PREPARATION_CANCEL_CONFIRMATION_REQUIRED");
        requirePin(expectedIntentSha256);
        root=PreparationFiles.existing(root);
        var intent=readIntent(root,expectedIntentSha256);
        PreparationFiles.existing(root.resolve("control"));
        var state=UpdateJournal.inspect(root.resolve("control")).state();
        bound(state,intent);
        if(Set.of(Phase.FAILED,Phase.ABORTED).contains(state.phase()))return status(root);
        try(var journal=UpdateJournal.open(root.resolve("control"))){
            bound(journal.state(),intent);
            if(!Set.of(Phase.PREPARING,Phase.PREPARED).contains(journal.state().phase()))throw new IOException("PREPARATION_NOT_CANCELLABLE");
            command(journal,intent,Action.ABORT);
        }
        return changed(status(root));
    }
    private static void begin(UpdateJournal journal,Map<String,String> intent)throws IOException {
        if(journal.state().phase()==Phase.IDLE)command(journal,intent,Action.BEGIN);
        bound(journal.state(),intent);
        if(!Set.of(Phase.PREPARING,Phase.PREPARED).contains(journal.state().phase()))throw new IOException("PREPARATION_CONTROL_PHASE");
    }
    private static void complete(Path root,Map<String,String> intent,UpdateJournal journal)throws IOException {
        var plan=compute(root,intent,true);
        immutable(root.resolve("plan.bin"),PreparationCodec.encode(plan));
        var seal=new TreeMap<String,String>();
        seal.put("schema","52");
        seal.put("scope","SERVER_PACKAGE_PREPARATION_ONLY");
        seal.put("intentSha256",PreparationFiles.digest(PreparationCodec.encode(intent)));
        seal.put("planSha256",PreparationFiles.digest(PreparationCodec.encode(plan)));
        seal.put("update",intent.get("update"));
        seal.put("packageSha256",intent.get("nextSha256"));
        seal.put("recordedPhase",plan.get("conflicts").equals("0")?"PREPARED":"CONFLICT");
        immutable(root.resolve("seal.bin"),PreparationCodec.encode(seal));
        if(!intent.equals(readIntent(root,seal.get("intentSha256"))))throw new IOException("PREPARATION_INTENT_CHANGED");
        command(journal,intent,plan.get("conflicts").equals("0")?Action.PACKAGE_VERIFIED:Action.FAIL);
        /* VERIFY52_COMMITTED */
    }
    private static Map<String,String> compute(Path root,Map<String,String> intent,boolean staging)throws IOException {
        if(!intent.equals(readIntent(root,PreparationFiles.digest(PreparationCodec.encode(intent)))))throw new IOException("PREPARATION_INTENT_CHANGED");
        Path profile=Path.of(intent.get("profile")),cache=Path.of(intent.get("cache"));
        var trust=trust(root,intent);
        byte[] baseline=PreparationFiles.read(root.resolve("baseline.bin"),PreparationFiles.MAX_RECORD);
        if(!PreparationFiles.digest(baseline).equals(intent.get("baselineSha256")))throw new IOException("PREPARATION_BASELINE_CHANGED");
        PreparationCodec.decode(baseline);
        var observed=PreparationFiles.inventory(profile);
        if(!Arrays.equals(baseline,PreparationCodec.encode(observed)))throw new IOException("PREPARATION_PROFILE_CHANGED");
        PackageStaging.Prepared old=intent.get("previousSha256").isEmpty()?null:staged(cache,intent.get("previous"),intent.get("previousSha256"),trust,target(intent,true),staging);
        /* VERIFY52_OLD_STAGED */var next=staged(cache,intent.get("next"),intent.get("nextSha256"),trust,target(intent,false),staging);
        /* VERIFY52_NEXT_STAGED */if(old==null&&next.verified().manifest().parent()!=null)throw new IOException("PREPARATION_SIGNED_BASE_REQUIRED");
        var plan=PackagePlanner.plan(old==null?null:old.verified(),next.verified(),target(intent,false),profile);
        var out=new TreeMap<String,String>();
        out.put("schema","52");
        out.put("scope","SERVER_PACKAGE_PREPARATION_ONLY");
        out.put("intentSha256",PreparationFiles.digest(PreparationCodec.encode(intent)));
        out.put("baselineSha256",intent.get("baselineSha256"));
        out.put("packageSha256",next.verified().archiveSha256());
        out.put("manifestSha256",next.verified().manifestSha256());
        out.put("fromRelease",plan.fromRelease()==null?"":plan.fromRelease().toString());
        out.put("toRelease",plan.toRelease().toString());
        out.put("writes",Long.toString(plan.writesPlanned()));
        out.put("conflicts",Long.toString(plan.changes().stream().filter(c->c.action()==PackagePlanner.Action.CONFLICT).count()));
        out.put("count",Integer.toString(plan.changes().size()));
        out.put("observedFiles",observed.get("count"));
        out.put("observedBytes",observed.get("bytes"));
        int i=0;
        for(var c:plan.changes()){
            String prefix="change."+(i++)+".";
            out.put(prefix+"path",c.path());
            out.put(prefix+"action",c.action().name());
            out.put(prefix+"reason",c.reason());
            out.put(prefix+"before",c.expectedBefore()==null?"":c.expectedBefore());
            out.put(prefix+"after",c.expectedAfter()==null?"":c.expectedAfter());
            out.put(prefix+"observed",c.observedBefore()==null?"":c.observedBefore());
            out.put(prefix+"existed",Boolean.toString(c.existed()));
        }
        next.recheck();
        if(old!=null)old.recheck();
        if(!observed.equals(PreparationFiles.inventory(profile)))throw new IOException("PREPARATION_PROFILE_CHANGED_DURING_SCAN");
        return Collections.unmodifiableMap(out);
    }
    private static PackageStaging.Prepared staged(Path cache,String source,String hash,Map<String,PublicKey> trust,PackageModel.Target target,boolean staging)throws IOException {
        if(Files.exists(cache,LinkOption.NOFOLLOW_LINKS)){
            PreparationFiles.existing(cache);
            for(String name:List.of("objects","archives","format","cache.lock")){
                Path p=cache.resolve(name);
                if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))PreparationFiles.existing(p);
            }
        }
        else PreparationFiles.existing(cache.getParent());
        if(!staging)return PackageStaging.load(cache,hash,trust,target);
        Path stored=cache.resolve("archives").resolve(hash+".zip"),input=Files.exists(stored,LinkOption.NOFOLLOW_LINKS)?stored:Path.of(source);
        if(!PackageArchive.verify(input,trust,target).archiveSha256().equals(hash))throw new IOException("PREPARATION_SOURCE_ARCHIVE_CHANGED");
        return PackageStaging.prepare(cache,input,trust,target);
    }
    private static Map<String,String> sealed(Path root,Map<String,String> intent)throws IOException {
        var seal=PreparationCodec.decode(PreparationFiles.read(root.resolve("seal.bin"),PreparationFiles.MAX_RECORD));
        byte[] raw=PreparationFiles.read(root.resolve("plan.bin"),PreparationFiles.MAX_RECORD);
        var plan=PreparationCodec.decode(raw);
        if(!seal.keySet().equals(SEAL)||!seal.get("schema").equals("52")||!seal.get("scope").equals(intent.get("scope"))||!seal.get("intentSha256").equals(PreparationFiles.digest(PreparationCodec.encode(intent)))||!seal.get("planSha256").equals(PreparationFiles.digest(raw))||!seal.get("update").equals(intent.get("update"))||!seal.get("packageSha256").equals(intent.get("nextSha256"))||!plan.getOrDefault("intentSha256","").equals(seal.get("intentSha256"))||!plan.getOrDefault("schema","").equals("52")||!plan.getOrDefault("scope","").equals(intent.get("scope"))||!plan.getOrDefault("baselineSha256","").equals(intent.get("baselineSha256"))||!plan.getOrDefault("packageSha256","").equals(intent.get("nextSha256"))||!seal.get("recordedPhase").equals(plan.getOrDefault("conflicts","").equals("0")?"PREPARED":"CONFLICT"))throw new IOException("PREPARATION_SEAL_MISMATCH");
        return plan;
    }
    private static Map<String,String> readIntent(Path root,String expected)throws IOException {
        byte[] raw=PreparationFiles.read(root.resolve("intent.bin"),PreparationFiles.MAX_RECORD);
        if(expected!=null&&(!expected.matches("[0-9a-f]{64}")||!PreparationFiles.digest(raw).equals(expected)))throw new IOException("PREPARATION_EXPECTED_INTENT_HASH");
        var map=PreparationCodec.decode(raw);
        try{
            if(!map.keySet().equals(INTENT)||!map.get("schema").equals("52")||!map.get("scope").equals("SERVER_PACKAGE_PREPARATION_ONLY")||!map.get("root").equals(root.toString()))throw new IOException("PREPARATION_INTENT_SCHEMA_OR_ROOT");
            for(String field:List.of("publicKeySha256","signer","nextSha256","baselineSha256"))if(!map.get(field).matches("[0-9a-f]{64}"))throw new IOException("PREPARATION_INTENT_HASH");
            if(!map.get("previousSha256").isEmpty()&&!map.get("previousSha256").matches("[0-9a-f]{64}"))throw new IOException("PREPARATION_BASE_HASH");
            UUID id=UUID.fromString(map.get("update"));
            if(!id.toString().equals(map.get("update"))||id.equals(NONE))throw new IOException("PREPARATION_UPDATE_ID");
            target(map,false);
            if(!map.get("previousSha256").isEmpty())target(map,true);
            for(String field:List.of("root","profile","cache","next")){
                Path path=Path.of(map.get(field));
                if(!path.isAbsolute()||!path.normalize().equals(path))throw new IOException("PREPARATION_ABSOLUTE_PATH_REQUIRED");
            }
            if(map.get("previousSha256").isEmpty()!=map.get("previous").isEmpty()||map.get("previousSha256").isEmpty()!=map.get("previousLoader").isEmpty())throw new IOException("PREPARATION_BASE_FIELDS");
            if(!map.get("previous").isEmpty()){
                Path p=Path.of(map.get("previous"));
                if(!p.isAbsolute()||!p.normalize().equals(p))throw new IOException("PREPARATION_BASE_PATH");
            }
            if(Files.exists(root.resolve("control.started"),LinkOption.NOFOLLOW_LINKS))requireControlStarted(root,map);
            trust(root,map);
            byte[] baseline=PreparationFiles.read(root.resolve("baseline.bin"),PreparationFiles.MAX_RECORD);
            if(!PreparationFiles.digest(baseline).equals(map.get("baselineSha256")))throw new IOException("PREPARATION_BASELINE_CHANGED");
            PreparationCodec.decode(baseline);
            PreparationFiles.disjoint(Path.of(map.get("profile")),root);
            PreparationFiles.disjoint(Path.of(map.get("cache")),root);
            PreparationFiles.disjoint(Path.of(map.get("profile")),Path.of(map.get("cache")));
            return map;
        }
        catch(IllegalArgumentException bad){
            throw new IOException("PREPARATION_INTENT_INVALID",bad);
        }
    }
    private static Map<String,PublicKey> trust(Path root,Map<String,String> intent)throws IOException {
        byte[] bytes=PreparationFiles.read(root.resolve("public.der"),4096);
        PublicKey key=key(bytes);
        if(!PreparationFiles.digest(bytes).equals(intent.get("publicKeySha256"))||!PreparationFiles.digest(key.getEncoded()).equals(intent.get("signer")))throw new IOException("PREPARATION_PINNED_KEY_CHANGED");
        return Map.of(intent.get("signer"),key);
    }
    private static PublicKey key(byte[] data)throws IOException{
        try{
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(data));
        }
        catch(GeneralSecurityException bad){
            throw new IOException("PREPARATION_INVALID_PUBLIC_KEY",bad);
        }
    }
    private static void bound(State state,Map<String,String> intent)throws IOException{
        if(state.phase()!=Phase.IDLE&&(!state.updateId().toString().equals(intent.get("update"))||!state.packageSha256().equals(intent.get("nextSha256"))))throw new IOException("PREPARATION_CONTROL_BINDING");
    }
    private static void command(UpdateJournal journal,Map<String,String> intent,Action action)throws IOException {
        var state=journal.state();
        var result=journal.execute(new Command(UUID.randomUUID(),state.epoch(),state.revision(),UUID.fromString(intent.get("update")),action,action==Action.BEGIN?intent.get("nextSha256"):"",action==Action.FAIL?"PROFILE_CONFLICT":""));
        if(result.outcome()!=Outcome.APPLIED)throw new IOException("PREPARATION_CONTROL_REJECTED_"+result.rejection());
    }
    private static void immutable(Path path,byte[] data)throws IOException {
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
            if(!Arrays.equals(data,PreparationFiles.read(path,PreparationFiles.MAX_RECORD)))throw new IOException("PREPARATION_REVIEW_INPUTS_CHANGED_USE_NEW_DIRECTORY");
        }
        else PreparationFiles.write(path,data);
    }
    private static Map<String,Object> base(Map<String,String> intent)throws IOException {
        var out=new LinkedHashMap<String,Object>();
        out.put("report","server_update_preparation_v52");
        out.put("intentSha256",PreparationFiles.digest(PreparationCodec.encode(intent)));
        out.put("update",intent.get("update"));
        out.put("distribution",intent.get("distribution"));
        out.put("packageSha256",intent.get("nextSha256"));
        out.put("profile",intent.get("profile"));
        out.put("readOnly",true);
        out.put("inputsChecked",false);
        out.put("profileModified",false);
        out.put("serverStopped",false);
        out.put("gameLaunched",false);
        out.put("authorizationVerified",false);
        out.put("installedReceiptVerified",false);
        out.put("runtimeCompatibilityVerified",false);
        out.put("baselineOwnership","declared_signed_package");
        out.put("profileScope","flat_mods_inventory");
        out.put("installAuthority",false);
        out.put("productionLaunchAllowed",false);
        return out;
    }
    private static void controlStarted(Path root,Map<String,String> intent)throws IOException {
        immutable(root.resolve("control.started"),PreparationCodec.encode(Map.of("schema","52","intentSha256",PreparationFiles.digest(PreparationCodec.encode(intent)))));
    }
    private static void requireControlStarted(Path root,Map<String,String> intent)throws IOException {
        var marker=PreparationCodec.decode(PreparationFiles.read(root.resolve("control.started"),PreparationFiles.MAX_RECORD));
        if(!marker.equals(Map.of("schema","52","intentSha256",PreparationFiles.digest(PreparationCodec.encode(intent)))))throw new IOException("PREPARATION_CONTROL_START_MARKER_CHANGED");
    }
    private static void requirePin(String hash)throws IOException{
        if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IOException("PREPARATION_EXPECTED_INTENT_HASH_REQUIRED");
    }
    private static Map<String,Object> changed(Map<String,Object> out){
        out.put("readOnly",false);
        out.put("preparationStorageModified",true);
        return out;
    }
    private static void summary(Map<String,Object> out,Map<String,String> plan)throws IOException {
        try{
            int n=Integer.parseInt(plan.get("count"));
            if(n<0||n>PackageModel.MAX_FILES*2)throw new IOException("PREPARATION_PLAN_COUNT");
            out.put("writesPlanned",Long.parseLong(plan.get("writes")));
            out.put("conflicts",Long.parseLong(plan.get("conflicts")));
            out.put("observedFiles",Long.parseLong(plan.get("observedFiles")));
            out.put("observedBytes",Long.parseLong(plan.get("observedBytes")));
            out.put("fromRelease",plan.get("fromRelease"));
            out.put("toRelease",plan.get("toRelease"));
            var changes=new ArrayList<Map<String,String>>();
            for(int i=0;i<n;i++){
                var row=new LinkedHashMap<String,String>();
                for(String field:List.of("path","action","reason","before","after","observed","existed")){
                    String value=plan.get("change."+i+"."+field);
                    if(value==null)throw new IOException("PREPARATION_PLAN_FIELD");
                    row.put(field,value);
                }
                changes.add(row);
            }
            out.put("changes",changes);
        }
        catch(NumberFormatException|NullPointerException bad){
            throw new IOException("PREPARATION_PLAN_CORRUPT",bad);
        }
    }
}
