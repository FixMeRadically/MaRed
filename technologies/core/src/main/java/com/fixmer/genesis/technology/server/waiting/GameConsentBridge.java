package com.fixmer.genesis.technology.server.waiting;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Trusted server-side proxy. Never send controller/grant credentials to Minecraft clients.
 * The caller must obtain identity from the authenticated game connection, not a packet field.
 * All file/crypto/HTTP work executes on the bounded worker; callbacks execute on that worker too.
 */
public final class GameConsentBridge implements AutoCloseable {
    public enum Identity { ONLINE_ACCOUNT, INTEGRATED_OWNER }
    public record Session(UUID player,UUID connection,long sequence,Identity identity) {
        public Session {Objects.requireNonNull(identity);Objects.requireNonNull(player);Objects.requireNonNull(connection);if(player.equals(new UUID(0,0))||connection.equals(new UUID(0,0)))throw new IllegalArgumentException("zero UUID");if(sequence<1)throw new IllegalArgumentException("sequence");}
    }
    public record View(UUID instance,UUID update,String packageSha256,UUID offer,long revision,long remainingMillis,
                       String decision,String phase,boolean paused,boolean disconnectRequested) {
        public View {if(!packageSha256.matches("[a-f0-9]{64}")||revision<0||remainingMillis<0||remainingMillis>300000||!Set.of("PENDING","ACCEPTED","DECLINED","EXPIRED").contains(decision)||!Set.of("OFFERING","WAITING","CANCELLED","SUSPENDED").contains(phase))throw new IllegalArgumentException("Invalid consent view");}
    }
    public record Reply(View view,String code) {public boolean success(){return view!=null;}}
    private final Path root;private final UUID run=UUID.randomUUID();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"Genesis-game-consent");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private Map<String,String> endpoint;private String token;private FileChannel lease;private FileLock lock;private volatile boolean closed;
    private final Map<UUID,Map<String,String>> decisionIntents=new HashMap<>();
    public GameConsentBridge(Path root){this.root=root.toAbsolutePath().normalize();}
    private void open()throws Exception{
        if(endpoint!=null)return;WaitingFiles.safe(root);
        if(!Files.isRegularFile(root.resolve("prepared.v57"),LinkOption.NOFOLLOW_LINKS))throw new IOException("PREPARED_ROOT_REQUIRED");
        synchronized(this){if(closed)throw new IOException("BRIDGE_CLOSED");if(lease==null){
            Path path=WaitingFiles.safe(root.resolve("game.lock"));lease=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            WaitingFiles.privateMode(path,false);lock=lease.tryLock();if(lock==null)throw new IOException("GAME_ACTOR_ALREADY_RUNNING");
        }}
        var ep=WaitingFiles.read(root.resolve("endpoint.v56"));var key=WaitingFiles.read(root.resolve("controller.key"));
        if(!Objects.equals(ep.get("instance"),key.get("instance")))throw new IOException("OWNER_CHANGED");
        WaitingClient.request(ep,key.get("token"),"/v1/controller/game-open",Map.of("run",run.toString()));endpoint=ep;token=key.get("token");
    }
    private Map<String,String> event(Session s,String action,UUID request,UUID offer,long revision,String decision)throws Exception{
        open();return WaitingClient.request(endpoint,token,"/v1/controller/game-event",Map.of("run",run.toString(),"player",s.player().toString(),"connection",s.connection().toString(),"sequence",Long.toString(s.sequence()),"action",action,"request",request.toString(),"offer",offer.toString(),"revision",Long.toString(revision),"decision",decision));
    }
    private View poll(Session s)throws Exception{
        var m=event(s,"status",UUID.randomUUID(),s.connection(),0,"none");
        if(!s.player().toString().equals(m.get("player")))throw new IOException("PLAYER_BINDING_CHANGED");
        return new View(WaitingFiles.id(m.get("instance")),WaitingFiles.id(m.get("update")),m.get("packageSha256"),WaitingFiles.id(m.get("offer")),WaitingFiles.number(m.get("revision")),WaitingFiles.number(m.get("decisionRemainingMillis")),m.get("decision"),m.get("phase"),Boolean.parseBoolean(m.get("paused")),Boolean.parseBoolean(m.get("disconnectRequested")));
    }
    private interface Work {View run()throws Exception;}
    private boolean submit(Work task,Consumer<Reply> callback){
        if(closed)return false;
        try{worker.execute(()->{Reply reply;try{if(closed)return;reply=new Reply(task.run(),"OK");}catch(Exception bad){reply=new Reply(null,"UNAVAILABLE_REVIEW_REQUIRED");}if(!closed)callback.accept(reply);});return true;}
        catch(RejectedExecutionException full){return false;}
    }
    public boolean bind(Session session,Consumer<Reply> callback){return submit(()->{event(session,"bind",UUID.randomUUID(),session.connection(),0,"none");return poll(session);},callback);}
    public boolean status(Session session,Consumer<Reply> callback){return submit(()->poll(session),callback);}
    public boolean decide(Session session,View offered,UUID request,boolean accept,Consumer<Reply> callback){
        return submit(()->{
            var body=Map.of("player",session.player().toString(),"connection",session.connection().toString(),"sequence",Long.toString(session.sequence()),"offer",offered.offer().toString(),"revision",Long.toString(offered.revision()),"decision",accept?"accept":"decline","update",offered.update().toString(),"sha",offered.packageSha256());
            var prior=decisionIntents.get(request);
            if(prior!=null&&!prior.equals(body))throw new IOException("REQUEST_ID_REUSED");
            if(prior==null){if(decisionIntents.size()>=512)throw new IOException("LOCAL_INTENT_BOUND");decisionIntents.put(request,body);}
            // Re-read own binding; client-supplied UUID/package never establishes identity or authority.
            View current=poll(session);
            if(!current.update().equals(offered.update())||!current.packageSha256().equals(offered.packageSha256())||!current.offer().equals(offered.offer()))throw new IOException("OFFER_CHANGED");
            var result=event(session,"decision",request,offered.offer(),offered.revision(),accept?"accept":"decline");
            if(!Set.of("APPLIED","REPLAY").contains(result.get("outcome")))throw new IOException("DECISION_REFUSED");return poll(session);
        },callback);
    }
    /** Logout must use a NEW higher server sequence. Old async work cannot pass its tombstone. */
    public boolean logout(Session tombstone){return submit(()->{event(tombstone,"logout",UUID.randomUUID(),tombstone.connection(),0,"none");return null;},reply->{});}
    public int queued(){return worker.getQueue().size();}
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        // Return to the server thread immediately. Queued stale work is discarded; an in-flight
        // request is followed by a terminal actor fence. If transport is down, heartbeat expiry
        // remains the fail-closed fallback, never a fabricated immediate acknowledgement.
        worker.getQueue().clear();
        try{worker.execute(()->{
            try{if(endpoint!=null)WaitingClient.request(endpoint,token,"/v1/controller/game-close",Map.of("run",run.toString()));}
            catch(Exception unavailable){}finally{releaseLease();}
        });worker.shutdown();}catch(RejectedExecutionException unavailable){worker.shutdownNow();releaseLease();}
    }
    private synchronized void releaseLease(){
        try{if(lock!=null)lock.release();}catch(IOException ignored){}try{if(lease!=null)lease.close();}catch(IOException ignored){}
    }
}
