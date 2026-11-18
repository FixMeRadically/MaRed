package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Exercises the same encoded requests, push frames and journals used by the Minecraft adapter. */
public final class WorkspaceRealtimeChecks130 {
    private static final UUID O=new UUID(130,1),D=new UUID(130,2),V=new UUID(130,3);
    private static int checks;
    interface Work{void run()throws Exception;}
    static void ok(boolean value){if(!value)throw new AssertionError("Realtime130 #"+checks);checks++;}
    static void rejects(Work work)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Expected rejection #"+checks);}
    static Map<String,String> wire(Map<String,String> data,int max)throws Exception{return WorkspaceCodec122.decode(WorkspaceCodec122.encode(data,max),max);}
    static Map<String,String> call(WorkspaceHub122 h,UUID actor,String room,String op,Map<String,String> fields)throws Exception{
        var q=new TreeMap<>(fields);q.put("op",op);q.put("project",room);q.put("request",UUID.randomUUID().toString());return wire(h.execute(actor,true,"Player",wire(q,WorkspaceCodec122.REQUEST)),WorkspaceCodec122.RESPONSE);
    }
    static String rev(WorkspaceHub122 h,String p)throws Exception{return call(h,O,p,"VIEW",Map.of()).get("revision");}
    static void member(WorkspaceHub122 h,String p,UUID who,String role,String prefix,String hidden)throws Exception{
        call(h,O,p,"MEMBER",Map.of("target",who.toString(),"role",role,"prefix",prefix,"deny","","hidden",hidden,"rev",rev(h,p)));
    }
    static WorkspaceReplica122 replica(WorkspaceHub122 h,UUID actor,String p)throws Exception{
        var data=call(h,actor,p,"OPEN",Map.of("path","src/A.java"));var envelope=new TreeMap<>(data);envelope.put("request",UUID.randomUUID().toString());envelope.put("online",O+"|Owner\n"+D+"|Developer");ok(envelope.size()>24);ok(wire(envelope,WorkspaceCodec122.RESPONSE).equals(envelope));var r=new WorkspaceReplica122(actor,WorkspaceCodec122.document(data.get("document")));grant(r,data);return r;
    }
    static void grant(WorkspaceReplica122 r,Map<String,String> d){r.grant(d.get("lease"),Long.parseLong(d.get("first")),Long.parseLong(d.get("last")));}
    static void prepare(WorkspaceHub122 h,UUID actor,String p,WorkspaceReplica122 r,String text)throws Exception{
        for(int tries=0;!r.prepare(text);tries++){if(tries>8)throw new AssertionError("Lease allocation");grant(r,call(h,actor,p,"LEASE",Map.of("path","src/A.java")));}ok(r.text().equals(text));
    }
    static void deliver(WorkspaceHub122 h,UUID actor,String p,WorkspaceReplica122 r,Map<UUID,WorkspaceReplica122> peers)throws Exception{
        var pending=r.first();var q=Map.of("op","EDIT","project",p,"request",pending.request().toString(),"path","src/A.java","batch",pending.batch(),"lease",pending.lease());
        var ack=wire(h.execute(actor,false,"Player",wire(q,WorkspaceCodec122.REQUEST)),WorkspaceCodec122.RESPONSE);
        // Simulate a duplicate delivery before the response reaches the client.
        h.execute(actor,false,"Player",q);
        r.acknowledge(pending.request(),WorkspaceCodec122.document(ack.get("document")));
        for(var frame:h.events130(actor,p,"EDIT","src/A.java")){
            var data=wire(frame.data(),WorkspaceCodec122.RESPONSE);ok(data.get("actor").equals(frame.recipient().toString()));ok(!data.containsKey("request"));
            if("document".equals(data.get("event130"))&&peers.containsKey(frame.recipient()))peers.get(frame.recipient()).merge(WorkspaceCodec122.document(data.get("document")));
        }
    }
    static Map<String,String> poll(WorkspaceHub122 h,UUID actor,String p,WorkspaceReplica122 r,UUID session,long sequence,int color,boolean editing)throws Exception{
        var ids=r.document().visibleIds();var end=ids.isEmpty()?CollaborativeDocument121.ROOT:ids.get(ids.size()-1);
        end=r.confirmedAnchor130(end);var fields=Map.of("path","src/A.java","anchor",WorkspaceCodec122.id(end),"selection",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"color",Integer.toString(color),"session",session.toString(),"sequence",Long.toString(sequence),"editing",Boolean.toString(editing));
        return call(h,actor,p,"POLL",fields);
    }
    static void scheduler(){
        var scheduler=new WorkspaceSchedule130();var counts=new EnumMap<WorkspaceSchedule130.Lane,Integer>(WorkspaceSchedule130.Lane.class);var last=new EnumMap<WorkspaceSchedule130.Lane,Long>(WorkspaceSchedule130.Lane.class);
        for(long now=0;now<10_000_000_000L;now+=20_000_000L){var lane=scheduler.next(now,true,true,true,true,true);if(lane==null)continue;counts.merge(lane,1,Integer::sum);Long previous=last.put(lane,now);if(previous!=null&&lane==WorkspaceSchedule130.Lane.PRESENCE)ok(now-previous<300_000_000L);if(previous!=null&&lane==WorkspaceSchedule130.Lane.CATALOG)ok(now-previous<2_200_000_000L);if(previous!=null&&lane==WorkspaceSchedule130.Lane.BACKGROUND)ok(now-previous<1_500_000_000L);}
        ok(counts.get(WorkspaceSchedule130.Lane.EDIT)>=70);ok(counts.get(WorkspaceSchedule130.Lane.PRESENCE)>=40);ok(counts.get(WorkspaceSchedule130.Lane.CATALOG)>=4);ok(counts.get(WorkspaceSchedule130.Lane.SOURCE)>=4);ok(counts.get(WorkspaceSchedule130.Lane.BACKGROUND)>=8);
        ok(scheduler.next(20_000_000_000L,false,false,false,false,false)==null);
    }
    static void outbox(){
        var queue=new WorkspaceOutbox130();var ids=new ArrayList<String>();
        for(int i=0;i<16;i++){String id=UUID.randomUUID().toString();ids.add(id);ok(queue.offer(Map.of("op","LIST","project","","request",id),d->{}));}
        ok(!queue.offer(Map.of("op","LIST","project","","request",UUID.randomUUID().toString()),d->{}));
        for(String id:ids)ok(queue.poll().request().get("request").equals(id));ok(queue.poll()==null);
        queue.offer(Map.of("op","LIST","project","","request",UUID.randomUUID().toString()),d->{});queue.clear();ok(queue.size()==0);
    }
    static void ttl(){
        var clock=new AtomicLong();var policy=new WorkspacePolicy121(O);policy.grant(O,0,D,new WorkspacePolicy121.Member(WorkspacePolicy121.Role.VIEWER,Set.of(),Set.of(),List.of()));
        var presence=new WorkspacePresence121(clock::get,1000);presence.update(policy,O,"src/A.java","Owner",0x123456,CollaborativeDocument121.ROOT,CollaborativeDocument121.ROOT,1,true);ok(presence.visible(policy,D).size()==1);
        clock.set(1001);ok(presence.visible(policy,D).isEmpty());presence.newSession(O);presence.update(policy,O,"src/A.java","Owner",1,CollaborativeDocument121.ROOT,CollaborativeDocument121.ROOT,1);presence.disconnect(O);ok(presence.visible(policy,D).isEmpty());
    }
    private static void failingTransientConsumer(){
        var jobs=new ArrayDeque<Runnable>();
        var values=new ArrayList<Integer>();
        try(var lane=new LatestLane154<String,Integer>(jobs::add,4,value->{
            if(value==1)throw new IllegalStateException("consumer fixture");
            values.add(value);
        },error->{throw new IllegalStateException("reporter fixture");})){
            ok(lane.offer("first",1));
            ok(lane.offer("second",2));
            jobs.removeFirst().run();
            ok(values.equals(List.of(2)));
            ok(lane.offer("third",3));
            jobs.removeFirst().run();
            ok(values.equals(List.of(2,3)));
        }
    }
    public static void main(String[] args)throws Exception{
        failingTransientConsumer();
        scheduler();ttl();outbox();var tooMany=new TreeMap<String,String>();for(int i=0;i<=WorkspaceCodec122.MAX_FIELDS;i++)tooMany.put("key"+i,"value");rejects(()->wire(tooMany,WorkspaceCodec122.RESPONSE));Path root=Files.createTempDirectory("workspace-realtime130-");String project;
        try{
            var watchNow=new AtomicLong(1);try(var h=new WorkspaceHub122(root.resolve("hub"),watchNow::get)){
                project=call(h,O,"","CREATE_SCRIPT",Map.of("source","Existing","data",WorkspaceProject126.pack(Map.of("src/A.java","","private/Secret.java","hidden")))).get("project");String p=project;
                member(h,p,D,"DEVELOPER","","");member(h,p,V,"VIEWER","private","CODE");
                var a=replica(h,O,p);var b=replica(h,D,p);call(h,V,p,"OPEN",Map.of("path","src/A.java"));var models=Map.of(O,a,D,b);UUID os=UUID.randomUUID(),ds=UUID.randomUUID();
                prepare(h,O,p,a,"a😀");prepare(h,O,p,a,a.text()+"b");ok(a.pendingCount130()>=2);
                var checkpoint=a.checkpoint(a.text());String fingerprint=call(h,O,p,"VIEW",Map.of()).get("store");var cache=new WorkspaceCache122(root.resolve("cache"));cache.save(O,fingerprint,UUID.fromString(p),"src/A.java",checkpoint);a=WorkspaceReplica122.restore(O,cache.load(O,fingerprint,UUID.fromString(p),"src/A.java"));ok(a.pendingCount130()>=2);ok(a.text().equals("a😀b"));models=Map.of(O,a,D,b);
                // Cursor anchors are valid even before newly typed CRDT nodes have reached the server.
                poll(h,O,p,a,os,1,0x123456,true);
                var presence=h.events130(O,p,"POLL","src/A.java");var toD=presence.stream().filter(e->e.recipient().equals(D)).findFirst().orElseThrow();
                ok(toD.data().get("event130").equals("presence"));ok(toD.data().get("peers").contains(O+"|src/A.java|Player *|1193046|"));ok(!toD.data().containsKey("document"));
                prepare(h,D,p,b,"XY");prepare(h,D,p,b,b.text()+"Z");
                while(a.pending()||b.pending()){if(a.pending())deliver(h,O,p,a,models);if(b.pending())deliver(h,D,p,b,models);}
                ok(a.text().equals(b.text()));ok(a.text().contains("a😀b"));ok(a.text().contains("XYZ"));
                var random=new Random(130);
                for(int round=0;round<40;round++){
                    prepare(h,O,p,a,a.text()+"[o"+round+"]");prepare(h,O,p,a,a.text()+"!");
                    prepare(h,D,p,b,b.text()+"[d"+round+"]");prepare(h,D,p,b,b.text()+"?");
                    poll(h,O,p,a,os,round+2,0xABCDEF,true);poll(h,D,p,b,ds,round+1,0xF08080,true);
                    while(a.pending()||b.pending()){boolean first=random.nextBoolean();if(first&&a.pending()||!b.pending())deliver(h,O,p,a,models);else deliver(h,D,p,b,models);}
                    ok(a.text().equals(b.text()));ok(a.text().contains("[o"+round+"]!"));ok(a.text().contains("[d"+round+"]?"));
                }
                // Overlapping replacements/deletions must also converge while subsequent typing is queued.
                String shared=a.text();int cut=shared.offsetByCodePoints(0,3);
                prepare(h,O,p,a,"<owner>"+shared.substring(cut));prepare(h,O,p,a,a.text()+"tailO");
                int from=shared.offsetByCodePoints(0,1),to=shared.offsetByCodePoints(0,4);
                prepare(h,D,p,b,shared.substring(0,from)+"<developer>"+shared.substring(to));prepare(h,D,p,b,b.text()+"tailD");
                while(a.pending()||b.pending()){if(b.pending())deliver(h,D,p,b,models);if(a.pending())deliver(h,O,p,a,models);}
                ok(a.text().equals(b.text()));ok(a.text().contains("<owner>"));ok(a.text().contains("<developer>"));ok(a.text().contains("tailO"));ok(a.text().contains("tailD"));
                var afterClockRollover=WorkspaceReplica122.restore(O,a.checkpoint(a.text()));ok(afterClockRollover.text().equals(a.text()));ok(afterClockRollover.pendingCount130()==a.pendingCount130());
                poll(h,O,p,a,os,100,0xABCDEF,false);var replayModel=a;rejects(()->poll(h,O,p,replayModel,os,100,1,false));
                var disconnected=h.disconnect130(O);ok(disconnected.stream().filter(e->e.recipient().equals(D)).allMatch(e->!e.data().getOrDefault("peers","").contains(O.toString())));
                call(h,O,p,"OPEN",Map.of("path","src/A.java"));poll(h,O,p,a,UUID.randomUUID(),1,2,false);
                // Hidden paths and their cursor labels never leak in a push to a restricted member.
                call(h,O,p,"POLL",Map.of("path","private/Secret.java","anchor",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"selection",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"color","1","sequence","1","session",UUID.randomUUID().toString()));
                var hidden=h.events130(O,p,"POLL","private/Secret.java");ok(hidden.stream().filter(e->e.recipient().equals(V)).noneMatch(e->e.data().values().stream().anyMatch(value->value.contains("Secret.java"))));
                String epochBefore=call(h,O,p,"SYNC",Map.of("path","src/A.java")).get("epoch");
                call(h,O,p,"COMPACT",Map.of("path","src/A.java","rev",rev(h,p)));
                var reset=call(h,D,p,"SYNC",Map.of("path","src/A.java"));ok(!reset.get("epoch").equals(epochBefore));ok(new CollaborativeDocument121(WorkspaceCodec122.document(reset.get("document"))).text().equals(b.text()));
                call(h,O,p,"NEW",Map.of("path","src/New.java","rev",rev(h,p)));ok(h.events130(O,p,"NEW","src/New.java").stream().anyMatch(e->e.recipient().equals(D)&&e.data().get("files").contains("src/New.java")));
                member(h,p,D,"NONE","","");var revoked=h.events130(O,p,"MEMBER","");ok(revoked.stream().anyMatch(e->e.recipient().equals(D)&&e.data().get("result").equals("DENIED")));rejects(()->call(h,D,p,"SYNC",Map.of("path","src/A.java")));
                var read=Map.of("op","SYNC","project",p,"path","src/A.java","request",UUID.randomUUID().toString());var retry=new WorkspaceRequest123(read,0);ok(retry.tick(3_000_000_000L)==WorkspaceRequest123.Action.SEND);watchNow.set(11_000_000_001L);ok(h.events130(O,p,"POLL","src/A.java").isEmpty());
            }
            try(var h=new WorkspaceHub122(root.resolve("hub"))){ok(call(h,O,project,"OPEN",Map.of("path","src/A.java")).get("peers").isEmpty());ok(h.events130(O,project,"POLL","src/A.java").isEmpty());}
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Realtime130 checks passed: "+checks+"; queued typing, duplicate wire delivery, push convergence, cursor anchors/colors/selections, fairness, ACL, disconnect and restart.");
    }
}
