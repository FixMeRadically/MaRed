package com.fixmer.mared;

import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.creation.CreationCatalog145;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import static com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;

public final class WorkspaceModelChecks147 {
    static int count;
    static final UUID OWNER=UUID.randomUUID(),OTHER=UUID.randomUUID(),VIEWER=UUID.randomUUID(),ROOM=UUID.randomUUID();
    static void check(boolean test,String message){if(!test)throw new AssertionError(message);count++;}
    interface Action {void run()throws Exception;}
    static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Expected refusal");}catch(IOException|SecurityException|IllegalArgumentException|IllegalStateException expected){count++;}}
    static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String room,Map<String,String> fields)throws Exception{
        var q=new TreeMap<>(fields);q.put("op",op);q.put("project",room);q.put("request",UUID.randomUUID().toString());WorkspaceHub122.request(q);
        return hub.execute(actor,true,"Player",q);
    }
    static Map<String,String> begin(WorkspaceModels147 service,WorkspaceEngine121 engine,UUID actor,String expected,String text)throws Exception{
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);return service.execute("MODEL_BEGIN147",ROOM,engine,actor,Map.of("path","models/test.bbmodel","expected",expected,"hash",WorkspaceRelease121.sha(bytes),"size",Integer.toString(bytes.length)));
    }
    static void put(WorkspaceModels147 service,WorkspaceEngine121 engine,UUID actor,String token,String text)throws Exception{
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        for(int at=0;at<bytes.length;at+=WorkspaceModels147.CHUNK){byte[] part=Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+WorkspaceModels147.CHUNK));service.execute("MODEL_PUT147",ROOM,engine,actor,Map.of("path","models/test.bbmodel","token",token,"offset",Integer.toString(at),"chunk",Base64.getEncoder().encodeToString(part)));}
    }
    static void commit(WorkspaceModels147 service,WorkspaceEngine121 engine,UUID actor,String token)throws Exception{service.execute("MODEL_COMMIT147",ROOM,engine,actor,Map.of("path","models/test.bbmodel","token",token));}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("models147-");String first="{\"meta\":{\"model_format\":\"free\"},\"name\":\"Модель\",\"animations\":[{\"name\":\"idle\"}],\"texture\":\""+"x".repeat(70000)+"\"}";
        reject(()->WorkspaceModels147.encode(String.valueOf((char)0xd800)));
        WorkspaceModels147.validate(("\uFEFF"+first).getBytes(StandardCharsets.UTF_8));check(true,"Windows UTF8 BOM accepted");
        String next=first.replace("idle","walk");String path="models/test.bbmodel";
        try{
            try(var hub=new WorkspaceHub122(root.resolve("hub"))){
                String room=call(hub,OWNER,"CREATE","",Map.of("text","Models")).get("project");
                var q=call(hub,OWNER,"MODEL_STAT147",room,Map.of("path",path));check(q.get("hash147").isEmpty()&&q.get("editable147").equals("true"),"hub exposes model protocol");
                reject(()->call(hub,OTHER,"MODEL_OPEN147",room,Map.of("path",path)));
                var resource=call(hub,OWNER,"CREATE_SCRIPT","",Map.of("source","resource","data",WorkspaceProject126.pack(Map.of()),"direction147","resourcepacks"));check(resource.get("direction147").equals("resourcepacks"),"shared project retains its product direction");
                var legacy=call(hub,OWNER,"CREATE_SCRIPT","",Map.of("source","legacy","data",WorkspaceProject126.pack(Map.of("old.bbmodel","{\"meta\":{},\"secret\":\"secret\"}"))));String legacyId=legacy.get("project");
                call(hub,OWNER,"MEMBER",legacyId,Map.of("target",VIEWER.toString(),"role","REVIEWER","prefix","","deny","","hidden","ASSETS","rev",legacy.get("revision")));
                check(call(hub,VIEWER,"VIEW",legacyId,Map.of()).get("files").isEmpty(),"hidden old text-model omitted from catalog");
                check(call(hub,VIEWER,"SEARCH126",legacyId,Map.of("query","secret","matchCase","false")).get("hits").isEmpty(),"hidden model cannot leak through text search");
                check(call(hub,VIEWER,"JOIN_MANIFEST131",legacyId,Map.of()).get("sourceFiles").isEmpty(),"hidden model cannot leak through join/export");
                check(!call(hub,VIEWER,"HLOG131",legacyId,Map.of("query","","author","","action","","before",Long.toString(Long.MAX_VALUE))).get("log131").contains("old.bbmodel"),"hidden model omitted from history log");
                reject(()->call(hub,VIEWER,"HISTORY_DIFF",legacyId,Map.of("path","old.bbmodel","history","1")));
                reject(()->call(hub,OWNER,"MODEL_OPEN147",room,Map.of("path","../x.bbmodel")));
                reject(()->call(hub,OWNER,"MODEL_OPEN147",room,Map.of("path","code.java")));
                byte[] packet=WorkspaceCodec122.encode(Map.of("chunk",Base64.getEncoder().encodeToString(new byte[WorkspaceModels147.CHUNK])),WorkspaceCodec122.REQUEST);check(packet.length<WorkspaceCodec122.REQUEST,"chunks fit real request bound");
            }
            var service=new WorkspaceModels147();
            try(var engine=new WorkspaceEngine121(root.resolve("journal"),ROOM,OWNER)){
                engine.grant(OWNER,engine.revision(),OTHER,new Member(Role.DEVELOPER,Set.of(),Set.of(),List.of()));
                engine.grant(OWNER,engine.revision(),VIEWER,new Member(Role.VIEWER,Set.of(),Set.of(),List.of()));
                var started=begin(service,engine,OWNER,"",first);String token=started.get("token147");
                check(!engine.files(OWNER).containsKey(path),"begin does not create a partial model");
                reject(()->commit(service,engine,OWNER,token));
                reject(()->put(service,engine,OTHER,token,first));
                put(service,engine,OWNER,token,first);check(!engine.files(OWNER).containsKey(path),"uploaded chunks are invisible until commit");
                put(service,engine,OWNER,token,first);count++; // identical retries are accepted
                commit(service,engine,OWNER,token);check(new String(engine.files(OWNER).get(path).bytes(),StandardCharsets.UTF_8).equals(first),"large Unicode model and animation committed exactly");
                reject(()->engine.document(OWNER,path));
                String hash=WorkspaceRelease121.sha(first.getBytes(StandardCharsets.UTF_8));
                var opened=service.execute("MODEL_OPEN147",ROOM,engine,VIEWER,Map.of("path",path));check(opened.get("editable147").equals("false"),"viewer can read but cannot save");
                reject(()->begin(service,engine,VIEWER,hash,next));
                reject(()->service.execute("MODEL_GET147",ROOM,engine,OWNER,Map.of("path",path,"token",opened.get("token147"),"offset","0")));
                reject(()->service.execute("MODEL_GET147",ROOM,engine,VIEWER,Map.of("path",path,"token",opened.get("token147"),"offset","1")));
                String a=begin(service,engine,OWNER,hash,next).get("token147"),b=begin(service,engine,OTHER,hash,first+" ").get("token147");
                put(service,engine,OWNER,a,next);put(service,engine,OTHER,b,first+" ");commit(service,engine,OWNER,a);
                reject(()->commit(service,engine,OTHER,b));check(new String(engine.files(OWNER).get(path).bytes(),StandardCharsets.UTF_8).equals(next),"stale collaborator cannot replace a newer model");
                // A download remains an immutable snapshot across another writer's commit.
                byte[] chunk=Base64.getDecoder().decode(service.execute("MODEL_GET147",ROOM,engine,VIEWER,Map.of("path",path,"token",opened.get("token147"),"offset","0")).get("chunk147"));
                check(Arrays.equals(chunk,Arrays.copyOf(first.getBytes(StandardCharsets.UTF_8),WorkspaceModels147.CHUNK)),"download pinned to opening snapshot");
                check(engine.historyLog131(OWNER,"","","MODEL147",Long.MAX_VALUE).rows().stream().anyMatch(e->e.path().equals(path)),"model commits enter normal shared history");
                String fresh=WorkspaceRelease121.sha(next.getBytes(StandardCharsets.UTF_8));String c=begin(service,engine,OTHER,fresh,first).get("token147");put(service,engine,OTHER,c,first);
                engine.grant(OWNER,engine.revision(),OTHER,new Member(Role.DEVELOPER,Set.of(Capability.ERASE_CODE),Set.of(),List.of()));reject(()->commit(service,engine,OTHER,c));
                engine.grant(OWNER,engine.revision(),VIEWER,new Member(Role.VIEWER,Set.of(),Set.of(Panel.ASSETS),List.of()));reject(()->service.execute("MODEL_OPEN147",ROOM,engine,VIEWER,Map.of("path",path)));
                reject(()->engine.putModel147(OWNER,"model.json","",first.getBytes(StandardCharsets.UTF_8)));
                reject(()->WorkspaceModels147.validate(new byte[WorkspaceModels147.MAX+1]));reject(()->WorkspaceModels147.validate(new byte[]{(byte)0xff}));
                engine.createText(OWNER,engine.revision(),"old.bbmodel","{\"meta\":{}}");String oldHash=WorkspaceRelease121.sha(engine.files(OWNER).get("old.bbmodel").bytes());engine.putModel147(OWNER,"old.bbmodel",oldHash,next.getBytes(StandardCharsets.UTF_8));reject(()->engine.document(OWNER,"old.bbmodel"));check(engine.files(OWNER).get("old.bbmodel").bytes().length>65536,"old text model migrates atomically");
            }
            try(var engine=new WorkspaceEngine121(root.resolve("journal"),ROOM,OWNER)){check(new String(engine.files(OWNER).get(path).bytes(),StandardCharsets.UTF_8).equals(next),"models survive journal restart");}
            var storage=new SourceProjects(root.resolve("projects"));storage.createContent145("pack",CreationCatalog145.Direction.RESOURCEPACKS);check(storage.direction147("pack").equals("resourcepacks"),"existing project direction is retained");storage.createFile("pack",path,first);
            var binding=new WorkspaceScripts129(storage.root(),root.resolve("bindings"));check(!binding.snapshot("pack").containsKey(path),"large model bypasses old short-text import");check(binding.models147("pack").get(path).equals(first),"initial sharing captures model assets separately");
            // A pre-147 short-text binding must not interpret the new asset channel as deletion.
            String small="{\"meta\":{}}";storage.createFile("pack","models/old.bbmodel",small);
            String fingerprint="a".repeat(64);Path oldBinding=root.resolve("bindings").resolve(OWNER.toString()).resolve(fingerprint).resolve(ROOM+".binding");Files.createDirectories(oldBinding.getParent());
            var oldTexts=new TreeMap<>(binding.snapshot("pack"));oldTexts.put("models/old.bbmodel",small);
            Files.write(oldBinding,WorkspaceCodec122.encode(Map.of("source","pack","data",WorkspaceProject126.pack(oldTexts)),4*1024*1024));
            binding.sync(OWNER,fingerprint,ROOM,WorkspaceProject126.pack(binding.snapshot("pack")));
            check(storage.read("pack","models/old.bbmodel").equals(small),"upgrading an old binding preserves local model files");
            binding.mirrorModel147(OWNER,fingerprint,ROOM,"models/old.bbmodel",next);check(storage.read("pack","models/old.bbmodel").equals(next),"server model mirrored to original folder");
            storage.save("pack","models/old.bbmodel",next,next+" ");reject(()->binding.mirrorModel147(OWNER,fingerprint,ROOM,"models/old.bbmodel",first));check(storage.read("pack","models/old.bbmodel").equals(next+" "),"offline local edits preserved");
            binding.mirrorModel147(OWNER,fingerprint,ROOM,"models/new.bbmodel",first);check(storage.read("pack","models/new.bbmodel").equals(first),"new shared model materialized in original folder");
            storage.checkpoint("pack","models/new.bbmodel",first,first+" ");reject(()->binding.mirrorModel147(OWNER,fingerprint,ROOM,"models/new.bbmodel",next));
            check(CreationCatalog145.sections(CreationCatalog145.Direction.MAPS).stream().anyMatch(section->section.id().equals("tools147")),"previous tools reachable under new directions");
        }finally{try(var all=Files.walk(root)){for(Path p:all.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Workspace model checks147: "+count+" passed");
    }
}
