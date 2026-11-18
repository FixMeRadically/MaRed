package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.server.remote.*;
import java.nio.file.*;
import java.util.*;
import static com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;

/** Real hub/journal, byte transfer and filesystem. Minecraft rendering and Gradle are separate checks. */
public final class WorkspacePipelineChecks125 {
    private static int count;
    private static final UUID O=new UUID(125,1),D=new UUID(125,2);
    private static void ok(boolean value){count++;if(!value)throw new AssertionError("Pipeline125 #"+count);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action)throws Exception{try{action.run();}catch(Exception expected){ok(true);return;}throw new AssertionError("Rejection missing #"+count);}
    private static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> values)throws Exception{var q=new TreeMap<>(values);q.put("op",op);q.put("project",project);q.put("request",UUID.randomUUID().toString());return hub.execute(actor,true,"Player",q);}
    private static String rev(WorkspaceHub122 h,String p)throws Exception{return call(h,O,"VIEW",p,Map.of()).get("revision");}
    private static void approve(WorkspaceHub122 h,String p,String paths)throws Exception{var preview=call(h,O,"PREVIEW",p,Map.of("paths",paths,"rev",rev(h,p)));call(h,O,"APPROVE",p,Map.of("hash",preview.get("hash"),"rev",preview.get("revision")));}
    private static RemoteConsent72.Frame frame(UUID ticket,String sha,String decision,String phase,long revision){return new RemoteConsent72.Frame(O,D,ticket,O,D,sha,"localhost:25565","https://localhost:8443/","a".repeat(64),revision,1,1000,phase,decision);}
    public static void main(String[] args)throws Exception {
        Path temp=Files.createTempDirectory("Workspace pipeline125 ");
        try {
            var files=new TreeMap<String,WorkspaceRelease121.File>();
            files.put("empty.txt",new WorkspaceRelease121.File("empty.txt",1,new byte[0]));
            files.put("src/main/java/Demo.java",new WorkspaceRelease121.File("src/main/java/Demo.java",2,"😀Жx".repeat(4096).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            String hash=WorkspaceRelease121.digest(files),manifest=WorkspaceSources125.manifest(files);
            var download=new WorkspaceSources125.Download(hash,manifest);reject(download::verified);reject(()->download.materialize(temp));
            reject(()->download.accept("other.txt",0,""));reject(()->download.accept("empty.txt",1,""));reject(()->download.accept("empty.txt",0,"AA=="));
            while(download.next()!=null){var next=download.next();byte[] all=files.get(next.path()).bytes();int offset=download.offset();byte[] chunk=Arrays.copyOfRange(all,offset,Math.min(all.length,offset+WorkspaceSources125.CHUNK));download.accept(next.path(),offset,Base64.getEncoder().encodeToString(chunk));ok(true);}
            ok(download.verified().size()==2);reject(()->download.accept("empty.txt",0,""));
            Path project=download.materialize(temp.resolve("copies")),second=download.materialize(temp.resolve("copies"));ok(!project.equals(second));
            for(var f:files.values())ok(Arrays.equals(f.bytes(),Files.readAllBytes(project.resolve(f.path()))));
            ok(Files.exists(project.getParent().resolve("workspace125")));ok(!Files.exists(project.resolve("workspace125")));
            for(String bad:List.of("../escape|1|0|"+hash,"CON.txt|1|0|"+hash,"a|0|0|"+hash,"a|1|-1|"+hash,"a|1|"+(WorkspaceSources125.MAX_BYTES+1)+"|"+hash,"a|1|0|bad","a|1|0|"+hash+"\nA|1|0|"+hash,"a|1|0|"+hash+"\na/b|1|0|"+hash,"a/b|1|0|"+hash+"\na|1|0|"+hash,""))reject(()->new WorkspaceSources125.Download(hash,bad));
            var tampered=new WorkspaceSources125.Download(hash,manifest);tampered.accept("empty.txt",0,"");reject(()->tampered.accept("src/main/java/Demo.java",0,"AA=="));
            byte[] data="text".getBytes();String one="test.txt|1|4|"+WorkspaceRelease121.sha(data);var corrupt=new WorkspaceSources125.Download(hash,one);reject(()->corrupt.accept("test.txt",0,Base64.getEncoder().encodeToString("xxxx".getBytes())));
            var wrongHash=new WorkspaceSources125.Download(hash,one);wrongHash.accept("test.txt",0,Base64.getEncoder().encodeToString(data));reject(wrongHash::verified);
            String p;
            try(var hub=new WorkspaceHub122(temp.resolve("server"))){
                p=call(hub,O,"CREATE","",Map.of("text","Test build pipeline")).get("project");
                reject(()->call(hub,O,"BUILD_SOURCE",p,Map.of()));
                call(hub,O,"NEW",p,Map.of("path","build.gradle","rev",rev(hub,p)));
                call(hub,O,"NEW",p,Map.of("path","src/A.java","rev",rev(hub,p)));
                call(hub,O,"MEMBER",p,Map.of("target",D.toString(),"role","DEVELOPER","prefix","","deny","","hidden","","rev",rev(hub,p)));
                approve(hub,p,"build.gradle\nsrc/A.java");var source=call(hub,D,"BUILD_SOURCE",p,Map.of());ok(source.get("build125").equals("true"));
                var received=new WorkspaceSources125.Download(source.get("sourceHash"),source.get("sourceFiles"));
                while(received.next()!=null){String path=received.next().path();var chunk=call(hub,D,"BUILD_CHUNK",p,Map.of("path",path,"hash",received.hash(),"offset",Integer.toString(received.offset())));received.accept(path,received.offset(),chunk.get("chunk"));}
                ok(received.verified().size()==2);reject(()->call(hub,D,"BUILD_CHUNK",p,Map.of("path","build.gradle","hash","0".repeat(64),"offset","0")));
                reject(()->call(hub,D,"BUILD_CHUNK",p,Map.of("path","build.gradle","hash",received.hash(),"offset","1")));
                call(hub,O,"MEMBER",p,Map.of("target",D.toString(),"role","DEVELOPER","prefix","src","deny","","hidden","CODE","rev",rev(hub,p)));
                reject(()->call(hub,D,"BUILD_SOURCE",p,Map.of()));approve(hub,p,"build.gradle\nsrc/A.java");reject(()->call(hub,D,"BUILD_SOURCE",p,Map.of()));
                reject(()->call(hub,D,"BUILD_CHUNK",p,Map.of("path","build.gradle","hash",received.hash(),"offset","0")));
                call(hub,O,"MEMBER",p,Map.of("target",D.toString(),"role","VIEWER","prefix","","deny","","hidden","","rev",rev(hub,p)));approve(hub,p,"build.gradle\nsrc/A.java");reject(()->call(hub,D,"BUILD_SOURCE",p,Map.of()));
                approve(hub,p,"build.gradle");ok(call(hub,O,"BUILD_SOURCE",p,Map.of()).get("sourceHash").equals(received.hash()));
                call(hub,O,"NEW",p,Map.of("path","new.txt","rev",rev(hub,p)));approve(hub,p,"new.txt");ok(!call(hub,O,"BUILD_SOURCE",p,Map.of()).get("sourceHash").equals(received.hash()));reject(()->call(hub,O,"BUILD_CHUNK",p,Map.of("path","build.gradle","hash",received.hash(),"offset","0")));
                call(hub,O,"MEMBER",p,Map.of("target",D.toString(),"role","NONE","prefix","","deny","","hidden","","rev",rev(hub,p)));reject(()->call(hub,D,"BUILD_SOURCE",p,Map.of()));approve(hub,p,"build.gradle");
            }
            try(var hub=new WorkspaceHub122(temp.resolve("server"))){ok(call(hub,O,"BUILD_SOURCE",p,Map.of()).get("sourceFiles").startsWith("build.gradle|"));}
            var request=new TreeMap<String,String>();request.put("request",UUID.randomUUID().toString());request.put("op","BUILD_SOURCE");request.put("project",p);
            var retry=new WorkspaceRequest123(request,0);for(int n=1;n<=3;n++)ok(retry.tick(n*3_000_000_000L)==WorkspaceRequest123.Action.SEND);ok(retry.tick(12_000_000_000L)==WorkspaceRequest123.Action.UNKNOWN);
            var intent=new RemoteConsentIntent125();String sha="b".repeat(64);var pending=frame(O,sha,"PENDING","ACTIVE",1);var accepted=frame(O,sha,"ACCEPTED","ACTIVE",1);
            ok(!intent.permits(accepted));intent.accept(pending);ok(!intent.permits(pending));ok(intent.permits(accepted));
            ok(!intent.permits(frame(D,sha,"ACCEPTED","ACTIVE",1)));ok(!intent.permits(frame(O,"c".repeat(64),"ACCEPTED","ACTIVE",1)));ok(!intent.permits(frame(O,sha,"ACCEPTED","ACTIVE",2)));ok(!intent.permits(frame(O,sha,"DECLINED","ACTIVE",1)));ok(!intent.permits(frame(O,sha,"ACCEPTED","CANCELLED",1)));
            intent.clear();ok(!intent.permits(accepted));reject(()->intent.accept(accepted));
        }finally{try(var walk=Files.walk(temp)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Workspace pipeline125 passed: "+count+"; approved transfer, chunks, tamper, fresh filesystem, ACL revocation, hidden source, approval changes, journal replay and reordered consent.");
    }
}
