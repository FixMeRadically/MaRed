package com.fixmer.genesis.technology.server.update;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Headless local control contract. No networking, Minecraft classes or process actions. */
public final class UpdateProtocol {
    public static final int VERSION=1;
    public static final UUID NONE=new UUID(0,0);
    private UpdateProtocol() {}

    public enum Phase {
        IDLE, PREPARING, PREPARED, OFFERING, QUIESCING, CHECKPOINTED,
        SWITCH_CONFIRMED, STARTING, VALIDATING, ACTIVE, COMPLETE,
        ABORTED, FAILED, RECOVERY_REQUIRED
    }
    public enum Action {
        BEGIN, PACKAGE_VERIFIED, OFFER, QUIESCE, CHECKPOINT_SAVED,
        CONFIRM_SWITCH, PROCESS_STARTED, WORLD_READY, OPEN_GAMEPLAY, FINISH,
        ABORT, FAIL
    }
    public enum Outcome { APPLIED, REPLAY, REJECTED }
    public enum Rejection { NONE, REQUEST_ID_REUSED, WRONG_EPOCH, STALE_REVISION, WRONG_UPDATE, UPDATE_ID_REUSED, INVALID_PHASE }

    public record State(long revision,long epoch,UUID updateId,String packageSha256,
                        Phase phase,boolean gameplayOpened,String failureCode) {
        public State {
            Objects.requireNonNull(updateId);Objects.requireNonNull(packageSha256);
            Objects.requireNonNull(phase);Objects.requireNonNull(failureCode);
            if(revision<0||epoch<0||epoch>revision)throw new IllegalArgumentException("Invalid state counters");
            if(phase==Phase.IDLE){
                if(!updateId.equals(NONE)||!packageSha256.isEmpty()||gameplayOpened||!failureCode.isEmpty())throw new IllegalArgumentException("Invalid idle state");
            }else if(updateId.equals(NONE)||!hash(packageSha256))throw new IllegalArgumentException("Missing update identity");
            if((phase==Phase.ACTIVE||phase==Phase.COMPLETE)&&!gameplayOpened||gameplayOpened&&phase!=Phase.ACTIVE&&phase!=Phase.COMPLETE&&phase!=Phase.RECOVERY_REQUIRED)throw new IllegalArgumentException("Invalid gameplay flag");
            if((phase==Phase.FAILED||phase==Phase.RECOVERY_REQUIRED)!=!failureCode.isEmpty())throw new IllegalArgumentException("Invalid failure state");
            if(!failureCode.isEmpty()&&!failureCode.matches("[A-Z][A-Z0-9_]{0,63}"))throw new IllegalArgumentException("Invalid failure code");
        }
        public static State initial(){return new State(0,0,NONE,"",Phase.IDLE,false,"");}
        State acquire(){return new State(Math.addExact(revision,1),Math.addExact(epoch,1),updateId,packageSha256,phase,gameplayOpened,failureCode);}
    }

    public record Command(UUID requestId,long epoch,long expectedRevision,UUID updateId,
                          Action action,String packageSha256,String failureCode) {
        public Command {
            Objects.requireNonNull(requestId);Objects.requireNonNull(updateId);Objects.requireNonNull(action);
            Objects.requireNonNull(packageSha256);Objects.requireNonNull(failureCode);
            if(requestId.equals(NONE)||updateId.equals(NONE)||epoch<1||expectedRevision<1)throw new IllegalArgumentException("Invalid command identity");
            if(action==Action.BEGIN?!hash(packageSha256):!packageSha256.isEmpty())throw new IllegalArgumentException("Package hash belongs to BEGIN only");
            if(action==Action.FAIL?!failureCode.matches("[A-Z][A-Z0-9_]{0,63}"):!failureCode.isEmpty())throw new IllegalArgumentException("Failure code belongs to FAIL only");
        }
        public String fingerprint(){return sha((VERSION+"\n"+requestId+"\n"+epoch+"\n"+expectedRevision+"\n"+updateId+"\n"+action+"\n"+packageSha256+"\n"+failureCode).getBytes(StandardCharsets.UTF_8));}
    }
    public record Receipt(UUID requestId,String fingerprint,long appliedRevision) {
        public Receipt {
            Objects.requireNonNull(requestId);Objects.requireNonNull(fingerprint);
            if(requestId.equals(NONE)||!hash(fingerprint)||appliedRevision<1)throw new IllegalArgumentException("Invalid receipt");
        }
    }
    public record Result(Outcome outcome,Rejection rejection,long appliedRevision,State state) {
        public Result {Objects.requireNonNull(outcome);Objects.requireNonNull(rejection);Objects.requireNonNull(state);}
    }
    public static boolean hash(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
    static String sha(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
