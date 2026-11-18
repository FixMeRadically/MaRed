package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.scripts.SourceProjects;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import java.nio.file.*;
import java.util.*;

/** Real Scripts files, durable bindings, collaboration journals and authorization. */
public final class WorkspaceSectionsChecks129 {
    static final UUID OWNER=new UUID(129,1),OTHER=new UUID(129,2);
    static int checks;
    interface Work{void run()throws Exception;}
    static void ok(boolean value){if(!value)throw new AssertionError("Check "+checks);checks++;}
    static void rejects(Work work)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Expected rejection "+checks);}
    static Map<String,String> call(WorkspaceHub122 h,UUID actor,String op,String room,Map<String,String> fields)throws Exception{
        var q=new TreeMap<>(fields);q.put("op",op);q.put("project",room);q.put("request",UUID.randomUUID().toString());return h.execute(actor,true,"Player",q);
    }
    static String rev(WorkspaceHub122 h,String room)throws Exception{return call(h,OWNER,"VIEW",room,Map.of()).get("revision");}
    static void edit(WorkspaceHub122 h,UUID actor,String room,String text)throws Exception{
        var d=call(h,actor,"OPEN",room,Map.of("path","src/Main.java"));var replica=new WorkspaceReplica122(actor,WorkspaceCodec122.document(d.get("document")));
        replica.grant(d.get("lease"),Long.parseLong(d.get("first")),Long.parseLong(d.get("last")));ok(replica.prepare(text));
        while(replica.pending()){
            var op=replica.first();var q=new TreeMap<>(Map.of("op","EDIT","project",room,"request",op.request().toString(),"path","src/Main.java","batch",op.batch(),"lease",op.lease()));
            var a=h.execute(actor,false,"Player",q);replica.acknowledge(op.request(),WorkspaceCodec122.document(a.get("document")));
        }
    }
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("workspace129-");String room,store;
        try{
            var sources=new SourceProjects(temp.resolve("scripts"));sources.create("Existing",SourceProjects.Language.JAVA);
            var bridge=new WorkspaceScripts129(sources.root(),temp.resolve("bindings"));ok(bridge.projects().size()==1);
            var original=bridge.snapshot("Existing");String packed=WorkspaceProject126.pack(original);
            try(var h=new WorkspaceHub122(temp.resolve("hub"))){
                var created=call(h,OWNER,"CREATE_SCRIPT","",Map.of("source","Existing","data",packed));room=created.get("project");store=created.get("store");
                ok(created.get("files").contains("src/Main.java"));ok(!created.get("scriptHash129").isEmpty());
                ok(call(h,OWNER,"CREATE_SCRIPT","",Map.of("source","Existing","data",packed)).get("project").equals(room));
                bridge.bind(OWNER,store,UUID.fromString(room),"Existing",packed);ok(bridge.bound(OWNER,store,UUID.fromString(room)));
                String p=room;rejects(()->call(h,OTHER,"REMOVE_WORKSPACE",p,Map.of("rev",rev(h,p))));rejects(()->call(h,OTHER,"SOURCE_PULL",p,Map.of()));
                call(h,OWNER,"MEMBER",p,Map.of("target",OTHER.toString(),"role","DEVELOPER","prefix","","deny","","hidden","","rev",rev(h,p)));
                rejects(()->call(h,OTHER,"REMOVE_WORKSPACE",p,Map.of("rev",rev(h,p))));rejects(()->call(h,OTHER,"SOURCE_PULL",p,Map.of()));
                edit(h,OTHER,p,"// changed by teammate 😀\n");String revision=rev(h,p);
                var diff=call(h,OWNER,"HISTORY_DIFF",p,Map.of("path","src/Main.java","history",revision));ok(diff.get("afterText").contains("teammate"));ok(diff.get("beforeText").equals(original.get("src/Main.java")));
                String remote=call(h,OWNER,"SOURCE_PULL",p,Map.of()).get("data");bridge.sync(OWNER,store,UUID.fromString(p),remote);ok(sources.read("Existing","src/Main.java").contains("teammate"));
                bridge.sync(OWNER,store,UUID.fromString(p),remote);ok(sources.list().projects().size()==1);
                sources.save("Existing","src/Main.java",sources.read("Existing","src/Main.java"),"local conflict");edit(h,OTHER,p,"remote conflict");
                String conflict=call(h,OWNER,"SOURCE_PULL",p,Map.of()).get("data");String fingerprint=store;
                rejects(()->bridge.sync(OWNER,fingerprint,UUID.fromString(p),conflict));ok(sources.read("Existing","src/Main.java").equals("local conflict"));
                sources.save("Existing","src/Main.java","local conflict","// changed by teammate 😀\n");bridge.sync(OWNER,store,UUID.fromString(p),conflict);ok(sources.read("Existing","src/Main.java").equals("remote conflict"));
                call(h,OWNER,"RENAME",p,Map.of("path","src/Main.java","target","src/Renamed.java","rev",rev(h,p)));
                bridge.sync(OWNER,store,UUID.fromString(p),call(h,OWNER,"SOURCE_PULL",p,Map.of()).get("data"));ok(sources.files("Existing").contains("src/Renamed.java"));ok(!sources.files("Existing").contains("src/Main.java"));
                var ticket=call(h,OWNER,"DELETE_REVIEW",p,Map.of("path","src/Renamed.java","rev",rev(h,p)));call(h,OWNER,"DELETE",p,Map.of("path","src/Renamed.java","rev",ticket.get("revision"),"ticket",ticket.get("ticket")));
                String deletedRev=rev(h,p);diff=call(h,OWNER,"HISTORY_DIFF",p,Map.of("path","src/Renamed.java","history",deletedRev));ok(diff.get("afterText").isEmpty());ok(diff.get("beforeText").equals("remote conflict"));
                bridge.sync(OWNER,store,UUID.fromString(p),call(h,OWNER,"SOURCE_PULL",p,Map.of()).get("data"));ok(sources.files("Existing").isEmpty());
                call(h,OWNER,"NEW",p,Map.of("path","src/Again.java","rev",rev(h,p)));
                bridge.sync(OWNER,store,UUID.fromString(p),call(h,OWNER,"SOURCE_PULL",p,Map.of()).get("data"));ok(sources.files("Existing").contains("src/Again.java"));
            }
            try(var h=new WorkspaceHub122(temp.resolve("hub"))){
                ok(bridge.bound(OWNER,store,UUID.fromString(room)));ok(call(h,OWNER,"VIEW",room,Map.of()).get("files").contains("src/Again.java"));
                String p=room;rejects(()->call(h,OWNER,"REMOVE_WORKSPACE",p,Map.of("rev","0")));
                call(h,OWNER,"REMOVE_WORKSPACE",p,Map.of("rev",rev(h,p)));ok(call(h,OWNER,"LIST","",Map.of()).get("projects").isEmpty());
                ok(sources.list().projects().size()==1);ok(sources.read("Existing","src/Again.java").isEmpty());rejects(()->call(h,OWNER,"VIEW",p,Map.of()));
            }
            sources.checkpoint("Existing","src/Again.java","","unsaved");rejects(()->bridge.snapshot("Existing"));
        }finally{try(var walk=Files.walk(temp)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Workspace129 checks passed: "+checks+" (real files, sync, conflicts, history, permissions, restart, deletion)");
    }
}
