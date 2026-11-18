package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.scripts.SourceProjects;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.mared.gui2.framework.components.editor.*;
import com.fixmer.mared.gui2.studio.panels.workspace.DocumentSession;
import java.nio.file.*;
import java.util.*;

/** Native editor document identity, source binding repair and mirror boundary regressions. No game renderer required. */
public final class WorkspaceWorkbenchChecks132 {
    private static int checks;
    private static final UUID OWNER=new UUID(132,1),PEER=new UUID(132,2);
    private interface Checked{void run()throws Exception;}
    private static void ok(boolean value){if(!value)throw new AssertionError("Workbench132 #"+checks);checks++;}
    private static void rejects(Checked action)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Expected rejection #"+checks);}
    private static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> fields)throws Exception{
        var q=new TreeMap<>(fields);q.put("op",op);q.put("project",project);q.put("request",UUID.randomUUID().toString());return hub.execute(actor,true,"Player",q);
    }
    private static void nativeIdentity()throws Exception{
        var doc=new EditorDocument();var history=new EditorHistory();var view=new EditorView();doc.setValue("same native Scripts document 😀\n");
        var solo=new DocumentSession(doc,history,view);solo.storageId="src/Main.java";var joined=new DocumentSession(solo.document,solo.history,solo.view);
        ok(joined.document==solo.document);ok(joined.history==solo.history);ok(joined.view==solo.view);
        joined.document.setValue("updated by teammate\n");ok(solo.currentText().equals("updated by teammate\n"));
        solo.lastSavedText=solo.currentText();solo.recomputeDirty();ok(!solo.dirty);joined.document.setValue("typed in the normal editor");solo.recomputeDirty();ok(solo.dirty);
        var other=new DocumentSession();ok(other.document!=solo.document);ok(other.history!=solo.history);ok(other.view!=solo.view);
        rejects(()->new DocumentSession(null,history,view));
    }
    private static void mirror(Path temp)throws Exception{
        var sources=new SourceProjects(temp.resolve("scripts"));sources.create("Existing",SourceProjects.Language.JAVA);
        // Exactly 64 files. A rename adding a lexically earlier path produces 65 intermediate checkpoint entries.
        for(int i=0;i<63;i++)sources.createFile("Existing",String.format("src/Z%02d.java",i),"class Z"+i+" {}\n");
        var mirror=new WorkspaceScripts129(sources.root(),temp.resolve("bindings"));var initial=mirror.snapshot("Existing");ok(initial.size()==64);
        String packed=WorkspaceProject126.pack(initial);String room,store;
        try(var hub=new WorkspaceHub122(temp.resolve("hub"))){
            var created=call(hub,OWNER,"CREATE_SCRIPT","",Map.of("source","Existing","data",packed));room=created.get("project");store=created.get("store");ok(created.get("source132").equals("Existing"));
            var baseline=call(hub,OWNER,"SOURCE_BASE132",room,Map.of());ok(baseline.get("data").equals(packed));rejects(()->call(hub,PEER,"SOURCE_BASE132",room,Map.of()));
            String finalRoom=room,finalStore=store;rejects(()->mirror.sync(OWNER,finalStore,UUID.fromString(finalRoom),packed));
            mirror.bind(OWNER,store,UUID.fromString(room),baseline.get("source132"),baseline.get("data"));ok(mirror.bound(OWNER,store,UUID.fromString(room)));
            var renamed=call(hub,OWNER,"RENAME",room,Map.of("path","src/Z62.java","target","src/A.java","rev",created.get("revision")));
            mirror.sync(OWNER,store,UUID.fromString(room),call(hub,OWNER,"SOURCE_PULL",room,Map.of()).get("data"));ok(sources.files("Existing").size()==64);ok(sources.read("Existing","src/A.java").contains("Z62"));ok(!sources.files("Existing").contains("src/Z62.java"));
            // The durable expanded checkpoint can be read on restart and retried without duplicate file writes.
            new WorkspaceScripts129(sources.root(),temp.resolve("bindings")).sync(OWNER,store,UUID.fromString(room),call(hub,OWNER,"SOURCE_PULL",room,Map.of()).get("data"));ok(sources.list().projects().size()==1);
            String revision=renamed.get("revision");rejects(()->call(hub,OWNER,"NEW",finalRoom,Map.of("path","unsupported","rev",revision)));
            rejects(()->call(hub,OWNER,"RENAME",finalRoom,Map.of("path","src/A.java","target","unsupported","rev",revision)));
        }
        try(var hub=new WorkspaceHub122(temp.resolve("hub"))){ok(call(hub,OWNER,"VIEW",room,Map.of()).get("source132").equals("Existing"));}
        // Legacy bindings migrate; stale recovery records matching already-saved disk content do not block sync.
        var old=sources.read("Existing","src/Main.java");sources.checkpoint("Existing","src/Main.java","older disk baseline",old);
        ok(mirror.snapshot("Existing").get("src/Main.java").equals(old));
        var remote=new TreeMap<>(mirror.snapshot("Existing"));remote.put("src/Main.java","// next remote version\n");
        mirror.sync(OWNER,store,UUID.fromString(room),WorkspaceProject126.pack(remote));ok(sources.read("Existing","src/Main.java").contains("next remote"));ok(sources.recovery("Existing","src/Main.java")==null);
        sources.checkpoint("Existing","src/Main.java",remote.get("src/Main.java"),"unsaved unique local draft");remote.put("src/Main.java","// incoming change\n");String next=WorkspaceProject126.pack(remote);String fStore=store,fRoom=room;
        rejects(()->mirror.sync(OWNER,fStore,UUID.fromString(fRoom),next));ok(sources.recovery("Existing","src/Main.java").text().equals("unsaved unique local draft"));
        // Backward compatibility with the exact old two-field binding schema.
        sources.checkpoint("Existing","src/Main.java","","");var current=mirror.snapshot("Existing");UUID legacy=UUID.randomUUID();Path legacyPath=temp.resolve("bindings").resolve(OWNER.toString()).resolve(store).resolve(legacy+".binding");
        Files.write(legacyPath,WorkspaceCodec122.encode(Map.of("source","Existing","data",WorkspaceProject126.pack(current)),32700));
        mirror.sync(OWNER,store,legacy,WorkspaceProject126.pack(current));ok(sources.list().projects().size()==1);
    }
    private static void interruptedMirror(Path temp)throws Exception{
        var sources=new SourceProjects(temp.resolve("interrupted-scripts"));sources.create("Same",SourceProjects.Language.JAVA);for(int i=0;i<63;i++)sources.createFile("Same",String.format("src/Z%02d.java",i),"class Z"+i+" {}\n");
        var mirror=new WorkspaceScripts129(sources.root(),temp.resolve("interrupted-bindings"));var original=mirror.snapshot("Same");UUID room=UUID.randomUUID();String store="b".repeat(64);mirror.bind(OWNER,store,room,"Same",WorkspaceProject126.pack(original));
        var remote=new TreeMap<>(original);remote.put("src/A.java",remote.remove("src/Z62.java"));sources.createFile("Same","src/A.java",remote.get("src/A.java"));
        Path binding=temp.resolve("interrupted-bindings").resolve(OWNER.toString()).resolve(store).resolve(room+".binding");Files.write(binding,WorkspaceCodec122.encode(Map.of("source","Same","data",WorkspaceProject126.pack(original)),32700));
        mirror.sync(OWNER,store,room,WorkspaceProject126.pack(remote));ok(sources.files("Same").size()==64);ok(!sources.files("Same").contains("src/Z62.java"));ok(sources.read("Same","src/A.java").contains("Z62"));ok(sources.list().projects().size()==1);
    }
    public static void main(String[] args)throws Exception{
        nativeIdentity();Path temp=Files.createTempDirectory("workspace132-");try{mirror(temp);interruptedMirror(temp);}finally{try(var paths=Files.walk(temp)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Workspace132 checks passed: "+checks+" (native identity, binding repair, 64-file rename, old bindings, drafts, source path permissions)");
    }
}
