package com.fixmer.mared.technology.scripts;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.dev.DevPaths;
import com.fixmer.mared.dev.client.*;
import com.fixmer.mared.technology.minecraft.NativeModWorkflow;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** One client-owned background pipeline. All game access and state transitions run on the client thread. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class BackgroundAutoApply {
    private BackgroundAutoApply() { }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"mared-background-build-io");t.setDaemon(true);return t;});
    private static final GradleBuildRunner BUILDS=new GradleBuildRunner();
    private static final ProjectChangeWatcher WATCH=new ProjectChangeWatcher();
    private static final Set<Object> EDITORS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Deque<String> LOG=new ArrayDeque<>();
    private record DirtyProject(String root,String id){}
    private static final Set<DirtyProject> CLOSED_DRAFTS=new HashSet<>();
    private static AutoBuildJournal.State settings=AutoBuildJournal.State.off();
    private static boolean initialized,loading,controlPending,ioBusy,runActive,closed,watchSeeded,recoveryFailed,settingsFailed;
    private static long epoch,buildEpoch;
    private static String phase="LOADING",detail="",staged="",runProject="",runRoot="",runHash="";
    private static AtomicBoolean cancellation=new AtomicBoolean();
    private static final AtomicBoolean RESTART_ACCEPTED=new AtomicBoolean();
    private static NativeWorldTarget target;
    private static SourceProjects runStorage;
    private static Path directory;
    private record Prepared(SourceProjects storage,Path directory,BuildTools.Settings tools,GradleBuildRunner.Options options) { }
    private static AutoBuildJournal journal(){return new AutoBuildJournal(DevPaths.file("automation.properties"));}
    private static String tr(String key){return MaredLang.get("mared.scripts."+key);}
    private static void status(String next,String text) {
        String bounded=text==null?"":text.replace('\r',' ').replace('\n',' ');
        bounded=bounded.substring(0,Math.min(1024,bounded.length()));
        if(phase.equals(next)&&detail.equals(bounded))return;
        phase=next;detail=bounded;append(tr("background.phase."+next)+(bounded.isEmpty()?"":" · "+bounded));
    }
    private static void append(String line) {
        line=line.substring(0,Math.min(2048,line.length()));
        LOG.addLast(java.time.LocalTime.now().withNano(0)+" "+line);
        while(LOG.size()>128)LOG.removeFirst();
        Mared.LOGGER.info("[Background build] {}",line);
    }
    private static void appendOutput(String output,int limit) {
        if(output==null||output.isEmpty())return;
        String[] lines=output.split("\\R");
        for(int i=Math.max(0,lines.length-limit);i<lines.length;i++)if(!lines[i].isBlank())append(lines[i]);
    }
    public static boolean busy(){return runActive||ioBusy||BUILDS.busy();}
    public static boolean selected(Path root,String id){return settings.enabled()&&settings.sameProject(root.toAbsolutePath().normalize().toString(),id);}
    public static String describe(){return tr("background.phase."+phase)+" · "+settings.project()+(detail.isEmpty()?"":" · "+detail);}
    public static String report(){return describe()+"\n"+settings.root()+"\n"+tr("background.last_result")+": "+tr("background.result."+settings.outcome())+"\n"+settings.detail()+"\n\n"+String.join("\n",LOG);}
    public static String chatReport(){return describe()+"\n"+String.join("\n",LOG.stream().skip(Math.max(0,LOG.size()-12)).toList());}
    public static void editorOpened(Object editor){EDITORS.add(editor);cancelRun("EDITOR");}
    /** Keep the editor lease until its final recovery/workspace write has actually completed. */
    public static void editorClosed(Object editor,CompletableFuture<Void> checkpoint) {
        editorClosed(editor,checkpoint,null,Set.of(),Set.of());
    }
    public static void editorClosed(Object editor,CompletableFuture<Void> checkpoint,Path root,Set<String> known,Set<String> dirty) {
        if(root!=null){String path=root.toAbsolutePath().normalize().toString();
            for(String id:known)CLOSED_DRAFTS.remove(new DirtyProject(path,id));
            for(String id:dirty)CLOSED_DRAFTS.add(new DirtyProject(path,id));
        }
        checkpoint.whenComplete((ignored,error)->Minecraft.getInstance().execute(()->{
            EDITORS.remove(editor);
            if(error!=null){recoveryFailed=true;status("FAILED",tr("background.checkpoint_failed"));control(s->s.pause(true).note(tr("background.checkpoint_failed")));}
        }));
    }
    public static CompletableFuture<Boolean> select(SourceProjects storage,String id,boolean enabled) {
        if(!SourceProjects.validProject(id))return CompletableFuture.completedFuture(false);
        return control(s->s.select(storage.root(),id,enabled)).thenApply(ok->{if(ok)recoveryFailed=false;return ok;});
    }
    /** A manual application is also an attempt; do not apply identical inputs again after its restart. */
    public static void acknowledgeManual(SourceProjects storage,String id,String hash) {
        if(hash==null||!hash.matches("[0-9a-f]{64}"))return;
        try{CompletableFuture.supplyAsync(()->{
            try{return journal().update(s->s.sameProject(storage.root().toString(),id)&&s.enabled()?s.result(hash,"RESTARTING",""):s);}
            catch(Exception e){throw new CompletionException(e);}
        },IO).whenComplete((s,error)->Minecraft.getInstance().execute(()->{if(error==null)settings=s;}));}
        catch(RejectedExecutionException ignored){ }
    }
    public static CompletableFuture<Boolean> pause(){return control(s->s.pause(true));}
    public static CompletableFuture<Boolean> resume(){return control(s->s.pause(false)).thenApply(ok->{if(ok){recoveryFailed=false;CLOSED_DRAFTS.remove(new DirtyProject(settings.root(),settings.project()));}return ok;});}
    public static CompletableFuture<Boolean> retry(){return control(AutoBuildJournal.State::retryOnce);}
    public static CompletableFuture<Boolean> stop(){return control(AutoBuildJournal.State::stop);}
    private static CompletableFuture<Boolean> control(UnaryOperator<AutoBuildJournal.State> change) {
        if(closed||controlPending)return CompletableFuture.completedFuture(false);
        boolean restartAccepted=RESTART_ACCEPTED.get();
        cancelRun("PAUSED");controlPending=true;
        var result=new CompletableFuture<Boolean>();
        try {
            CompletableFuture.supplyAsync(()->{
                try{return journal().update(change);}catch(Exception e){throw new CompletionException(e);}
            },IO).whenComplete((updated,error)->Minecraft.getInstance().execute(()->{
                controlPending=false;
                if(error!=null){status("FAILED",message(error));result.complete(false);return;}
                settings=updated;initialized=true;settingsFailed=false;WATCH.reset();watchSeeded=false;
                if(!restartAccepted)status(settings.enabled()?(settings.paused()?"PAUSED":"WATCHING"):"OFF",settings.detail());
                result.complete(true);
            }));
        }catch(RejectedExecutionException e){controlPending=false;result.complete(false);}
        return result;
    }
    private static String message(Throwable error) {
        while(error.getCause()!=null)error=error.getCause();
        return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
    }
    private static void cancelRun(String reason) {
        // Once the restart was accepted by the supervisor it cannot be withdrawn from this JVM.
        if(RESTART_ACCEPTED.get())return;
        boolean interrupted=runActive;
        String root=runRoot,id=runProject,hash=runHash;
        epoch++;cancellation.set(true);BUILDS.stop();runActive=false;WATCH.reset();watchSeeded=false;
        if(interrupted)try{IO.execute(()->{
            try{var updated=journal().update(s->s.sameProject(root,id)&&s.hash().equals(hash)?s.result(hash,"CANCELLED",reason).retryOnce().pause(s.paused()):s);
                Minecraft.getInstance().execute(()->settings=updated);}
            catch(Exception e){Mared.LOGGER.warn("[Background build] Could not retain cancelled attempt",e);}
        });}catch(RejectedExecutionException ignored){ }
        String token=staged;staged="";
        if(!token.isEmpty())try{IO.execute(()->NativeModWorkflow.cancel(token));}catch(RejectedExecutionException ignored){ }
        if(initialized&&settings.enabled())status(reason,"");
    }
    private static void notifyPlayer(String key) {
        var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.literal(tr(key)),false);
    }
    private static void persistResult(String outcome,String text) {
        String root=runRoot,id=runProject,hash=runHash;
        try {
            CompletableFuture.supplyAsync(()->{
                try{return journal().update(s->s.sameProject(root,id)&&s.hash().equals(hash)?s.result(hash,outcome,text):s);}
                catch(Exception e){throw new CompletionException(e);}
            },IO).whenComplete((s,error)->Minecraft.getInstance().execute(()->{
                if(error!=null){status("FAILED",message(error));return;}
                settings=s;
            }));
        }catch(RejectedExecutionException ignored){ }
    }
    private static void fail(String text) {
        runActive=false;status("FAILED",text);persistResult("FAILED",text);notifyPlayer("background.failed");
    }
    private static void load() {
        loading=true;
        CompletableFuture.supplyAsync(()->{
            try{return journal().read();}catch(Exception e){throw new CompletionException(e);}
        },IO).whenComplete((s,error)->Minecraft.getInstance().execute(()->{
            loading=false;initialized=true;
            if(controlPending)return;
            if(error!=null){settingsFailed=true;status("FAILED",message(error));return;}
            settings=s;status(s.enabled()?(s.paused()?"PAUSED":"WATCHING"):"OFF",s.outcome().equals("BUILDING")?tr("background.interrupted"):s.detail());
        }));
    }
    private static void begin(String hash) {
        long acceptedEpoch=epoch;
        runRoot=settings.root();runProject=settings.project();runHash=hash;
        String root=runRoot,id=runProject;
        target=NativeWorldTarget.capture();
        if(target==null)return;
        var expectedTarget=target;var stop=new AtomicBoolean();cancellation=stop;
        runActive=true;ioBusy=true;status("PREPARING",id);
        CompletableFuture.supplyAsync(()->{
            try {
                SourceProjects storage=new SourceProjects(Path.of(root));
                if(!storage.importedGradle(id)||!storage.hasGradle(id))throw new IOException("Background apply requires an imported Gradle mod project");
                for(String file:storage.files(id)){
                    if(stop.get())throw new IOException("Background preparation cancelled");
                    var recovery=storage.recovery(id,file);
                    if(recovery!=null&&!recovery.text().equals(recovery.baseline())&&!recovery.text().equals(storage.read(id,file)))
                        throw new DraftsPresent();
                }
                Path work=storage.workingDirectory(id);
                if(!hash.equals(ProjectChangeWatcher.capture(work,stop::get)))throw new ChangedInputs();
                var claim=journal().claim(root,id,hash);
                if(!claim.accepted())throw new DeclinedClaim(claim.state());
                storage.validateDependencies(id);
                return new Prepared(storage,work,storage.buildTools(id),storage.buildOptions(id));
            }catch(Exception e){throw new CompletionException(e);}
        },IO).whenComplete((prepared,error)->Minecraft.getInstance().execute(()->{
            ioBusy=false;
            if(closed||acceptedEpoch!=epoch){runActive=false;return;}
            if(error!=null){
                Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
                if(cause instanceof DraftsPresent){status("DRAFTS",tr("background.drafts"));runActive=false;control(s->s.pause(true).note(tr("background.drafts")));}
                else if(cause instanceof ChangedInputs){runActive=false;WATCH.seed(Path.of(root),settings.hash());status("WATCHING",tr("background.inputs_changed"));}
                else if(cause instanceof DeclinedClaim declined){settings=declined.state;runActive=false;status("WATCHING","");WATCH.reset();watchSeeded=false;}
                else fail(message(error));
                return;
            }
            if(!expectedTarget.current()){cancelRun("WAIT_WORLD");control(s->s.pause(true));return;}
            if(!EDITORS.isEmpty()||stop.get()){cancelRun("EDITOR");return;}
            settings=settings.result(hash,"BUILDING","");
            runStorage=prepared.storage();directory=prepared.directory();buildEpoch=acceptedEpoch;
            if(!BUILDS.start(directory,new GradleBuildRunner.Settings(prepared.tools().javaHome(),prepared.tools().wrapperJar()),"build",prepared.options())){
                fail(tr("operation_busy"));return;
            }
            status("BUILDING",id);notifyPlayer("background.started");
        }));
    }
    private static void built(GradleBuildRunner.Result result) {
        if(!runActive||buildEpoch!=epoch||closed)return;
        if(target==null||!target.current()||!EDITORS.isEmpty()){cancelRun("WAIT_WORLD");return;}
        appendOutput(result.output(),64);
        if(result.status()!=GradleBuildRunner.Status.SUCCESS){fail(result.status()+" · "+result.exitCode());return;}
        var jars=result.inventory().artifacts().stream().filter(a->a.kind().equals("mod")||a.kind().equals("mod_embedded")).toList();
        if(jars.size()!=1){fail(tr("background.choose_artifact"));return;}
        long acceptedEpoch=epoch;var stop=cancellation;var expectedTarget=target;
        SourceProjects storage=runStorage;String id=runProject,hash=runHash;Path work=directory;
        Map<String,String> versions=NativeModWorkflow.loadedVersions();
        ioBusy=true;status("STAGING",id);
        CompletableFuture.supplyAsync(()->{
            String token=null;
            try {
                if(!hash.equals(ProjectChangeWatcher.capture(work,stop::get)))throw new ChangedInputs();
                token=NativeModWorkflow.stage(storage,id,jars.get(0),versions,stop::get);
                if(stop.get()){NativeModWorkflow.cancel(token);throw new IOException("Background staging cancelled");}
                return token;
            }catch(Exception e){NativeModWorkflow.cancel(token);throw new CompletionException(e);}
        },IO).whenComplete((token,error)->Minecraft.getInstance().execute(()->{
            ioBusy=false;
            if(closed||acceptedEpoch!=epoch||stop.get()||!expectedTarget.current()||!EDITORS.isEmpty()){
                if(token!=null)try{IO.execute(()->NativeModWorkflow.cancel(token));}catch(RejectedExecutionException ignored){ }
                if(!closed&&acceptedEpoch==epoch&&!stop.get()&&!expectedTarget.current()){cancelRun("WAIT_WORLD");control(s->s.pause(true));}
                else runActive=false;
                return;
            }
            if(error!=null){
                Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
                if(cause instanceof ChangedInputs){runActive=false;status("WATCHING",tr("background.inputs_changed"));persistResult("STALE",tr("background.inputs_changed"));}
                else fail(message(error));return;
            }
            staged=token;status("CHECKPOINT",id);
            DevAutoRestart.afterSuccessfulApply(id,()->!closed&&!stop.get()&&acceptedEpoch==epoch&&expectedTarget.current()&&EDITORS.isEmpty())
                .whenComplete((ok,failure)->{
                    // Record acceptance before queueing a client callback: normal shutdown may drain no more ticks.
                    if(failure==null&&Boolean.TRUE.equals(ok))RESTART_ACCEPTED.set(true);
                    Minecraft.getInstance().execute(()->{
                    if(closed||acceptedEpoch!=epoch||stop.get()){runActive=false;return;}
                    if(failure!=null||!Boolean.TRUE.equals(ok)){
                        String rejected=staged;staged="";
                        try{IO.execute(()->NativeModWorkflow.cancel(rejected));}catch(RejectedExecutionException ignored){ }
                        fail(failure==null?DevActivationService.lastStatus:message(failure));
                    }else{status("RESTARTING",id);persistResult("RESTARTING","");}
                });});
        }));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { tickAt(System.nanoTime()); }
    static void tickAt(long now) {
        if(closed)return;
        appendOutput(BUILDS.drainOutput(),64);
        BUILDS.poll(BackgroundAutoApply::built);
        if(!initialized){if(!loading&&!controlPending)load();return;}
        if(controlPending||loading||settingsFailed)return;
        if(settings.enabled()&&CLOSED_DRAFTS.contains(new DirtyProject(settings.root(),settings.project()))){recoveryFailed=true;if(!settings.paused()&&!controlPending)control(s->s.pause(true).note(tr("background.drafts")));return;}
        if(recoveryFailed){if(!controlPending&&!settings.paused())control(s->s.pause(true));return;}
        if(runActive&&!RESTART_ACCEPTED.get()&&target!=null&&!target.current()){cancelRun("WAIT_WORLD");control(s->s.pause(true));return;}
        boolean allowed=EDITORS.isEmpty()&&NativeWorldTarget.capture()!=null&&!DevRestartService.isRestartInProgress()&&!DevActivationService.isActivating();
        if(!allowed){if(runActive&&!RESTART_ACCEPTED.get())cancelRun(EDITORS.isEmpty()?"WAIT_WORLD":"EDITOR");
            if(!busy()&&settings.enabled()&&!settings.paused())status(EDITORS.isEmpty()?"WAIT_WORLD":"EDITOR","");return;}
        if(!settings.enabled()){if(!busy())status("OFF","");return;}
        if(settings.paused()){if(!busy())status("PAUSED","");return;}
        if(DevModTrial.isPending()){if(!busy())status("WAIT_TRIAL","");return;}
        if(busy())return;
        if(settings.root().isBlank()||!SourceProjects.validProject(settings.project())){status("FAILED","Invalid automation project");return;}
        try {
            Path work;
            // Resolving Link metadata and source IO happens in begin()/the scan worker, never here.
            // directory is resolved asynchronously once for every watch session.
            if(!watchSeeded){
                watchSeeded=true;ioBusy=true;long captured=epoch;String root=settings.root(),id=settings.project();
                CompletableFuture.supplyAsync(()->{
                    try{SourceProjects s=new SourceProjects(Path.of(root));if(!s.importedGradle(id))throw new IOException("Not an imported Gradle project");return s.workingDirectory(id);}
                    catch(Exception e){throw new CompletionException(e);}
                },IO).whenComplete((path,error)->Minecraft.getInstance().execute(()->{
                    ioBusy=false;if(captured!=epoch||closed)return;
                    if(error!=null){status("FAILED",message(error));control(s->s.pause(true));return;}
                    directory=path;WATCH.seed(path,settings.retry()?"0".repeat(64):settings.hash());
                    status("WATCHING","");
                }));return;
            }
            work=directory;
            if(WATCH.poll(work,now))begin(WATCH.fingerprint());
            else if(settings.hash().isEmpty()&&!WATCH.fingerprint().isEmpty()){
                controlPending=true;String hash=WATCH.fingerprint(),root=settings.root(),id=settings.project();
                CompletableFuture.supplyAsync(()->{
                    try{return journal().update(s->s.sameProject(root,id)&&s.hash().isEmpty()?s.result(hash,"BASELINE",""):s);}
                    catch(Exception e){throw new CompletionException(e);}
                },IO).whenComplete((s,error)->Minecraft.getInstance().execute(()->{
                    controlPending=false;if(error!=null)status("FAILED",message(error));else settings=s;
                }));
            }
        }catch(Exception error){status("FAILED",message(error));control(s->s.pause(true));}
    }
    @SubscribeEvent public static void shutdown(GameShuttingDownEvent event) {
        closed=true;epoch++;cancellation.set(true);WATCH.close();BUILDS.close();
        // For an accepted restart keep its pending plan. Every other staged plan is cancelled.
        if(!RESTART_ACCEPTED.get()){String token=staged;staged="";try{IO.execute(()->NativeModWorkflow.cancel(token));}catch(RejectedExecutionException ignored){ }}
        IO.shutdown();
        try{if(!IO.awaitTermination(3,TimeUnit.SECONDS))Mared.LOGGER.warn("[Background build] Cleanup is still running");}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private static final class DraftsPresent extends IOException { }
    private static final class ChangedInputs extends IOException { }
    private static final class DeclinedClaim extends IOException {
        final AutoBuildJournal.State state;DeclinedClaim(AutoBuildJournal.State state){this.state=state;}
    }
}
