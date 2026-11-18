package com.fixmer.genesis.technology.server.waiting;

import java.io.*;
import java.util.*;
import java.util.function.LongSupplier;
import com.fixmer.genesis.technology.server.players.*;
import static com.fixmer.genesis.technology.server.players.PlayerUpdateProtocol.*;

/** Serialized controller worker state. Grants are local bearer capabilities, NOT verified Minecraft identities. */
final class WaitingBroker {
    static final long TTL=15*60*1000,HEARTBEAT=15000;
    record Grant(UUID player,UUID connection,UUID offer,long epoch,String token,long expires,long seen) {}
    record Issued(String fingerprint,Map<String,String> response) {}
    final UUID instance=UUID.randomUUID();final String controller=WaitingFiles.token();
    final PlayerUpdateJournal journal;final LongSupplier clock;final long base,nano;
    final Map<UUID,Grant> grants=new HashMap<>();final LinkedHashMap<UUID,Issued> issued=new LinkedHashMap<>();final Set<UUID> issuanceIds=new HashSet<>();
    private boolean failed,stopped;
    WaitingBroker(PlayerUpdateJournal journal,LongSupplier clock)throws IOException{this.journal=journal;this.clock=clock;base=readState().now();nano=clock.getAsLong();}
    private State readState()throws IOException{try{return journal.state();}catch(IOException failure){fail();throw new Unavailable();}}
    private long now(){return Math.addExact(base,Math.max(0,(clock.getAsLong()-nano)/1000000));}
    synchronized State state()throws IOException{healthy();return readState();}
    static final class Unavailable extends IOException{Unavailable(){super("SERVICE_REVIEW_REQUIRED");}}
    synchronized void fail(){failed=true;grants.clear();}
    private void healthy()throws IOException{if(failed||stopped)throw new Unavailable();}
    synchronized void stop(){stopped=true;grants.clear();issued.clear();}
    synchronized boolean controller(String token){return WaitingFiles.equal(controller,token);}
    synchronized Grant player(String token)throws IOException{
        healthy();long now=now();State s=readState();
        for(Grant g:grants.values())if(WaitingFiles.equal(g.token(),token)){
            Player p=s.players().get(g.player());
            if(g.expires()<=now||g.epoch()!=s.epoch()||p==null||!p.online()||!p.connection().equals(g.connection())||!p.offer().equals(g.offer()))return null;
            return g;
        }return null;
    }
    private Result execute(Command c)throws IOException{try{return journal.execute(c);}catch(IOException|RuntimeException e){failed=true;grants.clear();throw new Unavailable();}}
    // Trusted loopback game actor only. A sequence tombstone survives logout until this service owner ends.
    record GameFence(long sequence,UUID connection,boolean closed,Map<String,String> grant) {}
    private UUID gameRun;private boolean gameClosed;private final Map<UUID,GameFence> gameFences=new HashMap<>();
    synchronized boolean gameActive(){return gameRun!=null;}
    synchronized Map<String,String> gameOpen(Map<String,String> in)throws IOException{
        healthy();if(gameClosed)throw new IOException("GAME_ACTOR_CLOSED");WaitingFiles.keys(in,"run");UUID run=WaitingFiles.id(in.get("run"));
        if(gameRun!=null&&!gameRun.equals(run))throw new IOException("GAME_ACTOR_ALREADY_BOUND_RESTART_REVIEW_REQUIRED");
        if(gameRun==null){if(!grants.isEmpty())throw new IOException("LEGACY_GRANTS_ACTIVE");gameRun=run;}
        return Map.of("run",gameRun.toString(),"scope","SERVER_PROXIED_CONSENT_ONLY");
    }
    synchronized Map<String,String> gameClose(Map<String,String> in)throws IOException{
        healthy();WaitingFiles.keys(in,"run");if(gameRun==null||!gameRun.equals(WaitingFiles.id(in.get("run"))))throw new IOException("GAME_ACTOR_CHANGED");
        gameClosed=true;for(UUID id:List.copyOf(grants.keySet()))revoke(Map.of("player",id.toString()));return Map.of("closed","true");
    }
    synchronized Map<String,String> gameEvent(Map<String,String> in)throws IOException{
        healthy();WaitingFiles.keys(in,"run","player","connection","sequence","action","request","offer","revision","decision");
        if(gameClosed||gameRun==null||!gameRun.equals(WaitingFiles.id(in.get("run"))))throw new IOException("GAME_ACTOR_CHANGED");
        UUID id=WaitingFiles.id(in.get("player")),connection=WaitingFiles.id(in.get("connection"));long seq=WaitingFiles.number(in.get("sequence"));
        if(seq==0)throw new IOException("SEQUENCE_REQUIRED");String action=in.get("action");GameFence old=gameFences.get(id);
        if(old!=null&&(seq<old.sequence()||seq==old.sequence()&&!connection.equals(old.connection())))throw new IOException("STALE_GAME_SESSION");
        if(!Set.of("bind","logout","status","decision").contains(action))throw new IOException("GAME_ACTION_UNAVAILABLE");
        if(action.equals("bind")||action.equals("logout")){
            if(old!=null&&seq==old.sequence()){
                if(action.equals("logout")&&old.closed())return Map.of("revoked","true");
                if(action.equals("bind")&&!old.closed()&&old.grant()!=null&&player(old.grant().get("token"))!=null)return old.grant();
                throw new IOException("SESSION_EVENT_ALREADY_FINAL");
            }
            if(!gameFences.containsKey(id)&&gameFences.size()>=512)throw new IOException("GAME_PLAYER_BOUND");
            // Fence FIRST: a failed/unknown issuance must never permit an older request to revive access.
            gameFences.put(id,new GameFence(seq,connection,true,null));revoke(Map.of("player",id.toString()));
            if(action.equals("logout"))return Map.of("revoked","true");
            var grant=issue(Map.of("request",in.get("request"),"revision",Long.toString(state().revision()),"player",id.toString()));
            gameFences.put(id,new GameFence(seq,connection,false,grant));return grant;
        }
        if(old==null||old.closed()||seq!=old.sequence()||!connection.equals(old.connection())||old.grant()==null)throw new IOException("GAME_SESSION_NOT_BOUND");
        var grant=player(old.grant().get("token"));if(grant==null)throw new IOException("GAME_GRANT_EXPIRED");
        if(action.equals("status"))return playerStatus(grant);
        if(!grant.offer().toString().equals(in.get("offer")))throw new IOException("GAME_OFFER_CHANGED");
        return decide(grant,Map.of("request",in.get("request"),"revision",in.get("revision"),"decision",in.get("decision")));
    }
    synchronized Map<String,String> issue(Map<String,String> in)throws IOException{
        healthy();WaitingFiles.keys(in,"request","revision","player");UUID request=WaitingFiles.id(in.get("request")),id=WaitingFiles.id(in.get("player"));long revision=WaitingFiles.number(in.get("revision"));
        String fingerprint=id+":"+revision;Issued old=issued.get(request);
        if(old!=null){if(!old.fingerprint().equals(fingerprint))throw new IOException("REQUEST_ID_REUSED");Grant g=grants.get(id);if(g==null||!WaitingFiles.equal(g.token(),old.response().get("token"))||g.expires()<=now())throw new IOException("ISSUANCE_NO_LONGER_CURRENT");return old.response();}
        if(issuanceIds.contains(request))throw new IOException("ISSUANCE_HISTORY_EVICTED");
        if(issuanceIds.size()>=4096)throw new IOException("ISSUANCE_LIMIT_RESTART_REVIEW_REQUIRED");
        State s=readState();if(s.paused())throw new IOException("OWNER_REVIEW_REQUIRED");if(s.revision()!=revision)throw new IOException("STALE_REVISION");
        Player p=s.players().get(id);UUID connection=UUID.randomUUID(),offer=p==null?UUID.randomUUID():p.offer();
        boolean resume=p!=null&&(p.decision()==Decision.ACCEPTED||p.decision()==Decision.PENDING&&now()<p.deadline());
        if(!resume)offer=UUID.randomUUID();
        Action action=resume?Action.REBIND:Action.REOFFER;
        Command command=new Command(request,s.epoch(),revision,s.update(),now(),action,id,connection,offer,"",NONE);
        Result result=execute(command);if(!result.outcome().equals("APPLIED"))throw new IOException(result.reason());
        long now=now();Grant grant=new Grant(id,connection,offer,s.epoch(),WaitingFiles.token(),Math.addExact(now,TTL),now);
        grants.put(id,grant);issuanceIds.add(request);
        Map<String,String> response=Map.of("player",id.toString(),"connection",connection.toString(),"offer",offer.toString(),"epoch",Long.toString(s.epoch()),"instance",instance.toString(),"token",grant.token(),"ttlMillis",Long.toString(TTL),"scope","PLAYER_CONSENT_ONLY");
        issued.put(request,new Issued(fingerprint,response));if(issued.size()>128)issued.remove(issued.keySet().iterator().next());return response;
    }
    synchronized Map<String,String> playerStatus(Grant supplied)throws IOException{
        Grant grant=player(supplied.token());if(grant==null)throw new IOException("GRANT_REVOKED");
        grants.put(grant.player(),new Grant(grant.player(),grant.connection(),grant.offer(),grant.epoch(),grant.token(),grant.expires(),now()));
        State s=display(readState());var view=PlayerUpdateView.forPlayer(s,grant.player());var out=new TreeMap<>(summary(s));
        String stage=view.stage().equals("DOWNLOAD")?"CONSENT_RECORDED":view.stage();String message=view.stage().equals("DOWNLOAD")?"Согласие сохранено. Ожидаем подготовки доставки обновления.":view.message();
        out.put("stage",stage);out.put("messageBase64",Base64.getUrlEncoder().withoutPadding().encodeToString(message.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        out.put("decisionRemainingMillis",Long.toString(view.decisionRemainingMillis()));out.put("player",grant.player().toString());out.put("offer",grant.offer().toString());
        out.put("decision",s.players().get(grant.player()).decision().name());out.put("disconnectRequested",Boolean.toString(view.disconnectRequested()));return out;
    }
    synchronized Map<String,String> decide(Grant supplied,Map<String,String> in)throws IOException{
        WaitingFiles.keys(in,"request","revision","decision");Grant grant=player(supplied.token());if(grant==null)throw new IOException("GRANT_REVOKED");
        String decision=in.get("decision");Action action=switch(decision){case "accept"->Action.ACCEPT;case "decline"->Action.DECLINE;default->throw new IOException("INVALID_DECISION");};
        UUID request=WaitingFiles.id(in.get("request"));long revision=WaitingFiles.number(in.get("revision"));State s=readState();
        // Stable command clock permits durable replay of a client request after a lost HTTP response.
        // The first response cache is per service lifetime; old owners revoke every grant.
        var previous=decisions.get(request);String fingerprint=grant.player()+":"+grant.connection()+":"+revision+":"+decision;
        if(previous!=null){if(!previous.fingerprint().equals(fingerprint))throw new IOException("REQUEST_ID_REUSED");return previous.response();}
        if(decisionIds.contains(request))throw new IOException("DECISION_HISTORY_EVICTED");
        if(decisionIds.size()>=4096||decisionQuota.getOrDefault(grant.player(),0)>=8)throw new IOException("DECISION_LIMIT_REVIEW_REQUIRED");
        if(revision>s.revision())throw new IOException("FUTURE_REVISION");
        // Unrelated players may have advanced the global revision since this player polled.
        // The authenticated grant plus per-player phase guards are the decision fence.
        Command c=new Command(request,s.epoch(),s.revision(),s.update(),now(),action,grant.player(),grant.connection(),grant.offer(),"",NONE);
        Result result=execute(c);var out=new TreeMap<>(summary(result.state()));out.put("outcome",result.outcome());out.put("reason",result.reason());
        // Rejections are also cached so a timed-out HTTP response cannot turn a later retry into a fresh choice.
        Map<String,String> response=Map.copyOf(out);decisions.put(request,new Issued(fingerprint,response));decisionIds.add(request);decisionQuota.merge(grant.player(),1,Integer::sum);if(decisions.size()>128)decisions.remove(decisions.keySet().iterator().next());return response;
    }
    final Map<UUID,Integer> decisionQuota=new HashMap<>();
    final LinkedHashMap<UUID,Issued> decisions=new LinkedHashMap<>();final Set<UUID> decisionIds=new HashSet<>();
    final LinkedHashMap<UUID,Issued> controls=new LinkedHashMap<>();final Set<UUID> controlIds=new HashSet<>();
    synchronized Map<String,String> control(Map<String,String> in)throws IOException{
        WaitingFiles.keys(in,"request","revision","action");State s=state();long revision=WaitingFiles.number(in.get("revision"));UUID request=WaitingFiles.id(in.get("request"));
        String fingerprint=revision+":"+in.get("action");Issued old=controls.get(request);if(old!=null){if(!old.fingerprint().equals(fingerprint))throw new IOException("REQUEST_ID_REUSED");return old.response();}
        if(controlIds.contains(request)||controlIds.size()>=4096)throw new IOException("CONTROL_HISTORY_BOUND");
        Action action=switch(in.get("action")){case "resume"->Action.RESUME_TIMERS;case "freeze"->Action.FREEZE_COHORT;case "cancel"->Action.CANCEL;default->throw new IOException("PROTECTED_ACTION_UNAVAILABLE");};
        // No CONFIRM_SWITCH, ACCEPT_RUNTIME, SERVER_READY, OPEN_GATE or install command exists on this transport.
        Command c=new Command(request,s.epoch(),revision,s.update(),now(),action,NONE,NONE,NONE,"",NONE);
        Result result=execute(c);var out=new TreeMap<>(summary(result.state()));out.put("outcome",result.outcome());out.put("reason",result.reason());Map<String,String> response=Map.copyOf(out);controls.put(request,new Issued(fingerprint,response));controlIds.add(request);if(controls.size()>128)controls.remove(controls.keySet().iterator().next());return response;
    }
    synchronized Map<String,String> revoke(Map<String,String> in)throws IOException{
        WaitingFiles.keys(in,"player");UUID id=WaitingFiles.id(in.get("player"));Grant old=grants.remove(id);
        if(old!=null){State s=readState();Player p=s.players().get(id);if(p!=null&&p.online())execute(Command.bound(s,Action.DISCONNECT,id,now()));}
        return Map.of("revoked","true");
    }
    synchronized void maintain()throws IOException{
        healthy();State s=readState();if(s.paused()||s.phase()==Phase.CANCELLED)return;long now=now();
        for(Grant g:List.copyOf(grants.values()))if(g.expires()<=now||now-g.seen()>=HEARTBEAT){
            grants.remove(g.player());s=readState();Player p=s.players().get(g.player());if(p!=null&&p.online())execute(Command.bound(s,Action.DISCONNECT,g.player(),now));
        }
        s=readState();boolean due=!s.frozen()&&now>=s.decisionDeadline()||s.players().values().stream().anyMatch(p->p.decision()==Decision.PENDING&&now>=p.deadline());
        if(due)execute(Command.control(s,Action.TICK,now));
    }
    synchronized Map<String,String> status()throws IOException{return summary(display(state()));}
    private State display(State s){return new State(s.update(),s.packageSha256(),s.policy(),s.epoch(),s.revision(),Math.max(s.now(),now()),s.decisionDeadline(),s.barrierDeadline(),s.phase(),s.paused(),s.frozen(),s.audience(),s.cohort(),s.players(),s.serverRun(),s.serverReady(),s.timeoutApproved(),s.gateOpen());}
    private Map<String,String> summary(State s){var out=new TreeMap<String,String>();out.put("schema","56");out.put("scope","LOCAL_WAITING_CONSENT_ONLY");out.put("instance",instance.toString());out.put("epoch",Long.toString(s.epoch()));out.put("revision",Long.toString(s.revision()));out.put("update",s.update().toString());out.put("packageSha256",s.packageSha256());out.put("paused",Boolean.toString(s.paused()));out.put("phase",s.phase().name());out.put("cohort",Integer.toString(s.cohort().size()));out.put("required",Integer.toString(s.requiredCount()));out.put("ready",Integer.toString(s.readyCount()));out.put("actualClientsVerified","false");out.put("minecraftIdentityVerified","false");out.put("installAuthority","false");out.put("productionLaunchAllowed","false");out.put("restartAllowed","false");out.put("connectAllowed","false");return Map.copyOf(out);}
}
