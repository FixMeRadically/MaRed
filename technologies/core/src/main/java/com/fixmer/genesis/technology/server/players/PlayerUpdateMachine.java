package com.fixmer.genesis.technology.server.players;

import java.util.*;
import static com.fixmer.genesis.technology.server.players.PlayerUpdateProtocol.*;

/** Pure coordination transitions. No server stop, installation, networking or game access. */
public final class PlayerUpdateMachine {
    private PlayerUpdateMachine() {}
    public static Result advance(State s,Command c) {
        Objects.requireNonNull(s);Objects.requireNonNull(c);
        if(!s.update().equals(c.update()))return no(s,"WRONG_UPDATE");
        if(s.epoch()!=c.epoch())return no(s,"WRONG_EPOCH");
        if(s.revision()!=c.revision())return no(s,"STALE_REVISION");
        if(c.now()<s.now())return no(s,"CLOCK_MOVED_BACKWARD");
        if(s.phase()==Phase.CANCELLED)return no(s,"CANCELLED");
        if(s.paused()&&c.action()!=Action.RESUME_TIMERS&&c.action()!=Action.CANCEL)return no(s,"OWNER_REVIEW_REQUIRED");
        if(!s.paused()&&c.action()==Action.RESUME_TIMERS)return no(s,"NOT_PAUSED");
        var b=new Builder(s,c.now());Player p=s.players().get(c.player());
        boolean binding=switch(c.action()){case ACCEPT,DECLINE,DOWNLOADED,REPORT_RUNTIME,ACCEPT_RUNTIME,DISCONNECT,MARK_CONNECTED->true;default->false;};
        if(binding&&(p==null||!p.online()||!p.connection().equals(c.connection())||!p.offer().equals(c.offer())))return no(s,"STALE_PLAYER_BINDING");
        if(!c.packageSha256().isEmpty()&&!c.packageSha256().equals(s.packageSha256()))return no(s,"WRONG_PACKAGE");
        if((c.action()==Action.REOFFER||c.action()==Action.REBIND)&&s.players().values().stream().anyMatch(v->!v.id().equals(c.player())&&v.connection().equals(c.connection())))return no(s,"CONNECTION_ID_REUSED");
        switch(c.action()) {
            case ACCEPT,DECLINE -> {
                if(p.decision()!=Decision.PENDING)return no(s,"DECISION_ALREADY_MADE");
                if(c.now()>=p.deadline())return no(s,"DECISION_DEADLINE_PASSED");
                b.players.put(p.id(),p.decision(c.action()==Action.ACCEPT?Decision.ACCEPTED:Decision.DECLINED));
            }
            case TICK -> {
                b.expire();
                if(!s.frozen()&&c.now()>=s.decisionDeadline())b.freeze();
            }
            case FREEZE_COHORT -> {
                if(s.frozen())return no(s,"COHORT_ALREADY_FROZEN");
                b.expire();
                if(s.audience().stream().map(b.players::get).anyMatch(v->v.originalOffer()&&v.decision()==Decision.PENDING&&c.now()<s.decisionDeadline()))return no(s,"DECISIONS_PENDING");
                b.freeze();
            }
            case DOWNLOADED -> {
                if(p.decision()!=Decision.ACCEPTED||p.progress()!=Progress.NONE)return no(s,"INVALID_CLIENT_PHASE");
                b.players.put(p.id(),p.progress(Progress.DOWNLOADED));
            }
            case REPORT_RUNTIME -> {
                if(!s.switchConfirmed())return no(s,"SWITCH_NOT_CONFIRMED");
                if(p.decision()!=Decision.ACCEPTED||p.progress()!=Progress.DOWNLOADED)return no(s,"INVALID_CLIENT_PHASE");
                b.players.put(p.id(),p.progress(Progress.RUNTIME_REPORTED));
            }
            case ACCEPT_RUNTIME -> {
                if(!s.switchConfirmed()||p.progress()!=Progress.RUNTIME_REPORTED)return no(s,"RUNTIME_REPORT_REQUIRED");
                b.players.put(p.id(),p.progress(Progress.RUNTIME_ACCEPTED));
            }
            case REOFFER -> {
                if(p!=null&&p.connection().equals(c.connection()))return no(s,"SAME_CONNECTION");
                if(p!=null&&(p.decision()==Decision.ACCEPTED||p.decision()==Decision.PENDING&&c.now()<p.deadline()))return no(s,"REJOIN_MUST_RESUME_EXISTING_OFFER");
                if(b.players.values().stream().anyMatch(v->v.offer().equals(c.offer())))return no(s,"OFFER_ID_REUSED");
                if(p==null&&b.players.size()>=MAX_PLAYERS)return no(s,"PLAYER_LIMIT");
                b.players.put(c.player(),new Player(c.player(),c.connection(),c.offer(),add(c.now(),s.policy().decisionMillis()),Decision.PENDING,Progress.NONE,true,false));
            }
            case REBIND -> {
                // Only the trusted adapter may resume a still-pending/accepted offer after authenticating the new connection.
                if(p==null||(p.decision()!=Decision.ACCEPTED&&p.decision()!=Decision.PENDING)||p.decision()==Decision.PENDING&&c.now()>=p.deadline()||!p.offer().equals(c.offer())||p.connection().equals(c.connection()))return no(s,"REBIND_REFUSED");
                b.players.put(p.id(),new Player(p.id(),c.connection(),p.offer(),p.deadline(),p.decision(),p.progress()==Progress.NONE?Progress.NONE:Progress.DOWNLOADED,true,p.originalOffer()));
            }
            case DISCONNECT -> {
                b.players.put(p.id(),new Player(p.id(),p.connection(),p.offer(),p.deadline(),p.decision(),p.progress()==Progress.NONE?Progress.NONE:Progress.DOWNLOADED,false,p.originalOffer()));
            }
            case MARK_CONNECTED -> {
                if(p.progress()!=Progress.RUNTIME_ACCEPTED)return no(s,"INVALID_CLIENT_PHASE");
                if(!s.canConnect(p.id()))return no(s,"CONNECT_GATE_CLOSED");
                b.players.put(p.id(),p.progress(Progress.CONNECTED));
            }
            case CONFIRM_SWITCH -> {
                if(!s.frozen())return no(s,"COHORT_NOT_FROZEN");
                if(s.switchConfirmed())return no(s,"SWITCH_ALREADY_CONFIRMED");
                b.serverRun=c.serverRun();b.barrierDeadline=add(c.now(),s.policy().barrierMillis());
            }
            case SERVER_READY,SERVER_NOT_READY -> {
                if(!s.switchConfirmed()||!s.serverRun().equals(c.serverRun()))return no(s,"WRONG_SERVER_RUN");
                b.serverReady=c.action()==Action.SERVER_READY;
            }
            case APPROVE_TIMEOUT -> {
                if(!s.switchConfirmed()||s.gateOpen()||c.now()<s.barrierDeadline())return no(s,"BARRIER_TIMEOUT_NOT_REACHED");
                b.timeoutApproved=true;
            }
            case OPEN_GATE -> {
                if(!s.frozen()||!s.switchConfirmed()||!s.serverReady())return no(s,"SERVER_NOT_READY");
                if(s.gateOpen())return no(s,"GATE_ALREADY_OPEN");
                boolean timeout=c.now()>=s.barrierDeadline()&&(s.timeoutApproved()||s.policy().timeoutPolicy()==TimeoutPolicy.RELEASE_READY);
                if(s.readyCount()<s.requiredCount()&&!timeout)return no(s,"CLIENT_THRESHOLD_NOT_REACHED");
                b.gateOpen=true;
            }
            case CANCEL -> {
                if(s.switchConfirmed())return no(s,"SWITCH_ALREADY_CONFIRMED");
                b.phase=Phase.CANCELLED;b.gateOpen=false;
            }
            case RESUME_TIMERS -> {
                // Explicit review re-bases the recorded remaining time; downtime never silently expires offers.
                long elapsed=c.now()-s.now();
                b.decisionDeadline=add(s.decisionDeadline(),elapsed);
                if(s.switchConfirmed())b.barrierDeadline=add(s.barrierDeadline(),elapsed);
                b.players.replaceAll((id,v)->v.decision()==Decision.PENDING?new Player(v.id(),v.connection(),v.offer(),add(v.deadline(),elapsed),v.decision(),v.progress(),v.online(),v.originalOffer()):v);
                b.paused=false;
            }
        }
        return new Result("APPLIED","NONE",b.build(s.epoch(),add(s.revision(),1)));
    }
    /** Ownership recovery is explicit and pauses all externally actionable advice until reviewed. */
    static State acquired(State s) {
        var b=new Builder(s,s.now());b.paused=true;b.serverReady=false;b.gateOpen=false;
        b.players.replaceAll((id,p)->new Player(p.id(),p.connection(),p.offer(),p.deadline(),p.decision(),p.progress()==Progress.NONE?Progress.NONE:Progress.DOWNLOADED,false,p.originalOffer()));
        return b.build(add(s.epoch(),1),add(s.revision(),1));
    }
    static Result no(State s,String reason){return new Result("REJECTED",reason,s);}
    private static final class Builder {
        final State old;final long now;final Map<UUID,Player> players;Set<UUID> cohort;
        long decisionDeadline,barrierDeadline;Phase phase;boolean paused,frozen,serverReady,timeoutApproved,gateOpen;UUID serverRun;
        Builder(State s,long now){old=s;this.now=now;players=new HashMap<>(s.players());cohort=s.cohort();decisionDeadline=s.decisionDeadline();barrierDeadline=s.barrierDeadline();phase=s.phase();paused=s.paused();frozen=s.frozen();serverReady=s.serverReady();timeoutApproved=s.timeoutApproved();gateOpen=s.gateOpen();serverRun=s.serverRun();}
        void expire(){players.replaceAll((id,p)->p.decision()==Decision.PENDING&&now>=p.deadline()?p.decision(Decision.EXPIRED):p);}
        void freeze(){var ids=new HashSet<UUID>();for(UUID id:old.audience()){Player p=players.get(id);if(p.originalOffer()&&p.decision()==Decision.ACCEPTED)ids.add(id);}cohort=Set.copyOf(ids);frozen=true;phase=Phase.WAITING;barrierDeadline=0;}
        State build(long epoch,long revision){return new State(old.update(),old.packageSha256(),old.policy(),epoch,revision,now,decisionDeadline,barrierDeadline,phase,paused,frozen,old.audience(),cohort,players,serverRun,serverReady,timeoutApproved,gateOpen);}
    }
}
