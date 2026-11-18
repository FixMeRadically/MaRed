package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicLong;

/** Production wire and hub: caret updates bypass durable transport without carrying documents. */
public final class CaretChecks151 {
    static int count;static final String ROOT=WorkspaceCodec122.id(CollaborativeDocument121.ROOT);
    static void check(boolean value,String description){if(!value)throw new AssertionError(description);count++;}
    static Map<String,String> q(String op,String project,Map<String,String> fields){return WorkspaceClient122.request(op,project,fields);}
    static Map<String,String> caret(String project,String epoch,String session,long seq,String anchor,String selection){return q("CARET151",project,Map.of("path","main.java","epoch151",epoch,"session",session,"sequence",Long.toString(seq),"anchor",anchor,"selection",selection,"color","123456","editing","true"));}
    interface Action{void run()throws Exception;}
    static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|SecurityException expected){count++;}}
    public static void main(String[] args)throws Exception{
        var cadence=new CaretSchedule151();
        check(cadence.take(0,"start"),"first position immediately eligible");
        check(!cadence.take(49_999_999,"next"),"20Hz cap");
        check(cadence.take(50_000_000,"latest"),"only latest state selected");
        check(!cadence.take(499_999_999,"latest"),"stationary caret does not poll documents");
        check(cadence.take(550_000_000,"latest"),"stationary heartbeat");
        cadence.retry();check(cadence.take(600_000_000,"latest"),"transient refusal can retry next tick");
        check(CaretSchedule151.project("abc","abc",2)==2,"unchanged caret exact");
        check(CaretSchedule151.project("abc","aXYZbc",2)==5,"caret after local insertion remains anchored");
        check(CaretSchedule151.project("aXYZbc","abc",5)==2,"pending local position projects to confirmed document");
        check(CaretSchedule151.project("a😀b","a🚀🚀😀b",2)==4,"Unicode code points do not split a surrogate");
        check(CaretSchedule151.project("hello world","hello!",9)<=6,"replacement projects inside bounds");
        check(CaretSchedule151.project("abc","",3)==0,"whole document deletion");
        check(CaretSchedule151.project("","hello",0)==0,"root before first insertion");
        AtomicLong clock=new AtomicLong();CaretView151 view=new CaretView151(clock::get);String actor=UUID.randomUUID().toString();String row=actor+"|main.java|Owner|123456|"+ROOT+"|"+ROOT;
        view.delta(row,5,"old");check(view.rows().equals(row),"delta visible");
        view.delta(actor,4,"old");check(view.rows().equals(row),"out of order hide ignored");
        view.delta(actor,6,"old");check(view.rows().isEmpty(),"hidden actor removed");
        view.delta(row,5,"old");check(view.rows().isEmpty(),"hidden tombstone prevents stale resurrection");
        view.delta(row,1,"new");check(view.rows().equals(row),"new session resets sequence");
        clock.set(6_000_000_000L);check(view.rows().isEmpty(),"each actor expires independently");
        view.delta("broken|bad",2,"new");check(view.rows().isEmpty(),"invalid update ignored");
        var connection=new Object();var durable=new RealtimeChecks149.Host(connection);var cursor=new RealtimeChecks149.Host(connection);var mouse=new RealtimeChecks149.Host(connection);
        durable.client=new WorkspaceClient122(durable);cursor.client=new WorkspaceClient122(cursor,true);mouse.client=new WorkspaceClient122(mouse,true);
        durable.client.attach();cursor.client.attach();mouse.client.attach();
        try{
            var pendingDoc=new CollaborativeDocument121();var pendingBatch=pendingDoc.replace(RealtimeChecks149.ACTOR,0,0,"typing");
            durable.client.send(q("EDIT",RealtimeChecks149.PROJECT.toString(),Map.of("path","main.java","batch",WorkspaceCodec122.batch(pendingBatch),"lease","pending-lease")),answer->{});
            cursor.client.send(caret(RealtimeChecks149.PROJECT.toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),1,ROOT,ROOT),answer->{});
            mouse.client.send(RealtimeChecks149.pointer(),answer->{});
            check(cursor.sent.size()==1&&mouse.sent.size()==1&&durable.client.busy(),"text caret and mouse bypass busy durable request independently");
            cursor.answer(0,"BUSY");check(!cursor.client.busy()&&durable.client.busy(),"caret refusal cannot release durable transport");
            cursor.client.send(caret(RealtimeChecks149.PROJECT.toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),2,ROOT,ROOT),answer->{});cursor.now=250_000_000;cursor.client.tick();
            check(!cursor.client.busy()&&cursor.sent.size()==2,"lost ephemeral ACK discarded without stale retries");
            cursor.client.send(q("NEW",RealtimeChecks149.PROJECT.toString(),Map.of("path","main.java","rev",ROOT)),answer->check(answer.get("result").equals("INVALID"),"durable mutation rejected on caret channel"));
        }finally{durable.client.detach();cursor.client.detach();mouse.client.detach();}
        Path root=Files.createTempDirectory("caret151-");UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),legacy=UUID.randomUUID();
        try(var hub=new WorkspaceHub122(root)){
            var created=hub.execute(owner,true,"Owner",q("CREATE","",Map.of("text","Caret")));String project=created.get("project");
            check(created.get("caret151").equals("true"),"server negotiates independent caret transport");
            var file=hub.execute(owner,true,"Owner",q("NEW",project,Map.of("path","main.java","rev",created.get("revision"))));
            var member=hub.execute(owner,true,"Owner",q("MEMBER",project,Map.of("target",other.toString(),"role","DEVELOPER","prefix","","deny","","hidden","","rev",file.get("revision"))));
            member=hub.execute(owner,true,"Owner",q("MEMBER",project,Map.of("target",legacy.toString(),"role","VIEWER","prefix","","deny","","hidden","","rev",member.get("revision"))));
            hub.execute(other,false,"Other",q("OPEN",project,Map.of("path","main.java")));hub.execute(legacy,false,"Legacy",q("OPEN",project,Map.of("path","main.java")));
            var snapshot=WorkspaceCodec122.document(file.get("document"));var doc=new CollaborativeDocument121(snapshot);var batch=doc.replace(owner,0,0,"abc😀");
            var edited=hub.execute(owner,true,"Owner",q("EDIT",project,Map.of("path","main.java","batch",WorkspaceCodec122.batch(batch),"lease",file.get("lease"))));
            String epoch=edited.get("epoch"),session=UUID.randomUUID().toString();var ids=WorkspaceCodec122.document(edited.get("document")).nodes();String a=WorkspaceCodec122.id(ids.get(0).id());
            var registration=caret(project,epoch,UUID.randomUUID().toString(),1,ROOT,ROOT);hub.execute(other,false,"Other",registration);
            var command=caret(project,epoch,session,1,a,ROOT);var ack=hub.execute(owner,true,"Owner",command);
            check(!ack.containsKey("document")&&!ack.containsKey("files"),"caret ACK has no document or catalog");
            var events=hub.events130(owner,project,"CARET151","main.java");
            var delta=events.stream().filter(event->event.recipient().equals(other)).findFirst().orElseThrow().data();
            check(delta.containsKey("caretDelta151")&&!delta.containsKey("peers"),"new client gets only one actor delta");
            check(WorkspaceCodec122.encode(delta,WorkspaceCodec122.RESPONSE).length<1000,"selection push stays compact");
            check(delta.get("caretDelta151").endsWith("|"+a+"|"+ROOT),"caret and selection delivered independently");
            check(events.stream().filter(event->event.recipient().equals(legacy)).findFirst().orElseThrow().data().containsKey("peers"),"old clients still receive compatible presence");
            hub.execute(owner,true,"Owner",caret(project,epoch,session,2,ROOT,a));hub.execute(owner,true,"Owner",command);
            var newer=hub.events130(owner,project,"CARET151","main.java").stream().filter(event->event.recipient().equals(other)).findFirst().orElseThrow().data();
            check(newer.get("caretSequence151").equals("2")&&newer.get("caretDelta151").endsWith("|"+ROOT+"|"+a),"late sample cannot roll back a caret");
            reject(()->hub.execute(owner,true,"Owner",caret(project,UUID.randomUUID().toString(),session,3,a,a)));
            reject(()->hub.execute(UUID.randomUUID(),false,"Stranger",command));
            var current=hub.execute(owner,true,"Owner",q("VIEW",project,Map.of()));check(current.get("revision").equals(edited.get("revision")),"cursor movement never writes a project journal revision");
            var denied=hub.execute(owner,true,"Owner",q("MEMBER",project,Map.of("target",other.toString(),"role","VIEWER","prefix","","deny","READ","hidden","","rev",current.get("revision"))));
            reject(()->hub.execute(other,false,"Other",registration));
            var hidden=hub.events130(owner,project,"CARET151","main.java").stream().filter(event->event.recipient().equals(other)).findFirst().orElseThrow().data();
            check(!hidden.getOrDefault("caretDelta151","").contains("main.java"),"revoked path never leaks through presence");
            hub.execute(owner,true,"Owner",q("LEAVE",project,Map.of()));
            check(hub.events130(owner,project,"LEAVE","").stream().filter(event->event.recipient().equals(legacy)).findFirst().orElseThrow().data().get("caretRemove151").equals(owner.toString()),"leave removes cursor immediately");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("CaretChecks151: "+count+" passed");
    }
}
