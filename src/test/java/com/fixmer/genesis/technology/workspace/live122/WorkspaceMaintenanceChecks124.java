package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import java.nio.file.*;
import java.util.*;
import static com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;

public final class WorkspaceMaintenanceChecks124 {
    private static int checks;
    private static final UUID O=new UUID(124,1),D=new UUID(124,2);
    private static void ok(boolean value){checks++;if(!value)throw new AssertionError("Workspace124 #"+checks);}
    private interface Checked{void run()throws Exception;}
    private static void reject(Checked action)throws Exception{try{action.run();}catch(Exception expected){ok(true);return;}throw new AssertionError("Missing rejection");}
    private static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> values)throws Exception{
        var q=new TreeMap<>(values);q.put("op",op);q.put("project",project);q.put("request",UUID.randomUUID().toString());return hub.execute(actor,true,"Player",q);
    }
    private static String rev(WorkspaceHub122 hub,String p)throws Exception{return call(hub,O,"VIEW",p,Map.of()).get("revision");}
    private static WorkspaceReplica122 replica(UUID actor,Map<String,String> open)throws Exception{
        var r=new WorkspaceReplica122(actor,WorkspaceCodec122.document(open.get("document")));
        if(open.containsKey("lease"))r.grant(open.get("lease"),Long.parseLong(open.get("first")),Long.parseLong(open.get("last")));return r;
    }
    private static void drain(WorkspaceHub122 hub,UUID actor,String p,WorkspaceReplica122 r)throws Exception{
        while(r.pending()){var pending=r.first();var answer=call(hub,actor,"EDIT",p,Map.of("path","src/A.java","batch",pending.batch(),"lease",pending.lease()));r.acknowledge(pending.request(),WorkspaceCodec122.document(answer.get("document")));}
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("Genesis workspace124 ");String project;
        try{
            var appearance=new WorkspaceAppearance124(root.resolve("colors"));
            ok(appearance.load(D,0x123456)==0x123456);appearance.save(D,0xFEDCBA);ok(appearance.load(D,0)==0xFEDCBA);ok(appearance.load(O,0)==0);
            ok(WorkspaceAppearance124.parse("#01aBff")==0x01ABFF);reject(()->WorkspaceAppearance124.parse("12345"));reject(()->appearance.save(D,-1));
            var policy=new WorkspacePolicy121(O);policy.grant(O,0,D,new Member(Role.DEVELOPER,Set.of(),Set.of(),List.of(new Rule("Private",Set.of(Capability.EDIT),Set.of(Panel.CODE)))));
            ok(!policy.allows(D,Capability.EDIT,"private/A.java"));ok(!policy.panel(D,Panel.CODE,"PRIVATE/A.java"));ok(policy.allows(D,Capability.EDIT,"private2/A.java"));
            try(var hub=new WorkspaceHub122(root.resolve("hub"))){
                project=call(hub,O,"CREATE","",Map.of("text","Maintenance")).get("project");String p=project;
                call(hub,O,"MEMBER",p,Map.of("target",D.toString(),"role","DEVELOPER","prefix","","deny","","hidden","","rev",rev(hub,p)));
                var open=call(hub,O,"NEW",p,Map.of("path","src/A.java","rev",rev(hub,p)));var r=replica(O,open);
                ok(r.prepare("initial 😀"));drain(hub,O,p,r);
                for(int n=0;n<70;n++){ok(r.prepare("version "+n+" 😀"));drain(hub,O,p,r);}
                var outstanding=replica(D,call(hub,D,"OPEN",p,Map.of("path","src/A.java")));ok(outstanding.prepare(outstanding.text()+" unsent"));
                var current=call(hub,O,"SYNC",p,Map.of("path","src/A.java"));var old=WorkspaceCodec122.document(current.get("document"));
                reject(()->call(hub,D,"COMPACT",p,Map.of("path","src/A.java","rev",rev(hub,p))));
                call(hub,O,"COMPACT",p,Map.of("path","src/A.java","rev",rev(hub,p)));
                var fresh=call(hub,O,"OPEN",p,Map.of("path","src/A.java"));var compact=WorkspaceCodec122.document(fresh.get("document"));
                ok(!fresh.get("epoch").equals(current.get("epoch")));ok(new CollaborativeDocument121(compact).text().equals(r.text()));ok(compact.nodes().size()<old.nodes().size());
                reject(()->drain(hub,D,p,outstanding));
                var history=call(hub,O,"HISTORY",p,Map.of("path","src/A.java"));ok(history.get("history").lines().count()==64);ok(!history.get("historyBefore").equals("0"));
                var earlier=call(hub,O,"HISTORY",p,Map.of("path","src/A.java","before",history.get("historyBefore")));ok(!earlier.get("history").isEmpty());
                var newest=new HashSet<>(history.get("history").lines().toList());ok(earlier.get("history").lines().noneMatch(newest::contains));
                var ticket=call(hub,O,"DELETE_REVIEW",p,Map.of("path","src/A.java","rev",rev(hub,p)));
                call(hub,O,"DELETE",p,Map.of("path","src/A.java","ticket",ticket.get("ticket"),"rev",ticket.get("revision")));
                ok(call(hub,O,"HISTORY_FILES",p,Map.of()).get("historyFiles").equals("src/A.java"));
                var deleted=call(hub,O,"HISTORY",p,Map.of("path","src/A.java"));String deletion=deleted.get("history").lines().findFirst().orElseThrow().split("\\|",3)[0];
                ok(!deleted.get("restorable").lines().toList().contains(deletion));String restore=deleted.get("restorable").lines().findFirst().orElseThrow();
                call(hub,O,"RESTORE",p,Map.of("path","src/A.java","history",restore,"rev",deleted.get("revision")));
                ok(call(hub,O,"HISTORY_FILES",p,Map.of()).get("historyFiles").isEmpty());
                ok(new CollaborativeDocument121(WorkspaceCodec122.document(call(hub,O,"SYNC",p,Map.of("path","src/A.java")).get("document"))).text().equals(r.text()));
                ticket=call(hub,O,"DELETE_REVIEW",p,Map.of("path","src/A.java","rev",rev(hub,p)));
                call(hub,O,"DELETE",p,Map.of("path","src/A.java","ticket",ticket.get("ticket"),"rev",ticket.get("revision")));
                call(hub,O,"MEMBER",p,Map.of("target",D.toString(),"role","DEVELOPER","prefix","SRC","deny","","hidden","CODE","rev",rev(hub,p)));
                ok(call(hub,D,"HISTORY_FILES",p,Map.of()).get("historyFiles").isEmpty());reject(()->call(hub,D,"HISTORY",p,Map.of("path","src/A.java")));
            }
            try(var hub=new WorkspaceHub122(root.resolve("hub"))){ok(call(hub,O,"HISTORY_FILES",project,Map.of()).get("historyFiles").equals("src/A.java"));}
        }finally{try(var walk=Files.walk(root)){for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("Workspace124 passed: "+checks+"; actual journal paging/deleted recovery/compaction/stale lease/ACL/persisted RGB; no Minecraft UI.");
    }
}
