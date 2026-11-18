package com.fixmer.genesis.technology.server.process;

import java.io.*;
import com.fixmer.genesis.technology.server.packages.ProfileCoordinator;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Local, single-server process controller. Does not install updates or open worlds itself. */
public final class ServerProcessSupervisor implements AutoCloseable {
    public record Reply(boolean accepted,String outcome,String message,long revision) {}
    private record Receipt(String fingerprint,Reply reply) {}
    private final ServerProcessConfig config;
    private final ServerFiles.Lease lease;
    private final String instance=UUID.randomUUID().toString(),token;
    private final Map<UUID,Receipt> receipts=new LinkedHashMap<>();
    private ServerControlServer control;
    private ProfileCoordinator coordinator;private ProfileCoordinator.LaunchPermit runPermit;
    private Path active,lastRun;
    private Properties guardian=new Properties();
    private Process guardianProcess;
    private ServerLaunchHandoff handoff;private boolean handoffApproved;
    private ServerLifecycleView lifecycle;private UUID checkpoint;private long checkpointSequence,checkpointAt;private String checkpointOutcome="NONE";
    private ServerPreparedRestart preparedRestart;private boolean restartHistoryInterrupted,preparedRestartQueued;
    private boolean preparedStopRequested;private ServerStopPreparation stopPreparation;private String stopPreparationError="",stopWatchdogError="";
    private String phase="STOPPED",error="",lastReason="";
    private boolean startRequested,restartRequested,shutdownRequested,closed,recovered;
    private long revision,nextStart,guardLaunched;
    private int automaticRestarts,lastExit;
    private String persisted="";

