package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.live122.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Consumer;

/** Connection-pinned serial transport. Terminal outcomes complete callbacks exactly once. */
public final class WorkspaceClient122 {
    /** Small host boundary makes retry, cancellation and callback behavior testable without a game window. */
    public interface Host135 {
        Object connection();
        boolean channel(Object connection);
        void send(byte[] bytes);
        void execute(Runnable action);
        long now();
        void failed(Exception failure);
    }
    private static final class MinecraftHost135 implements Host135 {
        public Object connection(){return Minecraft.getInstance().getConnection();}
        public boolean channel(Object connection){return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel((net.neoforged.neoforge.common.extensions.ICommonPacketListener)connection,WorkspacePayloads122.Request.TYPE.id());}
        public void send(byte[] bytes){PacketDistributor.sendToServer(new WorkspacePayloads122.Request(bytes));}
        public void execute(Runnable action){Minecraft.getInstance().execute(action);}
        public long now(){return System.nanoTime();}
        public void failed(Exception failure){com.fixmer.mared.Mared.LOGGER.warn("[Workspace135] Transport or callback failed",failure);}
    }
    private static final Set<WorkspaceClient122> attached135=Collections.newSetFromMap(new IdentityHashMap<>());
    static void detachDisconnected135(Object current){for(var client:new ArrayList<>(attached135))if(current==null||client.connection!=current){client.fail135("DISCONNECTED",true);client.detach();}}
    private final boolean pointerOnly149;
    private final Host135 host;
    private final Object connection;
    private final Consumer<WorkspacePayloads122.Response> sink;
    private final WorkspaceOutbox130 outbox130=new WorkspaceOutbox130();
    private Consumer<Map<String,String>> events130=d->{};
    private Map<String,String> pending;
    private Consumer<Map<String,String>> callback;
    private WorkspaceRequest123 retry;
    private boolean attached,suspended;
    private String actor,store;
    public String result="IDLE";

