package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.WorkspaceRelease121;
import com.fixmer.mared.technology.blockbench.WorkspaceModelClient147;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Actual production client -> codec -> hub -> codec -> client, without a Minecraft window. */
public final class WorkspaceModelWireChecks147 {
    static int count;static final UUID OWNER=UUID.randomUUID(),VIEWER=UUID.randomUUID();
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);count++;}
    static Map<String,String> direct(WorkspaceHub122 hub,UUID actor,String op,String room,Map<String,String> fields)throws Exception{var q=new TreeMap<>(fields);q.put("request",UUID.randomUUID().toString());q.put("op",op);q.put("project",room);return hub.execute(actor,true,"Player",q);}
    static final class Host implements WorkspaceClient122.Host135 {
        final WorkspaceHub122 hub;final UUID actor;final Object connection=new Object();WorkspaceClient122 client;boolean corrupt;int packets;
        Host(WorkspaceHub122 hub,UUID actor){this.hub=hub;this.actor=actor;}
        public Object connection(){return connection;}public boolean channel(Object ignored){return true;}public long now(){return System.nanoTime();}public void execute(Runnable action){action.run();}public void failed(Exception failure){throw new AssertionError(failure);}
        public void send(byte[] bytes){try{
            packets++;var q=WorkspaceCodec122.decode(bytes,WorkspaceCodec122.REQUEST);Map<String,String> answer;
            try{answer=hub.execute(actor,true,"Player",q);}catch(Exception refusal){answer=Map.of("result",refusal.getMessage()==null?"INVALID":refusal.getMessage());}
            var out=new TreeMap<>(answer);out.put("request",q.get("request"));if(corrupt&&out.containsKey("chunk147")){byte[] chunk=Base64.getDecoder().decode(out.get("chunk147"));if(chunk.length>0)chunk[0]^=1;out.put("chunk147",Base64.getEncoder().encodeToString(chunk));}
            client.receive135(WorkspaceCodec122.encode(out,WorkspaceCodec122.RESPONSE));
        }catch(Exception failure){throw new AssertionError(failure);}}
    }
    static Map<String,String> legacy149(Map<String,String> room){var legacy=new TreeMap<>(room);legacy.remove("compression149");return legacy;}
    static boolean failed149(java.util.concurrent.CompletableFuture<?> future){try{future.join();return false;}catch(java.util.concurrent.CompletionException expected){return true;}}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("model-wire147-");
        try(var hub=new WorkspaceHub122(root)){
            var room=direct(hub,OWNER,"CREATE","",Map.of("text","Models"));String project=room.get("project"),path="models/test.bbmodel";
            Host host=new Host(hub,OWNER);host.client=new WorkspaceClient122(host);
            try(var models=new WorkspaceModelClient147(host.client,legacy149(room),metadata->{})){
                check(models.save("models/bad.bbmodel","",String.valueOf((char)0xd800)).isCompletedExceptionally(),"invalid UTF16 cannot silently replace data");
                String text="{\"meta\":{},\"name\":\"Модель\",\"texture\":\""+"x".repeat(70000)+"\"}";
                var saved=models.save(path,"",text).join();check(saved.hash().equals(WorkspaceRelease121.sha(text.getBytes(StandardCharsets.UTF_8))),"acknowledged save hash");
                check(host.packets>=8,"large model used multiple real packets");
                check(models.watch(path).join().hash().equals(saved.hash()),"model activity channel returns current revision");
                var loaded=models.open(path).join();check(loaded.text().equals(text)&&loaded.editable(),"roundtrip UTF8 source");
                check(models.open("models/missing.bbmodel").isCompletedExceptionally(),"missing file is not an empty model");
                models.seed(Map.of(path,text+" ","models/second.bbmodel",text)).join();check(models.open(path).join().text().equals(text),"seeding preserves remote edits");check(models.open("models/second.bbmodel").join().text().equals(text),"seeding adds missing models");
                var fresh=direct(hub,OWNER,"VIEW",project,Map.of());direct(hub,OWNER,"MEMBER",project,Map.of("target",VIEWER.toString(),"role","VIEWER","prefix","","deny","","hidden","","rev",fresh.get("revision")));
                Host viewer=new Host(hub,VIEWER);viewer.client=new WorkspaceClient122(viewer);
                try(var readOnly=new WorkspaceModelClient147(viewer.client,room,metadata->{})){check(!readOnly.open(path).join().editable(),"viewer permission in actual client response");check(failed149(readOnly.save(path,saved.hash(),text+" ")),"viewer cannot save");}
                models.seed(Map.of("models/placeholder.bbmodel","")).join();check(models.open("models/placeholder.bbmodel").join().text().isEmpty(),"empty local model seed uses the real chunk protocol");
                host.corrupt=true;check(failed149(models.open(path)),"client rejects a corrupted downloaded model");host.corrupt=false;
                fresh=direct(hub,OWNER,"VIEW",project,Map.of());direct(hub,OWNER,"NEW",project,Map.of("path","models/empty.bbmodel","rev",fresh.get("revision")));
                check(models.open("models/empty.bbmodel").join().text().isEmpty(),"empty legacy model is an editable placeholder");
            }
        }finally{try(var all=Files.walk(root)){for(Path path:all.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("Model wire147 checks: "+count+" passed");
    }
}
