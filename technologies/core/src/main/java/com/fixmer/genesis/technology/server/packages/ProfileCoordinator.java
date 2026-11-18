package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.fixmer.genesis.technology.server.packages.AdmissionStore.State;

/** Durable launch/install exclusion. LIVE never grants installation without a future native containment backend. */
public final class ProfileCoordinator implements AutoCloseable {
    public enum Scope { LIVE, DIAGNOSTIC_NO_GAME }
    public enum Phase { IDLE, PROCESS_RESERVED, PROCESS_RUNNING, INSTALL_RESERVED, INSTALLING, RECOVERY_REQUIRED, RECOVERING }
    public record Snapshot(UUID profile,Scope scope,long epoch,long revision,Phase phase,UUID run,UUID installation,String packageSha256,String reason,boolean installationAvailable){}
    public static final class LaunchPermit {
        private final ProfileCoordinator owner;private final long epoch;private final UUID run,nonce;
        private LaunchPermit(ProfileCoordinator owner,long epoch,UUID run,UUID nonce){this.owner=owner;this.epoch=epoch;this.run=run;this.nonce=nonce;}
        public UUID run(){return run;}public long epoch(){return epoch;}
    }
    /** One-use diagnostic review reservation, never a stop proof or installation permit. */
    public static final class StopReview {
        private final ProfileCoordinator owner;private final LaunchPermit launch;private final UUID request;private final String context;private boolean consumed;
        private StopReview(ProfileCoordinator owner,LaunchPermit launch,UUID request,String context){this.owner=owner;this.launch=launch;this.request=request;this.context=context;}
    }
    private StopReview activeStopReview;
    public synchronized StopReview reserveDiagnosticStopReview(LaunchPermit launch,UUID request,String contextSha)throws IOException {
        validateLaunch(launch);PackageModel.nonzero(request);PackageModel.hash(contextSha);
        if(state.phase()!=Phase.PROCESS_RUNNING||activeStopReview!=null)throw new IOException("STOP_REVIEW_ALREADY_RESERVED_OR_NOT_RUNNING");
        activeStopReview=new StopReview(this,launch,request,contextSha);return activeStopReview;
    }
    /** Records only review-required; no code path here can grant IDLE or installation rights. */
    public synchronized void completeDiagnosticStopReview(StopReview review,UUID request,String contextSha,String aggregateSha)throws IOException {
        if(review==null||review.owner!=this||review!=activeStopReview||review.consumed)throw new IOException("STALE_STOP_REVIEW");
        review.consumed=true;
        try{
            validateLaunch(review.launch);PackageModel.hash(aggregateSha);
            if(!review.request.equals(request)||!review.context.equals(contextSha)||state.phase()!=Phase.PROCESS_RUNNING)throw new IOException("STOP_REVIEW_CONTEXT_MISMATCH");
            save(Phase.RECOVERY_REQUIRED,state.run(),null,null,null,"DIAGNOSTIC_NATIVE_STOP_REVIEW");
        }catch(IOException|RuntimeException bad){try{if(!requiresReview())requireProcessReview(review.launch.run());}catch(IOException extra){bad.addSuppressed(extra);}throw bad;}
    }
    static final class InstallPermit {
        private final ProfileCoordinator owner;private final long epoch;private final UUID tx,nonce;private final String hash;
        private InstallPermit(ProfileCoordinator owner,long epoch,UUID tx,String hash,UUID nonce){this.owner=owner;this.epoch=epoch;this.tx=tx;this.hash=hash;this.nonce=nonce;}
    }
    static final class RecoveryPermit {
        private final ProfileCoordinator owner;private final long epoch;private final UUID nonce;
        private RecoveryPermit(ProfileCoordinator owner,long epoch,UUID nonce){this.owner=owner;this.epoch=epoch;this.nonce=nonce;}
    }
    private final AdmissionStore store;private State state;private boolean closed,poisoned;
    public static Snapshot inspect(Path profile)throws IOException{return AdmissionStore.inspect(profile);}
    public static ProfileCoordinator openLive(Path profile)throws IOException{return new ProfileCoordinator(profile,null,null,PackageModel.Side.SERVER,Scope.LIVE,InstallIo.NONE);}
    static ProfileCoordinator openDiagnostic(Path profile,UUID profileId,UUID distribution,PackageModel.Side side)throws IOException{return openDiagnostic(profile,profileId,distribution,side,InstallIo.NONE);}
    static ProfileCoordinator openDiagnostic(Path profile,UUID profileId,UUID distribution,PackageModel.Side side,InstallIo.Fault fault)throws IOException{PackageModel.nonzero(profileId);PackageModel.nonzero(distribution);if(side==null||side==PackageModel.Side.BOTH)throw new IOException("TARGET_SIDE_REQUIRED");return new ProfileCoordinator(profile,profileId,distribution,side,Scope.DIAGNOSTIC_NO_GAME,fault);}
    private ProfileCoordinator(Path root,UUID profile,UUID distribution,PackageModel.Side side,Scope scope,InstallIo.Fault fault)throws IOException{
        store=new AdmissionStore(root,profile,distribution,side,scope,fault);
        try{State old=store.read();if(old==null&&store.previousBinding)throw new IOException("ADMISSION_SAVED_STATE_MISSING");long epoch=old==null?1:increment(old.epoch()),revision=old==null?1:increment(old.revision());
            if(old==null)state=new State(epoch,revision,Phase.IDLE,null,null,null,null,"NEW_PROFILE");
            else state=new State(epoch,revision,old.phase()==Phase.IDLE?Phase.IDLE:Phase.RECOVERY_REQUIRED,old.run(),old.install(),old.hash(),old.nonce(),old.phase()==Phase.IDLE?"NEW_OWNER_IDLE":"OWNER_CHANGED_RECOVERY");
            store.write(state);
        }catch(IOException|RuntimeException bad){store.close();throw bad;}
    }
    public synchronized Snapshot snapshot(){return new Snapshot(store.binding.profile(),store.binding.scope(),state.epoch(),state.revision(),state.phase(),state.run(),state.install(),state.hash(),poisoned?"STATE_WRITE_OUTCOME_UNKNOWN":closed?"OWNER_CLOSED":state.reason(),false);}
    public synchronized boolean requiresReview(){return poisoned||state.phase()==Phase.RECOVERY_REQUIRED||state.phase()==Phase.RECOVERING;}
    public synchronized LaunchPermit reserveLaunch(UUID run)throws IOException{
        ready();PackageModel.nonzero(run);if(state.phase()!=Phase.IDLE)throw new IOException("PROFILE_OPERATION_IN_PROGRESS");UUID nonce=UUID.randomUUID();save(Phase.PROCESS_RESERVED,run,null,null,nonce,"LAUNCH_RESERVED");return new LaunchPermit(this,state.epoch(),run,nonce);
    }
    public synchronized void validateLaunch(LaunchPermit permit)throws IOException{ready();if(permit==null||permit.owner!=this||permit.epoch!=state.epoch()||!Objects.equals(permit.run,state.run())||!Objects.equals(permit.nonce,state.nonce())||!(state.phase()==Phase.PROCESS_RESERVED||state.phase()==Phase.PROCESS_RUNNING))throw new IOException("STALE_LAUNCH_PERMIT");}
    /** Bind an in-memory reservation to its actual filesystem profile root. */
    public synchronized void validateLaunchFor(LaunchPermit permit,Path profileRoot)throws IOException {
        validateLaunch(permit);if(!store.binding.root().equals(profileRoot.toRealPath().toString()))throw new IOException("LAUNCH_PROFILE_ROOT_MISMATCH");
    }
    public synchronized void markRunning(LaunchPermit permit)throws IOException{validateLaunch(permit);if(state.phase()==Phase.PROCESS_RUNNING)return;save(Phase.PROCESS_RUNNING,state.run(),null,null,state.nonce(),"PROCESS_RUNNING");}
    /** Observation preserves ordinary restart behavior; it is deliberately NOT an installation proof. */
    public synchronized void observedStopped(LaunchPermit permit)throws IOException{validateLaunch(permit);if(activeStopReview!=null)throw new IOException("NATIVE_STOP_REVIEW_OWNS_LAUNCH");save(Phase.IDLE,null,null,null,null,"OBSERVED_STOP_ONLY");}
    public synchronized void requireProcessReview(UUID run)throws IOException{ready();if(state.install()!=null)throw new IOException("INSTALLATION_OWNS_PROFILE");if(run!=null)PackageModel.nonzero(run);save(Phase.RECOVERY_REQUIRED,run,null,null,null,"PROCESS_REVIEW_REQUIRED");}
    /** Manual review may resume process management. LIVE installation remains unavailable. */
    public synchronized void acknowledgeProcessReview()throws IOException{ready();if(activeStopReview!=null)throw new IOException("NATIVE_STOP_REVIEW_REQUIRES_NEW_OWNER");if(state.install()!=null||state.phase()==Phase.INSTALL_RESERVED||state.phase()==Phase.INSTALLING||state.phase()==Phase.RECOVERING)throw new IOException("INSTALLATION_RECOVERY_REQUIRED");save(Phase.IDLE,null,null,null,null,"MANUAL_PROCESS_REVIEW_ONLY");}
    public synchronized void requestLiveInstallation(UUID transaction,String packageSha256)throws IOException{ready();PackageModel.nonzero(transaction);PackageModel.hash(packageSha256);throw new IOException("NATIVE_CONTAINMENT_NOT_IMPLEMENTED");}
    synchronized InstallPermit reserveDiagnosticInstall(UUID transaction,String hash)throws IOException{
        ready();diagnostic();PackageModel.nonzero(transaction);PackageModel.hash(hash);if(state.phase()!=Phase.IDLE)throw new IOException("PROFILE_OPERATION_IN_PROGRESS");UUID nonce=UUID.randomUUID();save(Phase.INSTALL_RESERVED,null,transaction,hash,nonce,"DIAGNOSTIC_INSTALL_RESERVED");return new InstallPermit(this,state.epoch(),transaction,hash,nonce);
    }
    synchronized void validateInstall(InstallPermit p)throws IOException{
        ready();diagnostic();if(p==null||p.owner!=this||p.epoch!=state.epoch()||!p.tx.equals(state.install())||!p.hash.equals(state.hash())||!p.nonce.equals(state.nonce())||!(state.phase()==Phase.INSTALL_RESERVED||state.phase()==Phase.INSTALLING))throw new IOException("STALE_INSTALL_PERMIT");
    }
    synchronized void markInstalling(InstallPermit p)throws IOException{validateInstall(p);if(state.phase()==Phase.INSTALLING)return;save(Phase.INSTALLING,null,state.install(),state.hash(),state.nonce(),"DIAGNOSTIC_INSTALLING");}
    synchronized void completeInstall(InstallPermit p)throws IOException{validateInstall(p);save(Phase.IDLE,null,null,null,null,"DIAGNOSTIC_INSTALL_COMMITTED");}
    synchronized RecoveryPermit beginDiagnosticRecovery(UUID activeTransaction)throws IOException{
        ready();diagnostic();if(state.run()!=null)throw new IOException("PROCESS_STATE_REQUIRES_REVIEW");if(state.phase()==Phase.IDLE&&activeTransaction==null)return null;
        if(state.install()!=null&&activeTransaction!=null&&!state.install().equals(activeTransaction))throw new IOException("RECOVERY_TRANSACTION_MISMATCH");if(activeTransaction!=null)PackageModel.nonzero(activeTransaction);
        UUID nonce=UUID.randomUUID();save(Phase.RECOVERING,null,state.install()==null?activeTransaction:state.install(),state.hash(),nonce,"DIAGNOSTIC_RECOVERING");return new RecoveryPermit(this,state.epoch(),nonce);
    }
    synchronized void validateRecovery(RecoveryPermit p)throws IOException{ready();diagnostic();if(p==null||p.owner!=this||p.epoch!=state.epoch()||!p.nonce.equals(state.nonce())||state.phase()!=Phase.RECOVERING)throw new IOException("STALE_RECOVERY_PERMIT");}
    synchronized void completeDiagnosticRecovery(RecoveryPermit p)throws IOException{validateRecovery(p);save(Phase.IDLE,null,null,null,null,"DIAGNOSTIC_RECOVERY_FINISHED");}
    private void diagnostic()throws IOException{if(store.binding.scope()!=Scope.DIAGNOSTIC_NO_GAME)throw new IOException("NATIVE_CONTAINMENT_NOT_IMPLEMENTED");}
    private void ready()throws IOException{if(closed||poisoned)throw new IOException("COORDINATOR_CLOSED_OR_RECOVERY_REQUIRED");}
    private static long increment(long value)throws IOException{try{return Math.addExact(value,1);}catch(ArithmeticException overflow){throw new IOException("COORDINATOR_COUNTER_EXHAUSTED",overflow);}}
    private void save(Phase phase,UUID run,UUID install,String hash,UUID nonce,String reason)throws IOException{
        State next=new State(state.epoch(),increment(state.revision()),phase,run,install,hash,nonce,reason);try{store.write(next);state=next;}catch(IOException|RuntimeException bad){poisoned=true;throw bad;}
    }
    public synchronized void close()throws IOException{if(closed)return;closed=true;store.close();}
}
