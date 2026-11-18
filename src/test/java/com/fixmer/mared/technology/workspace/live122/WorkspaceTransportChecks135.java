package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.live122.WorkspaceCodec122;
import java.util.*;
import java.util.function.Consumer;

/** Real production client state machine and codec with an injected connection/clock, no game window. */
public final class WorkspaceTransportChecks135 {
    private static int passed,failed;
    private static final String ROOM=new UUID(135,3).toString(),ACTOR=new UUID(135,1).toString(),STORE="a".repeat(64);
    @FunctionalInterface interface Scenario{void run()throws Exception;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void test(String name,Scenario scenario){try{scenario.run();passed++;System.out.println("PASS "+name);}catch(Throwable failure){failed++;System.out.println("FAIL "+name+": "+failure);}}
    private static final class Host implements WorkspaceClient122.Host135 {
        Object connection=new Object();boolean channel=true,fail;long now;int errors;
        final List<Map<String,String>> sent=new ArrayList<>();
        public Object connection(){return connection;}
        public boolean channel(Object connection){return channel;}
        public void send(byte[] bytes){if(fail)throw new IllegalStateException("send failure");try{sent.add(WorkspaceCodec122.decode(bytes,WorkspaceCodec122.REQUEST));}catch(Exception failure){throw new IllegalArgumentException(failure);}}
        public void execute(Runnable action){action.run();}
        public long now(){return now;}
        public void failed(Exception failure){errors++;}
    }
    private static final class Client implements AutoCloseable {
        final Host host;final WorkspaceClient122 client;
        Client(){this(new Host());}
        Client(Host host){this.host=host;client=new WorkspaceClient122(host);client.attach();}
        void reply(Map<String,String> request,Map<String,String> values)throws Exception{var answer=new TreeMap<>(values);answer.put("request",request.get("request"));client.receive135(WorkspaceCodec122.encode(answer,WorkspaceCodec122.RESPONSE));}
        public void close(){client.detach();}
    }
    private static Map<String,String> view(){return WorkspaceClient122.request("VIEW",ROOM,Map.of());}
    public static void main(String[] args)throws Exception{
        test("disconnected send completes its callback",()->{try(var c=new Client()){c.host.connection=null;var results=new ArrayList<String>();c.client.send(view(),d->results.add(d.get("result")));require(results.equals(List.of("DISCONNECTED"))&&!c.client.busy(),"Caller left waiting");}});
        test("invalid queued request cannot cancel an in-flight request",()->{try(var c=new Client()){var first=view();var results=new ArrayList<String>();c.client.send(first,d->results.add("first:"+d.get("result")));c.client.send(Map.of(),d->results.add("invalid:"+d.get("result")));require(c.client.busy()&&c.host.sent.size()==1,"Valid request cancelled");c.reply(first,Map.of("result","OK"));require(results.equals(List.of("invalid:INVALID","first:OK")),"Callback outcomes wrong");}});
        test("queue overflow reports rejection without losing accepted requests",()->{try(var c=new Client()){var outcomes=new ArrayList<String>();for(int i=0;i<18;i++)c.client.send(view(),d->outcomes.add(d.get("result")));require(outcomes.equals(List.of("BUSY"))&&c.client.busy(),"Overflow caller never completed");for(int i=0;i<17;i++)c.reply(c.host.sent.get(i),Map.of("result","OK"));require(outcomes.size()==18&&Collections.frequency(outcomes,"OK")==17&&!c.client.busy(),"Queue dropped or duplicated callback");}});
        test("disconnect completes pending and queued callbacks once",()->{try(var c=new Client()){var outcomes=new ArrayList<String>();c.client.send(view(),d->outcomes.add(d.get("result")));c.client.send(view(),d->outcomes.add(d.get("result")));c.host.connection=null;c.client.tick();c.client.tick();require(outcomes.equals(List.of("DISCONNECTED","DISCONNECTED"))&&!c.client.busy(),"Disconnect lost/duplicated callbacks");}});
        test("non-idempotent timeout reports unknown and advances queue",()->{try(var c=new Client()){var outcomes=new ArrayList<String>();c.client.send(WorkspaceClient122.request("CREATE","",Map.of("text","Map")),d->outcomes.add(d.get("result")));c.client.send(view(),d->outcomes.add(d.get("result")));c.host.now=3_000_000_000L;c.client.tick();require(outcomes.equals(List.of("UNKNOWN"))&&c.host.sent.size()==2,"Unknown result was swallowed or retried mutation");c.reply(c.host.sent.get(1),Map.of("result","OK"));require(outcomes.equals(List.of("UNKNOWN","OK")),"Queue did not resume");}});
        test("read retries are bounded and complete their callback",()->{try(var c=new Client()){var outcomes=new ArrayList<String>();c.client.send(view(),d->outcomes.add(d.get("result")));for(int i=1;i<=4;i++){c.host.now=i*3_000_000_000L;c.client.tick();}require(c.host.sent.size()==4&&outcomes.equals(List.of("UNKNOWN"))&&!c.client.busy(),"Read never reached terminal outcome");}});
        test("throwing callback does not stop another consumer",()->{try(var c=new Client()){var first=view();var outcomes=new ArrayList<String>();c.client.send(first,d->{throw new IllegalStateException("callback failure");});c.client.send(view(),d->outcomes.add(d.get("result")));c.reply(first,Map.of("result","OK"));require(c.host.errors==1&&c.host.sent.size()==2,"Callback exception halted queue");c.reply(c.host.sent.get(1),Map.of("result","OK"));require(outcomes.equals(List.of("OK")),"Next callback not completed");}});
        test("foreign response cannot complete the current request",()->{try(var c=new Client()){var first=view();var outcomes=new ArrayList<String>();c.client.send(first,d->outcomes.add(d.get("result")));c.reply(view(),Map.of("result","OK"));require(c.client.busy()&&outcomes.isEmpty(),"Foreign reply accepted");c.reply(first,Map.of("result","OK"));require(outcomes.equals(List.of("OK")),"Matching reply not delivered");}});
        test("wrong project reply produces an explicit terminal error",()->{try(var c=new Client()){var first=view();var outcomes=new ArrayList<String>();c.client.send(first,d->outcomes.add(d.get("result")));c.reply(first,Map.of("result","OK","project",new UUID(1,3).toString()));require(outcomes.equals(List.of("INVALID"))&&!c.client.busy(),"Wrong project left callback waiting");}});
        test("identity change suspends connection and completes queued callers",()->{try(var c=new Client()){var first=view();c.client.send(first,d->{});c.reply(first,Map.of("result","OK","actor",ACTOR,"store",STORE));var second=view();var outcomes=new ArrayList<String>();c.client.send(second,d->outcomes.add(d.get("result")));c.client.send(view(),d->outcomes.add(d.get("result")));c.reply(second,Map.of("result","OK","actor",new UUID(2,1).toString(),"store",STORE));require(outcomes.equals(List.of("IDENTITY_CHANGED","IDENTITY_CHANGED"))&&!c.client.connected(),"Identity change did not fail closed");}});
        test("store change suspends connection and notifies caller",()->{try(var c=new Client()){var first=view();c.client.send(first,d->{});c.reply(first,Map.of("result","OK","actor",ACTOR,"store",STORE));var second=view();var outcomes=new ArrayList<String>();c.client.send(second,d->outcomes.add(d.get("result")));c.reply(second,Map.of("result","OK","actor",ACTOR,"store","b".repeat(64)));require(outcomes.equals(List.of("STORE_CHANGED"))&&!c.client.connected(),"Store change ignored");}});
        test("event callback failure cannot destroy an unrelated request",()->{try(var c=new Client()){var first=view();c.client.send(first,d->{});c.reply(first,Map.of("result","OK","actor",ACTOR,"store",STORE));c.client.events130(d->{throw new IllegalStateException("event failure");});var second=view();var outcomes=new ArrayList<String>();c.client.send(second,d->outcomes.add(d.get("result")));c.client.receive135(WorkspaceCodec122.encode(Map.of("event130","catalog","actor",ACTOR,"store",STORE,"project",ROOM,"result","OK"),WorkspaceCodec122.RESPONSE));require(c.client.busy()&&outcomes.isEmpty()&&c.host.errors==1,"Event exception cancelled request");c.reply(second,Map.of("result","OK"));require(outcomes.equals(List.of("OK")),"Pending callback disappeared");}});
        test("BUSY retries the exact request after backoff",()->{try(var c=new Client()){var first=view();var outcomes=new ArrayList<String>();c.client.send(first,d->outcomes.add(d.get("result")));c.reply(first,Map.of("result","BUSY"));c.host.now=499_999_999;c.client.tick();require(c.host.sent.size()==1&&outcomes.isEmpty(),"BUSY retried too soon");c.host.now++;c.client.tick();require(c.host.sent.size()==2&&c.host.sent.get(0).equals(c.host.sent.get(1)),"BUSY changed request identity");c.reply(first,Map.of("result","OK"));require(outcomes.equals(List.of("OK")),"BUSY emitted premature callback");}});
        test("connection replacement cannot reuse the pinned session",()->{try(var c=new Client()){var outcomes=new ArrayList<String>();c.client.send(view(),d->outcomes.add(d.get("result")));c.host.connection=new Object();c.client.tick();require(outcomes.equals(List.of("DISCONNECTED"))&&!c.client.connected(),"New connection inherited old transport");}});
        test("wire send failure completes caller without a stuck pending state",()->{try(var c=new Client()){var outcomes=new ArrayList<String>();c.host.fail=true;c.client.send(view(),d->outcomes.add(d.get("result")));require(outcomes.equals(List.of("INVALID"))&&!c.client.busy(),"Send exception left request pending");}});
        test("connection cleanup detaches abandoned transports without ticking their screen",()->{try(var abandoned=new Client();var active=new Client()){var outcomes=new ArrayList<String>();abandoned.client.send(view(),d->outcomes.add(d.get("result")));WorkspaceClient122.detachDisconnected135(active.host.connection);require(outcomes.equals(List.of("DISCONNECTED"))&&!abandoned.client.busy(),"Abandoned callback retained");var sent=new ArrayList<String>();abandoned.client.send(view(),d->sent.add(d.get("result")));require(sent.equals(List.of("DISCONNECTED")),"Old listener remained attached");active.client.send(view(),d->{});require(active.client.busy(),"Current session was detached");}});
        test("model and text transports serialize one connection fairly",()->{
            Host left=new Host(),right=new Host();right.connection=left.connection;
            try(var a=new Client(left);var b=new Client(right)){
                var first=view();var next=view();var other=view();var completed=new ArrayList<String>();
                a.client.send(first,d->{completed.add("a");a.client.send(next,n->completed.add("next"));});
                b.client.send(other,d->completed.add("b"));
                require(left.sent.size()==1&&right.sent.isEmpty()&&b.client.busy(),"Parallel requests reached one actor");
                a.reply(first,Map.of("result","OK"));require(left.sent.size()==1,"Chunk stream bypassed a waiting client");
                b.client.tick();require(right.sent.size()==1,"Waiting client did not get its turn");
                b.reply(other,Map.of("result","OK"));a.client.tick();require(left.sent.size()==2,"Original stream did not resume");
                a.reply(next,Map.of("result","OK"));require(completed.equals(List.of("a","b","next")),"Callbacks lost or reordered");
            }
        });
        System.out.println("Transport135 scenarios passed="+passed+" failed="+failed);if(failed>0)throw new AssertionError("Transport failures: "+failed);
    }
}