    public ServerProcessSupervisor(ServerProcessConfig config)throws IOException {
        this.config=config;ServerFiles.directory(config.stateDir());ServerFiles.directory(config.stateDir().resolve("runs"));lease=new ServerFiles.Lease(config.stateDir().resolve("supervisor.lock"));
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);token=HexFormat.of().formatHex(bytes);
        try{
            coordinator=ProfileCoordinator.openLive(config.workDir());
            Path current=config.stateDir().resolve("current.properties");
            if(Files.exists(current)){
                Properties old=ServerFiles.read(current);String run=old.getProperty("run","");
                if(!run.isEmpty()){
                    if(!canonicalUuid(run))throw new IOException("Invalid previous run identifier");
                    lastRun=config.stateDir().resolve("runs").resolve(run);
                    if(Files.isRegularFile(lastRun.resolve("status.properties")))guardian=ServerFiles.read(lastRun.resolve("status.properties"));
                    boolean resolved=Files.isRegularFile(lastRun.resolve("resolved.properties"));
                    boolean exited=guardian.getProperty("phase","").equals("EXITED")&&!knownAlive(guardian);
                    if(!resolved&&!exited){active=lastRun;phase="RECOVERY_REQUIRED";error="PREVIOUS_RUN_REQUIRES_REVIEW";recovered=true;}
                }
            }
            restartHistoryInterrupted=ServerPreparedRestart.interrupted(config.stateDir());
            if(restartHistoryInterrupted){recovered=true;phase="RECOVERY_REQUIRED";error="PREPARED_RESTART_INTERRUPTED";if(!coordinator.requiresReview())coordinator.requireProcessReview(lastRun==null?null:UUID.fromString(lastRun.getFileName().toString()));}
            if(active!=null&&!coordinator.requiresReview())coordinator.requireProcessReview(UUID.fromString(active.getFileName().toString()));
            if(coordinator.requiresReview()){recovered=true;phase="RECOVERY_REQUIRED";if(!restartHistoryInterrupted)error="ADMISSION_REVIEW_REQUIRED";}
            startRequested=active==null&&!recovered&&config.startOnBoot();if(startRequested)phase="START_REQUESTED";
            control=new ServerControlServer(this,config.port(),token,instance);
            Properties endpoint=new Properties();endpoint.setProperty("version","51");endpoint.setProperty("instance",instance);endpoint.setProperty("token",token);endpoint.setProperty("port",Integer.toString(control.port()));
            endpoint.setProperty("pid",Long.toString(ProcessHandle.current().pid()));endpoint.setProperty("start",ServerFiles.start(ProcessHandle.current()));endpoint.setProperty("online","true");
            ServerFiles.write(config.stateDir().resolve("control.properties"),endpoint);persist();control.start();
        }catch(IOException|RuntimeException bad){if(control!=null)control.close();if(coordinator!=null)try{coordinator.close();}catch(IOException extra){bad.addSuppressed(extra);}lease.close();throw bad;}
    }
    public int port(){return control.port();}
    public String instance(){return instance;}
    public Path stateDir(){return config.stateDir();}
    public synchronized boolean finished(){return shutdownRequested&&active==null&&!startRequested;}
    public synchronized Map<String,Object> snapshot(){
        var result=new LinkedHashMap<String,Object>();result.put("report","server_supervisor_v51");result.put("name",config.name());result.put("instance",instance);result.put("revision",revision);result.put("phase",phase);
        result.put("processRunning",alive(guardian,"child"));result.put("guardianRunning",alive(guardian,"guardian"));result.put("minecraftReady",false);
        var admission=coordinator.snapshot();result.put("profileId",admission.profile());result.put("ownerEpoch",admission.epoch());result.put("admissionPhase",admission.phase());result.put("installationAdmission","disabled_no_native_containment");
        result.put("launchHandoff",active==null?"idle":recovered?"review_required":handoffApproved?"approved":"awaiting_guardian");result.put("saveConfirmed",!recovered&&phase.equals("RUNNING")&&lifecycle!=null&&checkpoint!=null&&checkpointOutcome.equals("COMPLETED")&&lifecycle.ready(alive(guardian,"child"),System.nanoTime())&&lifecycle.completed(checkpoint,alive(guardian,"child"),System.nanoTime()));result.put("containmentVerified",false);
        result.put("guardianPhase",guardian.getProperty("phase",""));result.put("childPid",guardian.getProperty("child.pid","0"));result.put("run",lastRun==null?"":lastRun.getFileName().toString());result.put("automaticRestarts",automaticRestarts);result.put("lastExitCode",lastExit);
        if(lifecycle!=null)result.putAll(lifecycle.describe(alive(guardian,"child"),System.nanoTime()));else{result.put("lifecycleState","UNREPORTED");result.put("lifecycleFresh",false);result.put("lifecycleError","");result.put("worldQuiesced",false);}
        if(!phase.equals("RUNNING")||recovered)result.put("minecraftReady",false);
        if(stopPreparation!=null)result.putAll(stopPreparation.describe());else if(lastRun!=null)try{result.putAll(ServerStopPreparation.inspect(lastRun));}catch(IOException invalid){result.put("stopPreparationState","UNREADABLE");result.put("stopPreparationComplete",false);}
        else{result.put("stopPreparationState","NONE");result.put("stopPreparationComplete",false);}
        result.put("stopPreparationError",stopPreparationError);result.put("stopWatchdogError",stopWatchdogError);result.put("stopPreparationIsInstallPermit",false);
        if(preparedRestart!=null)result.putAll(preparedRestart.describe());else try{result.putAll(ServerPreparedRestart.inspect(config.stateDir()));}catch(IOException invalid){result.put("preparedRestartState","UNREADABLE");result.put("preparedRestartPending",false);result.put("preparedRestartHistorical",true);result.put("preparedRestartIsInstallPermit",false);}
        result.put("checkpointRequest",checkpoint==null?"":checkpoint.toString());result.put("checkpointOutcome",checkpointOutcome);result.put("checkpointPending",checkpointOutcome.equals("PENDING")||checkpointOutcome.equals("RUNNING"));
        result.put("lastReason",lastReason);result.put("error",error);result.put("logError",guardian.getProperty("logError",""));result.put("shutdownRequested",shutdownRequested);result.put("recoveryRequired",recovered||phase.equals("RECOVERY_REQUIRED"));return result;
    }
    public synchronized Reply command(String action,UUID request,long expected,boolean confirmed)throws IOException {
        if(closed)return new Reply(false,"rejected","CONTROLLER_CLOSED",revision);
        String fingerprint=action+"|"+expected+"|"+confirmed;Receipt old=receipts.get(request);
        if(old!=null){if(!old.fingerprint().equals(fingerprint))return new Reply(false,"rejected","REQUEST_ID_REUSED",revision);return new Reply(old.reply().accepted(),"replay",old.reply().message(),revision);}
        if(expected!=revision)return new Reply(false,"rejected","STALE_CONTROL_REVISION",revision);
        revision++;Reply reply;
        try{
            String rejected=perform(action,confirmed);reply=new Reply(rejected==null,rejected==null?"accepted":"rejected",rejected==null?"REQUEST_ACCEPTED_NOT_COMPLETED":rejected,revision);persist();
        }catch(IOException failed){startRequested=false;restartRequested=false;phase="RECOVERY_REQUIRED";error="CONTROL_IO_OUTCOME_UNKNOWN";recovered=true;reply=new Reply(false,"unknown",error,revision);}
        receipts.put(request,new Receipt(fingerprint,reply));if(receipts.size()>128)receipts.remove(receipts.keySet().iterator().next());return reply;
    }
    private String perform(String action,boolean confirmed)throws IOException {
        if(shutdownRequested&&!action.equals("force-stop")&&!action.equals("resolve"))return "SHUTDOWN_IN_PROGRESS";
        switch(action){
            case "start" -> {if(recovered)return "RECOVERY_REVIEW_REQUIRED";if(preparedRestart!=null&&preparedRestart.pending())return "PREPARED_RESTART_PENDING";if(active!=null)return phase.equals("RUNNING")||phase.equals("STARTING")?null:"PROCESS_TRANSITION_IN_PROGRESS";if(coordinator.requiresReview()||coordinator.snapshot().phase()!=ProfileCoordinator.Phase.IDLE)return "ADMISSION_REVIEW_REQUIRED";startRequested=true;automaticRestarts=0;phase="START_REQUESTED";error="";}
            case "checkpoint","prepare-stop","prepare-restart" -> {
                boolean safeRestart=action.equals("prepare-restart"),prepare=safeRestart||action.equals("prepare-stop");if(prepare&&!confirmed)return "EXPLICIT_CONFIRMATION_REQUIRED";
                if(preparedRestart!=null&&preparedRestart.pending())return "PREPARED_RESTART_PENDING";
                if(safeRestart&&active!=null&&Files.exists(active.resolve("restart.preparation"),LinkOption.NOFOLLOW_LINKS))return "PREPARED_RESTART_ALREADY_USED_FOR_RUN";
                if(stopPreparation!=null&&!stopPreparation.terminal())return "STOP_PREPARATION_PENDING";
                if(recovered)return "RECOVERY_REVIEW_REQUIRED";if(active==null||!phase.equals("RUNNING")||lifecycle==null||!lifecycle.ready(alive(guardian,"child"),System.nanoTime()))return "MINECRAFT_NOT_READY";
                if(checkpointOutcome.equals("PENDING")||checkpointOutcome.equals("RUNNING")||checkpointOutcome.startsWith("UNKNOWN_"))return "CHECKPOINT_OUTCOME_PENDING";
                if(checkpointSequence==Long.MAX_VALUE)return "CHECKPOINT_COUNTER_OVERFLOW";
                coordinator.validateLaunch(runPermit);checkpoint=UUID.randomUUID();checkpointAt=System.nanoTime();checkpointOutcome="PENDING";try{long sequence=++checkpointSequence;
                    if(safeRestart)preparedRestart=ServerPreparedRestart.arm(active,instance,checkpoint,sequence,guardian);
                    if(prepare){preparedStopRequested=true;stopPreparation=ServerStopPreparation.create(active,checkpoint,sequence,guardian);stopPreparationError="";}
                    ServerLifecycleChannel.request(active,sequence,checkpoint,System.currentTimeMillis(),prepare?ServerLifecycleState.Operation.SAVE_AND_STOP:ServerLifecycleState.Operation.CHECKPOINT);if(safeRestart)preparedRestart.requested();}catch(IOException failed){checkpointOutcome="UNKNOWN_IO";if(safeRestart)throw failed;return "CHECKPOINT_IO_FAILED";}
            }
            case "cancel-prepare" -> {
                if(recovered)return "RECOVERY_REVIEW_REQUIRED";
                if(preparedRestart!=null&&preparedRestart.pending()){
                    if(preparedRestart.phase().equals("LAUNCHING"))return "PREPARED_RESTART_SUCCESSOR_ALREADY_STARTED";
                    preparedRestart.cancel("ADMIN_CANCELLED_RESTART_STOP_MAY_CONTINUE");restartRequested=false;
                    if(active!=null&&stopPreparation!=null&&lifecycle!=null&&!lifecycle.stopEvidence(stopPreparation.request(),stopPreparation.sequence())){ServerLifecycleChannel.cancel(active,stopPreparation.request());stopPreparation.unknown("ADMIN_CANCEL_REQUESTED");}
                    if(active==null&&phase.equals("RESTART_WAIT"))phase="STOPPED";return null;
                }
                if(recovered)return "RECOVERY_REVIEW_REQUIRED";if(active==null||stopPreparation==null||stopPreparation.terminal())return "NO_STOP_PREPARATION_PENDING";
                if(lifecycle!=null&&lifecycle.stopEvidence(stopPreparation.request(),stopPreparation.sequence()))return "STOP_ALREADY_REQUESTED";
                try{ServerLifecycleChannel.cancel(active,stopPreparation.request());stopPreparation.unknown("ADMIN_CANCEL_REQUESTED");}catch(IOException failed){stopPreparationError="STOP_PREPARATION_WRITE_UNKNOWN";return "CANCEL_PREPARATION_IO_FAILED";}
            }
            case "stop","shutdown" -> {
                cancelPreparedRestart("ADMIN_STOP_OR_SHUTDOWN");startRequested=false;restartRequested=false;if(action.equals("shutdown"))shutdownRequested=true;
                if(active!=null){cancelCheckpoint();ServerFiles.request(active.resolve("stop.request"));if(!recovered)phase="STOPPING";}else phase=recovered?"RECOVERY_REQUIRED":"STOPPED";
            }
            case "restart" -> {if(preparedRestart!=null&&preparedRestart.pending())return "PREPARED_RESTART_PENDING";if(recovered)return "RECOVERY_REVIEW_REQUIRED";if(active==null)return "NO_ACTIVE_PROCESS";restartRequested=true;automaticRestarts=0;cancelCheckpoint();ServerFiles.request(active.resolve("stop.request"));phase="STOPPING";}
            case "force-stop" -> {
                if(!confirmed)return "EXPLICIT_CONFIRMATION_REQUIRED";cancelPreparedRestart("ADMIN_FORCE_STOP");startRequested=false;restartRequested=false;if(active==null)return "NO_ACTIVE_PROCESS";
                cancelCheckpoint();if(alive(guardian,"guardian"))ServerFiles.request(active.resolve("force.request"));
                else{forceRecorded(guardian);recovered=true;phase="RECOVERY_REQUIRED";error="FORCED_ORPHAN_REQUIRES_REVIEW";}
            }
            case "resolve" -> {
                if(!confirmed)return "EXPLICIT_CONFIRMATION_REQUIRED";
                if(active==null&&recovered&&coordinator.requiresReview()){
                    if(knownAlive(guardian)||guardianProcess!=null&&guardianProcess.isAlive())return "RECORDED_PROCESSES_ARE_STILL_RUNNING";
                    abandonInterruptedRestart();coordinator.acknowledgeProcessReview();runPermit=null;recovered=false;phase="STOPPED";error="";return null;
                }
                if(active==null||!recovered)return "NO_RECOVERY_REVIEW_PENDING";
                if(alive(guardian,"child")||descendantsAlive(guardian))return "RECORDED_PROCESSES_ARE_STILL_RUNNING";
                if(alive(guardian,"guardian")){
                    if(!guardian.getProperty("phase","").equals("OWNERSHIP_UNCERTAIN"))return "RECORDED_PROCESSES_ARE_STILL_RUNNING";
                    ServerFiles.request(active.resolve("acknowledge.request"));error="REVIEW_ACKNOWLEDGED_WAITING_GUARDIAN";return null;
                }
                var record=new Properties();record.setProperty("acknowledgedByInstance",instance);record.setProperty("meaning","Operator checked for unrecorded processes; not an automatic world-integrity proof");ServerFiles.write(active.resolve("resolved.properties"),record);
                abandonInterruptedRestart();coordinator.acknowledgeProcessReview();runPermit=null;active=null;guardianProcess=null;recovered=false;phase="STOPPED";error="";startRequested=false;restartRequested=false;
            }
            default -> {return "UNKNOWN_ACTION";}
        }return null;
    }
    public synchronized void tick()throws IOException {
        if(closed)return;
        if(active==null){
            if(preparedRestartQueued&&(preparedRestart==null||!preparedRestart.waiting())){phase="STOPPED";preparedRestartQueued=false;}
            if(!shutdownRequested&&(startRequested||phase.equals("RESTART_WAIT")&&System.nanoTime()>=nextStart)){
                startRequested=false;try{launch();}catch(IOException failed){failPreparedRestart("SUCCESSOR_LAUNCH_FAILED");phase="FAILED";error="LAUNCH_PREPARATION_FAILED";if(runPermit!=null){try{coordinator.requireProcessReview(runPermit.run());}catch(IOException extra){failed.addSuppressed(extra);}recovered=true;phase="RECOVERY_REQUIRED";error="ADMISSION_REVIEW_REQUIRED";}else if(coordinator.requiresReview()){recovered=true;phase="RECOVERY_REQUIRED";if(!restartHistoryInterrupted)error="ADMISSION_REVIEW_REQUIRED";}}
            }persist();return;
        }
        Path status=active.resolve("status.properties");if(Files.isRegularFile(status))try{guardian=ServerFiles.read(status);}catch(IOException failed){recovered=true;phase="RECOVERY_REQUIRED";error="INVALID_GUARDIAN_METADATA";persist();return;}
        if(!recovered&&handoff!=null){
            try{
                if(!guardian.isEmpty())handoff.validateStatus(guardian,guardianProcess.pid());
                Path ready=active.resolve("handoff.ready");
                if(!handoffApproved&&Files.isRegularFile(ready)&&!Files.exists(active.resolve("stop.request"))&&!shutdownRequested&&!restartRequested){
                    coordinator.validateLaunch(runPermit);handoff.approve(active,ServerFiles.read(ready),guardianProcess.pid());handoffApproved=true;
                }
                if(!handoffApproved&&System.nanoTime()-guardLaunched>TimeUnit.MILLISECONDS.toNanos(ServerLaunchHandoff.TIMEOUT_MS))ServerFiles.request(active.resolve("stop.request"));
            }catch(IOException invalid){ServerFiles.request(active.resolve("stop.request"));coordinator.requireProcessReview(runPermit.run());recovered=true;phase="RECOVERY_REQUIRED";error="LAUNCH_HANDOFF_REJECTED";persist();return;}
        }
        boolean guardianAlive=alive(guardian,"guardian")||guardianProcess!=null&&guardianProcess.isAlive();
        boolean childAlive=alive(guardian,"child"),others=descendantsAlive(guardian);
        if(lifecycle==null&&Files.isRegularFile(active.resolve("lifecycle.binding")))try{lifecycle=new ServerLifecycleView(active);}catch(IOException invalid){error="INVALID_LIFECYCLE_REPORT";}
        if(lifecycle!=null){
            long now=System.nanoTime();lifecycle.update(active,guardian,now);
            if(checkpoint!=null&&(checkpointOutcome.equals("PENDING")||checkpointOutcome.equals("RUNNING")||checkpointOutcome.startsWith("UNKNOWN_"))){
                String observed=lifecycle.outcome(checkpoint);
                if(lifecycle.fresh(childAlive,now)&&Set.of("COMPLETED","FAILED","EXPIRED","CANCELLED").contains(observed))checkpointOutcome=checkpointOutcome.startsWith("UNKNOWN_")?"LATE_"+observed:observed;
                else if(checkpointOutcome.equals("PENDING")&&observed.equals("RUNNING"))checkpointOutcome="RUNNING";
                if((checkpointOutcome.equals("PENDING")||checkpointOutcome.equals("RUNNING"))&&now-checkpointAt>TimeUnit.SECONDS.toNanos(30)){checkpointOutcome="UNKNOWN_TIMEOUT";try{ServerLifecycleChannel.cancel(active,checkpoint);}catch(IOException ignored){/* Unknown save outcome must not stop ordinary process management. */}}
            }
        }

        if(stopPreparation!=null&&!recovered&&lifecycle!=null)try{
            if(lifecycle.stopEvidence(stopPreparation.request(),stopPreparation.sequence())){stopPreparation.acknowledged();try{stopPreparation.signalShutdownObserved();stopWatchdogError="";}catch(IOException unavailable){stopWatchdogError="SHUTDOWN_WATCHDOG_UNAVAILABLE";}if(checkpoint!=null&&checkpoint.equals(stopPreparation.request()))checkpointOutcome=checkpointOutcome.startsWith("UNKNOWN_")?"LATE_COMPLETED":checkpointOutcome.equals("LATE_COMPLETED")?checkpointOutcome:"COMPLETED";}
            String outcome=lifecycle.outcome(stopPreparation.request());
            if(Set.of("FAILED","EXPIRED","CANCELLED").contains(outcome)){if(lifecycle.stopCallUncertain(stopPreparation.request()))stopPreparation.unknown("STOP_CALL_OUTCOME_UNKNOWN");else{stopPreparation.failed("GAME_"+outcome);preparedStopRequested=false;}}
            else if(checkpointOutcome.startsWith("UNKNOWN_"))stopPreparation.unknown("SAVE_OR_STOP_OUTCOME_UNKNOWN");
        }catch(IOException invalid){stopPreparationError="STOP_PREPARATION_WRITE_UNKNOWN";}
        String state=guardian.getProperty("phase","");
        if(recovered&&error.equals("REVIEW_ACKNOWLEDGED_WAITING_GUARDIAN")&&state.equals("EXITED")&&!knownAlive(guardian)){
            var record=new Properties();record.setProperty("acknowledgedByInstance",instance);record.setProperty("meaning","Operator acknowledged uncertain ownership; world integrity not verified");ServerFiles.write(active.resolve("resolved.properties"),record);
            abandonInterruptedRestart();coordinator.acknowledgeProcessReview();runPermit=null;active=null;guardianProcess=null;recovered=false;phase="STOPPED";error="";persist();return;
        }
        if(preparedRestart!=null&&preparedRestart.pending()&&preparedRestart.from(active)){
            if(!stopPreparationError.isEmpty()||!stopWatchdogError.isEmpty()||checkpointOutcome.startsWith("UNKNOWN_")||checkpointOutcome.startsWith("LATE_")||stopPreparation==null||Set.of("FAILED","UNKNOWN","UNKNOWN_IO").contains(String.valueOf(stopPreparation.describe().get("stopPreparationState"))))cancelPreparedRestart("SAVE_STOP_NOT_CONFIRMED");
        }
        if(state.equals("OWNERSHIP_UNCERTAIN")&&!recovered){recovered=true;error="OWNERSHIP_UNCERTAIN_REQUIRES_REVIEW";}
        if(recovered){
            // Adoption is cleanup-only: no automatic restart, even if the old server exits.
            phase="RECOVERY_REQUIRED";persist();return;
        }
        if(state.equals("FAILED")&&!guardianAlive&&!Boolean.parseBoolean(guardian.getProperty("launchIntent","true"))&&!childAlive&&!others){
            if(preparedRestart!=null&&preparedRestart.pending()&&preparedRestart.targets(active))failPreparedRestart("SUCCESSOR_FAILED_BEFORE_LAUNCH");if(recovered){phase="RECOVERY_REQUIRED";persist();return;}
            releaseObservedRun();active=null;guardianProcess=null;phase="FAILED";error=guardian.getProperty("error","GUARDIAN_FAILED_BEFORE_SERVER_LAUNCH");persist();return;
        }
        if(state.equals("EXITED")&&!guardianAlive&&!childAlive&&!others){
            lastExit=(int)ServerFiles.number(guardian,"exitCode",0);lastReason=guardian.getProperty("reason","");
            boolean crash=lastReason.equals("PROCESS_CRASH")&&lastExit!=0;
            if(stopPreparation!=null&&!recovered)try{stopPreparation.exited(guardian);}catch(IOException invalid){stopPreparationError="STOP_PREPARATION_WRITE_UNKNOWN";}
            boolean safeRestart=false;
            if(preparedRestart!=null&&preparedRestart.pending()&&preparedRestart.from(active)){
                try{if(guardianProcess==null||guardianProcess.exitValue()!=0)throw new IOException("PREPARED_RESTART_GUARDIAN_EXIT_UNKNOWN");preparedRestart.ready(guardian,lifecycle);safeRestart=true;}catch(IOException invalid){failPreparedRestart("SAVE_STOP_EXIT_NOT_CONFIRMED");if(!recovered)error="PREPARED_RESTART_NOT_CONFIRMED";}
            }
            boolean suppressed=preparedRestart!=null&&(preparedRestart.from(active)||preparedRestart.targets(active)&&!preparedRestart.phase().equals("COMPLETE"));
            if(preparedRestart!=null&&preparedRestart.pending()&&preparedRestart.targets(active))failPreparedRestart("SUCCESSOR_EXITED_BEFORE_READY");
            if(recovered){phase="RECOVERY_REQUIRED";persist();return;}
            preparedRestartQueued=safeRestart;
            releaseObservedRun();active=null;guardianProcess=null;
            if(!shutdownRequested&&(safeRestart||restartRequested||!suppressed&&!preparedStopRequested&&crash&&config.restartOnCrash()&&automaticRestarts<config.maxRestarts())){
                if(!restartRequested&&!safeRestart)automaticRestarts++;restartRequested=false;phase="RESTART_WAIT";nextStart=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.restartDelayMs());
            }else{phase=lastExit==0||!crash?"STOPPED":"FAILED";if(crash&&config.restartOnCrash()&&automaticRestarts>=config.maxRestarts())error="CRASH_RESTART_LIMIT";}
        }else if(!guardianAlive&&System.nanoTime()-guardLaunched>TimeUnit.SECONDS.toNanos(10)){
            if(!Boolean.parseBoolean(guardian.getProperty("launchIntent","true"))&&!childAlive&&!others){if(preparedRestart!=null&&preparedRestart.pending()&&preparedRestart.targets(active))failPreparedRestart("SUCCESSOR_GUARDIAN_LOST_BEFORE_LAUNCH");if(recovered){phase="RECOVERY_REQUIRED";persist();return;}releaseObservedRun();active=null;guardianProcess=null;phase="FAILED";error="GUARDIAN_FAILED_BEFORE_SERVER_LAUNCH";}
            else{recovered=true;phase="RECOVERY_REQUIRED";error="GUARDIAN_LOST_SERVER_STATE_UNCERTAIN";}
        }else if(guardianAlive){
            if(Set.of("STOP_TIMEOUT","FORCE_TIMEOUT","DESCENDANTS_REMAIN","OWNERSHIP_UNCERTAIN").contains(state))phase=state;
            else if(state.equals("RUNNING")&&!restartRequested&&!Files.exists(active.resolve("stop.request"))){if(!handoffApproved){ServerFiles.request(active.resolve("stop.request"));coordinator.requireProcessReview(runPermit.run());recovered=true;phase="RECOVERY_REQUIRED";error="RUNNING_WITHOUT_HANDOFF";}else{if(runPermit!=null)coordinator.markRunning(runPermit);phase="RUNNING";}}
            else if(state.equals("STOPPING"))phase="STOPPING";
            else if(state.equals("FAILED")){error=guardian.getProperty("error","GUARDIAN_FAILED");}
        }
        if(preparedRestart!=null&&preparedRestart.phase().equals("LAUNCHING")&&active!=null){
            if(phase.equals("RUNNING")&&lifecycle!=null&&lifecycle.ready(childAlive,System.nanoTime()))try{preparedRestart.successorReady(active);}catch(IOException failed){failPreparedRestart("SUCCESSOR_READY_WRITE_UNKNOWN");}
            else if(System.nanoTime()-guardLaunched>TimeUnit.SECONDS.toNanos(60))failPreparedRestart("SUCCESSOR_READY_TIMEOUT");
            else if(Set.of("FAILED","RECOVERY_REQUIRED","STOP_TIMEOUT","FORCE_TIMEOUT","DESCENDANTS_REMAIN","OWNERSHIP_UNCERTAIN").contains(phase))failPreparedRestart("SUCCESSOR_NOT_CONFIRMED");
        }
        persist();
    }
    private void launch()throws IOException {
        config.validateLaunch();preparedStopRequested=false;stopPreparation=null;stopPreparationError="";stopWatchdogError="";lifecycle=null;checkpoint=null;checkpointSequence=0;checkpointOutcome="NONE";UUID runId=preparedRestartQueued?preparedRestart.beginLaunch(instance):UUID.randomUUID();preparedRestartQueued=false;runPermit=coordinator.reserveLaunch(runId);Path run=config.stateDir().resolve("runs").resolve(runId.toString());ServerFiles.directory(run);lastRun=run;active=run;guardian=new Properties();guardianProcess=null;phase="STARTING";error="";guardLaunched=System.nanoTime();
        var current=new Properties();current.setProperty("run",run.getFileName().toString());current.setProperty("guardianLaunchIntent","true");ServerFiles.write(config.stateDir().resolve("current.properties"),current);
        Path frozenConfig=run.resolve("launch.properties");ServerFiles.write(frozenConfig,config.snapshot());
        handoffApproved=false;var ownership=coordinator.snapshot();handoff=ServerLaunchHandoff.create(run,ownership.profile(),ownership.epoch(),ProcessHandle.current().pid(),ServerFiles.start(ProcessHandle.current()));
        var command=List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name","").startsWith("Windows")?"java.exe":"java").toString(),"-cp",absoluteClasspath(),ServerProcessGuardian.class.getName(),frozenConfig.toString(),run.toString(),Long.toString(ProcessHandle.current().pid()),ServerFiles.start(ProcessHandle.current()));
        coordinator.validateLaunch(runPermit);if(preparedRestart!=null&&preparedRestart.phase().equals("LAUNCHING"))preparedRestart.validateLaunch(runId,instance);try{guardianProcess=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(run.resolve("guardian.log").toFile()).start();}
        catch(IOException failed){var status=new Properties();status.setProperty("phase","EXITED");status.setProperty("launchIntent","false");status.setProperty("reason","GUARDIAN_SPAWN_FAILED");status.setProperty("exitCode","2");ServerFiles.write(run.resolve("status.properties"),status);guardian=status;releaseObservedRun();active=null;phase="FAILED";error="GUARDIAN_SPAWN_FAILED";throw failed;}
        // Keep the ownership pipe open until terminal exit or supervisor.close().
    }
    private void cancelPreparedRestart(String reason){preparedRestartQueued=false;if(preparedRestart!=null&&preparedRestart.pending())try{preparedRestart.cancel(reason);}catch(IOException failed){restartJournalUnknown();}}
    private void failPreparedRestart(String reason){preparedRestartQueued=false;if(preparedRestart!=null&&preparedRestart.pending())try{preparedRestart.failed(reason);}catch(IOException failed){restartJournalUnknown();}}
    private void restartJournalUnknown(){startRequested=false;restartRequested=false;preparedRestartQueued=false;recovered=true;phase="RECOVERY_REQUIRED";error="PREPARED_RESTART_WRITE_UNKNOWN";try{if(!coordinator.requiresReview())coordinator.requireProcessReview(runPermit==null?null:runPermit.run());}catch(IOException failed){stopPreparationError="ADMISSION_REVIEW_WRITE_UNKNOWN";}}
    private void abandonInterruptedRestart()throws IOException{ServerPreparedRestart.abandon(config.stateDir());preparedRestart=null;restartHistoryInterrupted=false;preparedRestartQueued=false;}
    private void cancelCheckpoint(){if(active!=null&&checkpoint!=null&&(checkpointOutcome.equals("PENDING")||checkpointOutcome.equals("RUNNING")||checkpointOutcome.startsWith("UNKNOWN_"))){try{ServerLifecycleChannel.cancel(active,checkpoint);}catch(IOException ignored){}checkpointOutcome="STOP_REQUESTED";}}
    private void releaseObservedRun()throws IOException{if(checkpointOutcome.equals("PENDING")||checkpointOutcome.equals("RUNNING")||checkpointOutcome.startsWith("UNKNOWN_"))checkpointOutcome="PROCESS_EXITED_UNKNOWN";if(guardianProcess!=null)guardianProcess.getOutputStream().close();if(runPermit!=null){coordinator.observedStopped(runPermit);runPermit=null;}}
    static String absoluteClasspath(){
        String[] paths=System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator),-1);var result=new ArrayList<String>();for(String path:paths)result.add(Path.of(path.isEmpty()?".":path).toAbsolutePath().normalize().toString());return String.join(File.pathSeparator,result);
    }
    private void persist()throws IOException {
        Map<String,Object> snapshot=snapshot();Map<String,Object> stable=new LinkedHashMap<>(snapshot);stable.remove("lifecycleLastSeenMs");String fingerprint=stable.toString();if(fingerprint.equals(persisted))return;
        var properties=new Properties();snapshot.forEach((key,value)->properties.setProperty(key,String.valueOf(value)));ServerFiles.write(config.stateDir().resolve("summary.properties"),properties);persisted=fingerprint;
    }
    static boolean alive(Properties p,String prefix){try{return ServerFiles.sameProcess(Long.parseLong(p.getProperty(prefix+".pid","0")),p.getProperty(prefix+".start",""));}catch(NumberFormatException bad){return false;}}
    static boolean descendantsAlive(Properties p){try{int n=(int)Math.min(64,ServerFiles.number(p,"descendants",0));for(int i=0;i<n;i++)if(alive(p,"desc."+i))return true;}catch(IOException ignored){return true;}return false;}
    static boolean knownAlive(Properties p){return alive(p,"guardian")||alive(p,"child")||descendantsAlive(p);}
    private static void forceRecorded(Properties p){for(int i=0;i<64;i++)forceIdentity(p,"desc."+i);forceIdentity(p,"child");}
    private static void forceIdentity(Properties p,String prefix){try{ServerFiles.ownedProcess(Long.parseLong(p.getProperty(prefix+".pid","0")),p.getProperty(prefix+".start","")).ifPresent(ProcessHandle::destroyForcibly);}catch(NumberFormatException ignored){}}
    static boolean canonicalUuid(String value){return ServerFiles.canonicalUuid(value);}
    public synchronized String logTail()throws IOException {
        if(lastRun==null)return "";Path path=lastRun.resolve("server.log");ServerFiles.safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return "";
        try(var in=java.nio.channels.FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){long size=in.size();in.position(Math.max(0,size-32768));var buffer=java.nio.ByteBuffer.allocate((int)Math.min(32768,size));while(buffer.hasRemaining()&&in.read(buffer)>0){}buffer.flip();return StandardCharsets.UTF_8.decode(buffer).toString();}
    }
    public void run()throws IOException,InterruptedException {
        while(!finished()){tick();Thread.sleep(50);}
    }
    @Override public synchronized void close()throws IOException {
        if(closed)return;closed=true;startRequested=false;restartRequested=false;
        IOException failure=null;
        try{cancelPreparedRestart("SUPERVISOR_CLOSED_NO_REPLAY");if(active!=null)ServerFiles.request(active.resolve("stop.request"));}catch(IOException bad){failure=bad;}
        try{if(guardianProcess!=null)guardianProcess.getOutputStream().close();}catch(IOException bad){if(failure==null)failure=bad;else failure.addSuppressed(bad);}
        if(control!=null)control.close();
        try{var endpoint=ServerFiles.read(config.stateDir().resolve("control.properties"));endpoint.setProperty("online","false");ServerFiles.write(config.stateDir().resolve("control.properties"),endpoint);}catch(IOException bad){if(failure==null)failure=bad;else failure.addSuppressed(bad);}
        try{if(coordinator!=null)coordinator.close();}catch(IOException bad){if(failure==null)failure=bad;else failure.addSuppressed(bad);}
        try{lease.close();}catch(IOException bad){if(failure==null)failure=bad;else failure.addSuppressed(bad);}
        if(failure!=null)throw failure;
    }
}
