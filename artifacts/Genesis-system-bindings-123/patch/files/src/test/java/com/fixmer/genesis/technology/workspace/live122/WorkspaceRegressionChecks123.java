package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.mared.gui2.framework.components.editor.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;

/** Production recovery/retry/authorization code, real journals and export directories. */
public final class WorkspaceRegressionChecks123 {
    private static int checks;
    private static final UUID OWNER=new UUID(123,1), DEV=new UUID(123,2), PEER=new UUID(123,3);
    private static void check(boolean value) { checks++; if(!value)throw new AssertionError("Regression123 #"+checks); }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action) throws Exception {
        try { action.run(); } catch(Exception expected) { check(true); return; }
        throw new AssertionError("Expected rejection #"+(checks+1));
    }
    private static Map<String,String> q(String op,String project,Map<String,String> fields) {
        var q=new TreeMap<>(fields);q.put("op",op);q.put("project",project);q.put("request",UUID.randomUUID().toString());return q;
    }
    private static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> fields)throws Exception {
        return hub.execute(actor,true,"Player",q(op,project,fields));
    }
    private static String rev(WorkspaceHub122 hub,String project)throws Exception { return call(hub,OWNER,"VIEW",project,Map.of()).get("revision"); }
    private static void requests() {
        var create=q("CREATE","",Map.of("text","Project"));
        var pending=new WorkspaceRequest123(create,0);
        check(pending.tick(2_999_999_999L)==WorkspaceRequest123.Action.WAIT);
        check(pending.tick(3_000_000_000L)==WorkspaceRequest123.Action.UNKNOWN);
        pending=new WorkspaceRequest123(create,0);pending.busy(10);
        check(pending.tick(500_000_009L)==WorkspaceRequest123.Action.WAIT);
        check(pending.tick(500_000_010L)==WorkspaceRequest123.Action.SEND);
        check(pending.request().equals(create));
        // A lost response after the BUSY retry must NOT cause another CREATE.
        check(pending.tick(3_500_000_010L)==WorkspaceRequest123.Action.UNKNOWN);
        var edit=q("EDIT",OWNER.toString(),Map.of("path","a.java","batch","opaque","lease","opaque"));
        pending=new WorkspaceRequest123(edit,0);
        check(pending.tick(3_000_000_000L)==WorkspaceRequest123.Action.SEND);
        check(pending.tick(6_000_000_000L)==WorkspaceRequest123.Action.SEND);
        check(pending.request().equals(edit));
        pending.busy(6_000_000_000L);
        check(pending.tick(15_000_000_000L)==WorkspaceRequest123.Action.UNKNOWN);
    }
    private static void recovery(Path root)throws Exception {
        var original=new CollaborativeDocument121();original.apply(original.replace(OWNER,0,0,"original"));
        var replacement=new CollaborativeDocument121();replacement.apply(replacement.replace(PEER,0,0,"new file"));
        var replica=new WorkspaceReplica122(DEV,original.snapshot());
        String oldEpoch=UUID.randomUUID().toString(),currentEpoch=UUID.randomUUID().toString(),store="a".repeat(64);
        var saved=new TreeMap<>(replica.checkpoint("unsent local draft"));saved.put("epoch",oldEpoch);
        var cache=new WorkspaceCache122(root.resolve("cache"));
        cache.save(DEV,store,OWNER,"src/A.java",saved);
        // Repeated close/reopen MUST NOT relabel the stale target with the new epoch.
        for(int n=0;n<3;n++) {
            var recovered=WorkspaceReplica122.recover(DEV,cache.load(DEV,store,OWNER,"src/A.java"),currentEpoch,replacement.snapshot());
            check(recovered.stale());check(recovered.epoch().equals(oldEpoch));
            check(recovered.target().equals("unsent local draft"));check(recovered.replica().text().equals("new file"));
            var next=new TreeMap<>(recovered.replica().checkpoint(recovered.target()));next.put("epoch",recovered.epoch());
            cache.save(DEV,store,OWNER,"src/A.java",next);
        }
        var clean=new TreeMap<>(replica.checkpoint(replica.text()));clean.put("epoch",oldEpoch);
        var current=WorkspaceReplica122.recover(DEV,clean,oldEpoch,replacement.snapshot());
        check(!current.stale());check(current.target().equals("new file"));
    }
    private static void hub(Path root)throws Exception {
        try(var hub=new WorkspaceHub122(root.resolve("hub"))) {
            String p=call(hub,OWNER,"CREATE","",Map.of("text","Regression")).get("project");
            call(hub,OWNER,"MEMBER",p,Map.of("target",DEV.toString(),"role","DEVELOPER","prefix","","deny","","hidden","","rev",rev(hub,p)));
            call(hub,OWNER,"NEW",p,Map.of("path","src/A.java","rev",rev(hub,p)));
            call(hub,OWNER,"NEW",p,Map.of("path","private/Secret.java","rev",rev(hub,p)));
            var opened=call(hub,DEV,"OPEN",p,Map.of("path","src/A.java"));
            String revision=rev(hub,p);
            for(int n=0;n<4;n++) {
                var synced=call(hub,DEV,"SYNC",p,Map.of("path","src/A.java"));
                check(synced.containsKey("document"));check(!synced.containsKey("lease"));
                check(synced.get("revision").equals(revision));
            }
            rejects(()->call(hub,OWNER,"NEW",p,Map.of("path","src/A.java/child","rev",rev(hub,p))));
            rejects(()->call(hub,OWNER,"NEW",p,Map.of("path","SRC","rev",rev(hub,p))));
            rejects(()->call(hub,OWNER,"RENAME",p,Map.of("path","private/Secret.java","target","src/a.JAVA/nested","rev",rev(hub,p))));
            check(call(hub,OWNER,"OPEN",p,Map.of("path","private/Secret.java")).containsKey("document"));
            call(hub,OWNER,"MEMBER",p,Map.of("target",DEV.toString(),"role","DEVELOPER","prefix","private","deny","","hidden","CODE","rev",rev(hub,p)));
            var view=call(hub,DEV,"VIEW",p,Map.of());
            check(!view.get("files").contains("Secret"));check(!view.get("changes").contains("Secret"));
            rejects(()->call(hub,DEV,"SYNC",p,Map.of("path","private/Secret.java")));
            // Owner presence in a hidden document must not reveal its path through another document.
            call(hub,OWNER,"POLL",p,Map.of("path","private/Secret.java","anchor",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"selection",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"color","123","sequence","1","session",UUID.randomUUID().toString()));
            check(!call(hub,DEV,"SYNC",p,Map.of("path","src/A.java")).get("peers").contains("Secret"));
            call(hub,DEV,"POLL",p,Map.of("path","src/A.java","anchor",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"selection",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"color","123","sequence","1","session",UUID.randomUUID().toString(),"editing","true"));
            check(call(hub,OWNER,"SYNC",p,Map.of("path","src/A.java")).get("peers").contains("Player *"));
            rejects(()->call(hub,DEV,"POLL",p,Map.of("path","src/A.java","anchor",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"selection",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"color","123","sequence","2","session",UUID.randomUUID().toString(),"editing","invalid")));
            call(hub,OWNER,"NEW",p,Map.of("path","input122.manifest","rev",rev(hub,p)));
            var preview=call(hub,OWNER,"PREVIEW",p,Map.of("paths","input122.manifest","rev",rev(hub,p)));
            call(hub,OWNER,"APPROVE",p,Map.of("hash",preview.get("hash"),"rev",preview.get("revision")));
            rejects(()->call(hub,OWNER,"EXPORT",p,Map.of()));
            check(!Files.exists(root.resolve("hub").resolve(p).resolve("exports")));
            // A release that mixes a retained file with its new child cannot be exported.
            var a=new WorkspaceRelease121.File("tree",1,new byte[]{1});
            var b=new WorkspaceRelease121.File("TREE/child",1,new byte[]{2});
            rejects(()->WorkspaceRelease121.freeze(OWNER,1,OWNER,Map.of(a.path(),a,b.path(),b)));
            var local=new WorkspaceReplica122(DEV,WorkspaceCodec122.document(opened.get("document")));
            local.grant(opened.get("lease"),Long.parseLong(opened.get("first")),Long.parseLong(opened.get("last")));
            check(local.prepare("not approved"));var op=local.first();
            call(hub,OWNER,"MEMBER",p,Map.of("target",DEV.toString(),"role","VIEWER","prefix","","deny","","hidden","","rev",rev(hub,p)));
            rejects(()->call(hub,DEV,"EDIT",p,Map.of("path","src/A.java","batch",op.batch(),"lease",op.lease())));
        }
    }
    private static void readOnly() {
        var doc=new EditorDocument();doc.setValue("copy me");doc.startSelection(0,0);doc.extendSelection(0,7);
        var clipboard=new StringBuilder();
        var host=new EditorController.EditorHost() {
            public int x(){return 0;}public int y(){return 0;}public int w(){return 400;}public int h(){return 300;}
            public boolean focused(){return true;}public void requestFocus(){}public boolean editable(){return false;}
            public String clipboardGet(){return "overwrite";}public void clipboardSet(String text){clipboard.append(text);}
        };
        var controller=new EditorController(host,doc,new EditorHistory(),new EditorView(),()->{throw new AssertionError("read-only mutation");});
        check(controller.keyPressed(67,0,2));check(clipboard.toString().equals("copy me"));
        check(!controller.charTyped('x',0));
        for(int key:new int[]{86,88,90,89,259,261,257})check(!controller.keyPressed(key,0,2));
        check(doc.getValue().equals("copy me"));check(doc.getSelectedText().equals("copy me"));
    }
    public static void main(String[] args)throws Exception {
        requests();readOnly();Path root=Files.createTempDirectory("Genesis regression123 ");
        try { recovery(root);hub(root); }
        finally { try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);} }
        System.out.println("Regression123 passed: "+checks+"; actual retry/recovery/ACL/split-sync/export/readonly code; no Minecraft render acceptance.");
    }
}
