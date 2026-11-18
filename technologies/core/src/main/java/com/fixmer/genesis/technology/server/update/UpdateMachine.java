package com.fixmer.genesis.technology.server.update;

import java.util.*;
import static com.fixmer.genesis.technology.server.update.UpdateProtocol.*;

/** Pure transition validation. Milestone commands are assertions of a future trusted adapter. */
public final class UpdateMachine {
    private UpdateMachine() {}
    public record Decision(Rejection rejection,State state) {public boolean accepted(){return rejection==Rejection.NONE;}}
    public static Decision advance(State current,Command command){
        Objects.requireNonNull(current);Objects.requireNonNull(command);
        if(command.epoch()!=current.epoch())return reject(Rejection.WRONG_EPOCH,current);
        if(command.expectedRevision()!=current.revision())return reject(Rejection.STALE_REVISION,current);
        if(command.action()!=Action.BEGIN&&!command.updateId().equals(current.updateId()))return reject(Rejection.WRONG_UPDATE,current);
        Phase next=null;
        switch(command.action()){
            case BEGIN -> {
                if(EnumSet.of(Phase.IDLE,Phase.COMPLETE,Phase.ABORTED,Phase.FAILED).contains(current.phase())&&!command.updateId().equals(current.updateId()))next=Phase.PREPARING;
            }
            case PACKAGE_VERIFIED -> {if(current.phase()==Phase.PREPARING)next=Phase.PREPARED;}
            case OFFER -> {if(current.phase()==Phase.PREPARED)next=Phase.OFFERING;}
            case QUIESCE -> {if(current.phase()==Phase.OFFERING)next=Phase.QUIESCING;}
            case CHECKPOINT_SAVED -> {if(current.phase()==Phase.QUIESCING)next=Phase.CHECKPOINTED;}
            case CONFIRM_SWITCH -> {if(current.phase()==Phase.CHECKPOINTED)next=Phase.SWITCH_CONFIRMED;}
            case PROCESS_STARTED -> {if(current.phase()==Phase.SWITCH_CONFIRMED)next=Phase.STARTING;}
            case WORLD_READY -> {if(current.phase()==Phase.STARTING)next=Phase.VALIDATING;}
            case OPEN_GAMEPLAY -> {if(current.phase()==Phase.VALIDATING)next=Phase.ACTIVE;}
            case FINISH -> {if(current.phase()==Phase.ACTIVE)next=Phase.COMPLETE;}
            case ABORT -> {if(EnumSet.of(Phase.PREPARING,Phase.PREPARED,Phase.OFFERING).contains(current.phase()))next=Phase.ABORTED;}
            case FAIL -> {
                if(EnumSet.of(Phase.PREPARING,Phase.PREPARED,Phase.OFFERING).contains(current.phase()))next=Phase.FAILED;
                else if(EnumSet.of(Phase.QUIESCING,Phase.CHECKPOINTED,Phase.SWITCH_CONFIRMED,Phase.STARTING,Phase.VALIDATING,Phase.ACTIVE).contains(current.phase()))next=Phase.RECOVERY_REQUIRED;
            }
        }
        if(next==null)return reject(Rejection.INVALID_PHASE,current);
        boolean begin=command.action()==Action.BEGIN;
        var state=new State(Math.addExact(current.revision(),1),current.epoch(),begin?command.updateId():current.updateId(),
            begin?command.packageSha256():current.packageSha256(),next,begin?false:current.gameplayOpened()||next==Phase.ACTIVE,
            command.action()==Action.FAIL?command.failureCode():"");
        return new Decision(Rejection.NONE,state);
    }
    private static Decision reject(Rejection rejection,State state){return new Decision(rejection,state);}
}
