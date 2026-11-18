package com.fixmer.mared;
import java.util.*;import java.nio.file.*;import java.io.*;import com.fixmer.genesis.technology.workspace.live122.*;import com.fixmer.genesis.technology.scripts.SourceProjects;
/** Real filesystem/hub checks; does not emulate a Minecraft connection. */
public final class WorkspaceChecks143{
 static int passed;static void ok(boolean b){if(!b)throw new AssertionError();}static Map<String,String> q(String op,String project,Map<String,String> fields){var q=new TreeMap<>(fields);q.put("op",op);q.put("project",project);q.put("request",UUID.randomUUID().toString());return q;}
 public static void main(String[]args)throws Exception{Path root=Files.createTempDirectory("mared-workspace143-");try{
  for(String op:List.of("CREATE_SCRIPT","CREATE_COMMAND133")){
   Path folder=root.resolve(op);UUID owner=UUID.randomUUID();String id;
   try(var hub=new WorkspaceHub122(folder)){
    var creation=q(op,"",Map.of("source","empty","data",WorkspaceProject126.pack(Map.of())));var view=hub.execute(owner,true,"Owner",creation);id=view.get("project");ok(view.get("files").isEmpty());passed++;
    ok(id.equals(hub.execute(owner,true,"Owner",creation).get("project")));passed++;
    ok(hub.execute(owner,true,"Owner",q("SOURCE_PULL",id,Map.of())).get("data").isEmpty());passed++;
    try{hub.execute(UUID.randomUUID(),false,"Guest",q(op,"",Map.of("source","denied","data",WorkspaceProject126.pack(Map.of()))));throw new AssertionError();}catch(SecurityException expected){passed++;}
   }
   try(var hub=new WorkspaceHub122(folder)){
    var view=hub.execute(owner,true,"Owner",q("VIEW",id,Map.of()));ok(view.get("files").isEmpty());passed++;
    hub.execute(owner,true,"Owner",q("NEW",id,Map.of("path","first.mr","rev",view.get("revision"))));
    ok(hub.execute(owner,true,"Owner",q("VIEW",id,Map.of())).get("files").contains("first.mr|TEXT|"));passed++;
   }
  }
  Path sources=root.resolve("sources"),bindings=root.resolve("bindings");var projects=new SourceProjects(sources);projects.createEmpty("empty");var storage=new WorkspaceScripts129(sources,bindings);UUID actor=UUID.randomUUID(),room=UUID.randomUUID();String store="a".repeat(64);String packed=WorkspaceProject126.pack(Map.of());storage.bind(actor,store,room,"empty",packed);storage.sync(actor,store,room,WorkspaceProject126.pack(Map.of("created.mr","say hello")));ok(projects.read("empty","created.mr").equals("say hello"));passed++;
  Path binding=bindings.resolve(actor.toString()).resolve(store).resolve(room+".binding");var fields=new TreeMap<>(WorkspaceCodec122.decode(Files.readAllBytes(binding),4*1024*1024));byte[] zipped=Base64.getDecoder().decode(fields.get("baseline132"));fields.put("baseline132",Base64.getEncoder().encodeToString(Arrays.copyOf(zipped,zipped.length+1)));Files.write(binding,WorkspaceCodec122.encode(fields,4*1024*1024));try{storage.sync(actor,store,room,WorkspaceProject126.pack(Map.of("created.mr","new")));throw new AssertionError("Trailing bytes accepted");}catch(IOException expected){ok(projects.read("empty","created.mr").equals("say hello"));passed++;}
  System.out.println("Workspace143 passed="+passed+"; real hub replay, empty Scripts/Commands, permissions, first file, strict binding decode");
 }finally{try(var files=Files.walk(root)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}}
}
