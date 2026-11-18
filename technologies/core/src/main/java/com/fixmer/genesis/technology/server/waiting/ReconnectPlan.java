package com.fixmer.genesis.technology.server.waiting;

import java.util.*;

/** Pure future-screen retry scheduling. It creates no sockets and grants no server/installation admission. */
public final class ReconnectPlan {
    private ReconnectPlan() {}
    public enum Phase { WAITING, ATTEMPTING, ASK_CONTINUE, CONNECTED, CANCELLED, PERMANENT_FAILURE }
    public enum Failure { TEMPORARY, VERSION, AUTHORIZATION, BANNED, INVALID_TARGET }
    public record Policy(long initialMillis,long maximumMillis,long askAfterMillis) {
        public Policy{if(initialMillis<100||maximumMillis<initialMillis||maximumMillis>60000||askAfterMillis<1000||askAfterMillis>3600000)throw new IllegalArgumentException("Retry policy bounds");}
    }
    public record State(UUID update,UUID serverRun,String packageSha256,Policy policy,Phase phase,long generation,
                        int failures,long now,long waitingSince,long nextAttempt,UUID attempt,String reason) {
        public State{Objects.requireNonNull(update);Objects.requireNonNull(serverRun);Objects.requireNonNull(policy);Objects.requireNonNull(phase);Objects.requireNonNull(reason);
            if(update.equals(new UUID(0,0))||serverRun.equals(new UUID(0,0))||!packageSha256.matches("[a-f0-9]{64}")||generation<0||failures<0||now<0||waitingSince<0||nextAttempt<0||phase==Phase.ATTEMPTING!=(attempt!=null))throw new IllegalArgumentException("Invalid retry state");}
    }
    public static State start(UUID update,UUID serverRun,String sha,Policy policy,long now){return new State(update,serverRun,sha,policy,Phase.WAITING,1,0,now,now,now,null,"");}
    public static State begin(State s,long now,boolean admissionCurrentlyAllowed){
        time(s,now);if(s.phase()!=Phase.WAITING)return s;
        if(now-s.waitingSince()>=s.policy().askAfterMillis())return copy(s,Phase.ASK_CONTINUE,s.generation(),s.failures(),now,s.waitingSince(),now,null,"LONG_WAIT");
        if(now<s.nextAttempt()||!admissionCurrentlyAllowed)return copy(s,Phase.WAITING,s.generation(),s.failures(),now,s.waitingSince(),s.nextAttempt(),null,s.reason());
        return copy(s,Phase.ATTEMPTING,s.generation(),s.failures(),now,s.waitingSince(),now,UUID.randomUUID(),"");
    }
    public static State succeeded(State s,long generation,UUID attempt,long now){
        time(s,now);if(!current(s,generation,attempt))return s;return copy(s,Phase.CONNECTED,s.generation(),s.failures(),now,s.waitingSince(),now,null,"");
    }
    public static State failed(State s,long generation,UUID attempt,long now,Failure failure,int jitterPermille){
        time(s,now);Objects.requireNonNull(failure);if(jitterPermille<0||jitterPermille>250)throw new IllegalArgumentException("Jitter must be 0..250 permille");
        if(!current(s,generation,attempt))return s;
        if(failure!=Failure.TEMPORARY)return copy(s,Phase.PERMANENT_FAILURE,s.generation(),s.failures(),now,s.waitingSince(),now,null,failure.name());
        int failures=Math.min(30,s.failures()+1);long delay=Math.min(s.policy().maximumMillis(),Math.multiplyExact(s.policy().initialMillis(),1L<<Math.min(20,failures-1)));
        delay=Math.min(s.policy().maximumMillis(),Math.addExact(delay,delay*jitterPermille/1000));
        Phase phase=now-s.waitingSince()>=s.policy().askAfterMillis()?Phase.ASK_CONTINUE:Phase.WAITING;
        return copy(s,phase,s.generation(),failures,now,s.waitingSince(),Math.addExact(now,delay),null,phase==Phase.ASK_CONTINUE?"LONG_WAIT":"TEMPORARY");
    }
    public static State continueWaiting(State s,long now){time(s,now);if(s.phase()!=Phase.ASK_CONTINUE)return s;return copy(s,Phase.WAITING,Math.addExact(s.generation(),1),s.failures(),now,now,now,null,"");}
    public static State cancel(State s,long now){time(s,now);if(s.phase()==Phase.CANCELLED)return s;return copy(s,Phase.CANCELLED,Math.addExact(s.generation(),1),s.failures(),now,s.waitingSince(),now,null,"USER_CANCELLED");}
    private static boolean current(State s,long generation,UUID attempt){return s.phase()==Phase.ATTEMPTING&&s.generation()==generation&&s.attempt().equals(attempt);}
    private static void time(State s,long now){if(now<s.now())throw new IllegalArgumentException("Retry clock moved backward");}
    private static State copy(State s,Phase p,long gen,int failures,long now,long since,long next,UUID attempt,String reason){return new State(s.update(),s.serverRun(),s.packageSha256(),s.policy(),p,gen,failures,now,since,next,attempt,reason);}
}