    public WorkspaceClient122(){this(new MinecraftHost135());}
    public WorkspaceClient122(boolean pointerOnly){this(new MinecraftHost135(),pointerOnly);}
    public WorkspaceClient122(Host135 host){this(host,false);}
    public WorkspaceClient122(Host135 host,boolean pointerOnly){pointerOnly149=pointerOnly;this.host=Objects.requireNonNull(host);connection=host.connection();sink=p->host.execute(()->receive135(p.bytes()));}
    public void events130(Consumer<Map<String,String>> events){events130=Objects.requireNonNull(events);}
    public void attach(){attached=true;attached135.add(this);WorkspacePayloads122.attach131(sink);}
    public void detach(){attached=false;attached135.remove(this);WorkspacePayloads122.detach131(sink);pending=null;callback=null;retry=null;outbox130.clear();}
    public boolean connected(){Object current=host.connection();return !suspended&&current!=null&&current==connection&&host.channel(current);}
    public boolean readyForSend152(){return connected()&&!busy()&&!blocked147();}
    public boolean busy(){return pending!=null||outbox130.size()>0;}
    private static long order147;
    private long sent147,pendingAt149,editRtt149=-1;
    public long editConfirmationMillis149(){return editRtt149<0?-1:editRtt149/1_000_000L;}
    private boolean blocked147(){
        if(pointerOnly149)return false;
        for(var peer:attached135)if(peer!=this&&!peer.pointerOnly149&&peer.connection==connection&&peer.connected()&&(peer.pending!=null||peer.outbox130.size()>0&&peer.sent147<sent147))return true;
        return false;
    }
    private boolean structural130(Map<String,String> request){return Set.of("REMOVE_WORKSPACE","NEW","DELETE_REVIEW","DELETE","RENAME","RESTORE","COMPACT","TEMPLATE126","IMPORT_TEXT126").contains(request.get("op"));}
    public boolean blocksEdits(){return pending!=null&&structural130(pending)||outbox130.any(this::structural130);}
    private void notify135(Consumer<Map<String,String>> done,Map<String,String> answer){if(done!=null)try{done.accept(answer);}catch(RuntimeException failure){host.failed(failure);}}
    private void fail135(String code,boolean all){
        var done=callback;pending=null;callback=null;retry=null;result=code;
        var waiting=new ArrayList<WorkspaceOutbox130.Entry>();
        if(all)for(var entry=outbox130.poll();entry!=null;entry=outbox130.poll())waiting.add(entry);
        notify135(done,Map.of("result",code));
        for(var entry:waiting)notify135(entry.callback(),Map.of("result",code));
    }
    public void send(Map<String,String> request,Consumer<Map<String,String>> done){
        if(!attached||!connected()){result="DISCONNECTED";notify135(done,Map.of("result",result));return;}
        if(pointerOnly149&&!Set.of("POINTER149","CARET151").contains(request.get("op"))){notify135(done,Map.of("result","INVALID"));return;}
        try{WorkspaceHub122.request(request);}catch(RuntimeException invalid){notify135(done,Map.of("result","INVALID"));return;}
        if(pending!=null||outbox130.size()>0||blocked147()){
            if(!outbox130.offer(request,done))notify135(done,Map.of("result","BUSY"));
            return;
        }
        start147(request,done);
    }
    public boolean stream154(Map<String,String> request){
        if(!pointerOnly149||!attached||!connected()||!"POINTER149".equals(request.get("op"))||!"true".equals(request.get("stream154")))return false;
        long now=host.now();if(streamSent154&&now-streamAt154<40_000_000L)return false;
        try{WorkspaceHub122.request(request);host.send(WorkspaceCodec122.encode(request,WorkspaceCodec122.REQUEST));streamSent154=true;streamAt154=now;return true;}
        catch(Exception failure){host.failed(failure);return false;}
    }
    private boolean streamSent154;private long streamAt154;
    private void start147(Map<String,String> request,Consumer<Map<String,String>> done){
        try{request=com.fixmer.mared.technology.permissions.MaopClient153.sign(request);}catch(Exception failure){notify135(done,Map.of("result","AUTH153_REQUIRED"));return;}sent147=++order147;pendingAt149=host.now();pending=Map.copyOf(request);callback=done;retry=new WorkspaceRequest123(pending,host.now());result="WAITING";
        try{transmit();}catch(Exception failure){fail135("INVALID",false);host.failed(failure);}
    }
    private void transmit()throws java.io.IOException{host.send(WorkspaceCodec122.encode(pending,WorkspaceCodec122.REQUEST));}
    private void pump130(){if(attached&&connected()&&pending==null&&!blocked147()){var next=outbox130.poll();if(next!=null)start147(next.request(),next.callback());}}
    public void execute149(Runnable action){host.execute(action);}
    public void tick(){
        if(!connected()){fail135("DISCONNECTED",true);return;}
        if(pending==null){pump130();return;}
        if(pointerOnly149){if(host.now()-pendingAt149>=250_000_000L)fail135("UNKNOWN",false);return;}
        var action=retry.tick(host.now());
        if(action==WorkspaceRequest123.Action.UNKNOWN){fail135("UNKNOWN",false);pump130();return;}
        if(action==WorkspaceRequest123.Action.SEND)try{transmit();result="RETRY";}catch(Exception failure){fail135("DISCONNECTED",true);host.failed(failure);}
    }
    void receive135(byte[] bytes){
        if(!attached||!connected())return;
        try{
            var answer=WorkspaceCodec122.decode(bytes,WorkspaceCodec122.RESPONSE);
            if(answer.containsKey("event130")){
                if(actor!=null&&store!=null&&actor.equals(answer.get("actor"))&&store.equals(answer.get("store")))notify135(events130,answer);
                return;
            }
            if(pending==null||!pending.get("request").equals(answer.get("request")))return;
            if("BUSY".equals(answer.get("result"))){if(pointerOnly149){fail135("BUSY",false);return;}retry.busy(host.now());result="RETRY";return;}
            if(answer.containsKey("actor")){
                if(actor!=null&&!actor.equals(answer.get("actor"))){suspended=true;fail135("IDENTITY_CHANGED",true);return;}
                actor=answer.get("actor");
            }
            if(answer.containsKey("store")){
                if(store!=null&&!store.equals(answer.get("store"))){suspended=true;fail135("STORE_CHANGED",true);return;}
                store=answer.get("store");
            }
            if(!pending.get("project").isEmpty()&&answer.containsKey("project")&&!pending.get("project").equals(answer.get("project")))throw new IllegalArgumentException("PROJECT");
            if("EDIT".equals(pending.get("op"))&&"OK".equals(answer.get("result"))){long elapsed=host.now()-pendingAt149;if(elapsed>=0)editRtt149=editRtt149<0?elapsed:(long)(editRtt149*.75+elapsed*.25);}
            var done=callback;pending=null;callback=null;retry=null;result=answer.getOrDefault("result","INVALID");notify135(done,answer);pump130();
        }catch(Exception failure){fail135("INVALID",false);host.failed(failure);}
    }
    public static Map<String,String> request(String op,String project,Map<String,String> fields){var request=new TreeMap<>(fields);request.put("request",UUID.randomUUID().toString());request.put("op",op);request.put("project",project);return Map.copyOf(request);}
}
