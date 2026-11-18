package com.fixmer.genesis.technology.server.players;

import java.util.*;

/** Trusted-controller contract, NOT a packet handler or proof of installation. Times come from the controller. */
public final class PlayerUpdateProtocol {
    private PlayerUpdateProtocol() {}
    public static final int SCHEMA=55, MAX_PLAYERS=512, MAX_RECEIPTS=128;
    public static final UUID NONE=new UUID(0,0);
    public enum Phase { OFFERING, WAITING, CANCELLED }
    public enum Decision { PENDING, ACCEPTED, DECLINED, EXPIRED }
    public enum Progress { NONE, DOWNLOADED, RUNTIME_REPORTED, RUNTIME_ACCEPTED, CONNECTED }
    public enum TimeoutPolicy { WAIT_FOR_ADMIN, RELEASE_READY }
    public enum Action {
        TICK, ACCEPT, DECLINE, DOWNLOADED, REPORT_RUNTIME, ACCEPT_RUNTIME, REOFFER, REBIND,
        DISCONNECT, MARK_CONNECTED, FREEZE_COHORT, CONFIRM_SWITCH, SERVER_READY, SERVER_NOT_READY, OPEN_GATE,
        APPROVE_TIMEOUT, CANCEL, RESUME_TIMERS
    }
    public record Policy(long decisionMillis,int readyPercent,long barrierMillis,TimeoutPolicy timeoutPolicy) {
        public Policy {
            if(decisionMillis<1000||decisionMillis>300000||readyPercent<1||readyPercent>100||barrierMillis<1000||barrierMillis>86400000)
                throw new IllegalArgumentException("Policy out of bounds");
            Objects.requireNonNull(timeoutPolicy);
        }
        public static Policy defaults(long barrierMillis) {return new Policy(30000,25,barrierMillis,TimeoutPolicy.WAIT_FOR_ADMIN);}
    }
    public record Offer(UUID player,UUID connection,UUID offer) {
        public Offer {id(player);id(connection);id(offer);}
    }
    public record Player(UUID id,UUID connection,UUID offer,long deadline,Decision decision,Progress progress,boolean online,boolean originalOffer) {
        public Player {PlayerUpdateProtocol.id(id);PlayerUpdateProtocol.id(connection);PlayerUpdateProtocol.id(offer);time(deadline);Objects.requireNonNull(decision);Objects.requireNonNull(progress);}
        Player decision(Decision d) {return new Player(id,connection,offer,deadline,d,Progress.NONE,online,originalOffer);}
        Player progress(Progress p) {return new Player(id,connection,offer,deadline,decision,p,online,originalOffer);}
    }
    public record State(UUID update,String packageSha256,Policy policy,long epoch,long revision,long now,
                        long decisionDeadline,long barrierDeadline,Phase phase,boolean paused,boolean frozen,
                        Set<UUID> audience,Set<UUID> cohort,Map<UUID,Player> players,
                        UUID serverRun,boolean serverReady,boolean timeoutApproved,boolean gateOpen) {
        public State {
            id(update);hash(packageSha256);Objects.requireNonNull(policy);time(epoch);time(revision);time(now);time(decisionDeadline);time(barrierDeadline);Objects.requireNonNull(phase);Objects.requireNonNull(serverRun);
            audience=Set.copyOf(audience);cohort=Set.copyOf(cohort);players=Map.copyOf(players);
            if(players.size()>MAX_PLAYERS||!players.keySet().containsAll(audience)||!audience.containsAll(cohort))throw new IllegalArgumentException("Invalid player sets");
            for(var e:players.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("Player key mismatch");
        }
        public int readyCount() {return (int)cohort.stream().map(players::get).filter(PlayerUpdateProtocol::runtimeAccepted).count();}
        public int requiredCount() {return (cohort.size()*policy.readyPercent()+99)/100;}
        public boolean switchConfirmed() {return !serverRun.equals(NONE);}
        public boolean canConnect(UUID player) {return !paused&&phase==Phase.WAITING&&gateOpen&&serverReady&&runtimeAccepted(players.get(player));}
        public boolean canRestart(UUID player) {var p=players.get(player);return !paused&&phase!=Phase.CANCELLED&&switchConfirmed()&&p!=null&&p.online()&&p.decision()==Decision.ACCEPTED&&p.progress()==Progress.DOWNLOADED;}
    }
    /** IDs are correlation only. Authentication and privileges MUST be checked by a future transport adapter. */
    public record Command(UUID request,long epoch,long revision,UUID update,long now,Action action,
                          UUID player,UUID connection,UUID offer,String packageSha256,UUID serverRun) {
        public Command {
            id(request);time(epoch);time(revision);id(update);time(now);Objects.requireNonNull(action);
            Objects.requireNonNull(player);Objects.requireNonNull(connection);Objects.requireNonNull(offer);Objects.requireNonNull(packageSha256);Objects.requireNonNull(serverRun);
            boolean bound=switch(action){case ACCEPT,DECLINE,DOWNLOADED,REPORT_RUNTIME,ACCEPT_RUNTIME,REOFFER,REBIND,DISCONNECT,MARK_CONNECTED->true;default->false;};
            if(bound){id(player);id(connection);id(offer);}else if(!player.equals(NONE)||!connection.equals(NONE)||!offer.equals(NONE))throw new IllegalArgumentException("Unexpected player binding");
            if(action==Action.DOWNLOADED||action==Action.REPORT_RUNTIME||action==Action.ACCEPT_RUNTIME)hash(packageSha256);
            else if(!packageSha256.isEmpty())throw new IllegalArgumentException("Unexpected package hash");
            if(action==Action.CONFIRM_SWITCH||action==Action.SERVER_READY||action==Action.SERVER_NOT_READY)id(serverRun);
            else if(!serverRun.equals(NONE))throw new IllegalArgumentException("Unexpected server binding");
        }
        public static Command control(State s,Action action,long now) {return new Command(UUID.randomUUID(),s.epoch(),s.revision(),s.update(),now,action,NONE,NONE,NONE,"",NONE);}
        public static Command bound(State s,Action action,UUID player,long now) {var p=Objects.requireNonNull(s.players().get(player));return new Command(UUID.randomUUID(),s.epoch(),s.revision(),s.update(),now,action,player,p.connection(),p.offer(),switch(action){case DOWNLOADED,REPORT_RUNTIME,ACCEPT_RUNTIME->s.packageSha256();default->"";},NONE);}
    }
    public record Receipt(UUID request,String fingerprint,long revision) {}
    public record Result(String outcome,String reason,State state) {public boolean accepted(){return outcome.equals("APPLIED")||outcome.equals("REPLAY");}}
    public static State start(UUID update,String sha,Policy policy,long now,Collection<Offer> offers) {
        id(update);hash(sha);time(now);Objects.requireNonNull(policy);
        if(offers.size()>MAX_PLAYERS)throw new IllegalArgumentException("Too many players");
        var players=new TreeMap<UUID,Player>();var nonces=new HashSet<UUID>();var connections=new HashSet<UUID>();long deadline=add(now,policy.decisionMillis());
        for(Offer o:offers)if(!nonces.add(o.offer())||!connections.add(o.connection())||players.put(o.player(),new Player(o.player(),o.connection(),o.offer(),deadline,Decision.PENDING,Progress.NONE,true,true))!=null)throw new IllegalArgumentException("Duplicate offer/player");
        return new State(update,sha,policy,1,0,now,deadline,0,Phase.OFFERING,false,false,players.keySet(),Set.of(),players,NONE,false,false,false);
    }
    static boolean runtimeAccepted(Player p){return p!=null&&p.online()&&p.decision()==Decision.ACCEPTED&&(p.progress()==Progress.RUNTIME_ACCEPTED||p.progress()==Progress.CONNECTED);}
    static void id(UUID id){if(id==null||id.equals(NONE))throw new IllegalArgumentException("Nonzero ID required");}
    static void hash(String hash){if(hash==null||!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Canonical package SHA256 required");}
    static void time(long n){if(n<0)throw new IllegalArgumentException("Negative time/counter");}
    static long add(long a,long b){return Math.addExact(a,b);}
}
