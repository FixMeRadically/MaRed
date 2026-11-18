package com.fixmer.genesis.technology.workspace.live122;
import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.storage.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.mared.technology.scripts.ScriptHelpManual;
import com.fixmer.mared.technology.runtime.CommandFilePlan;
import com.fixmer.mared.gui2.framework.components.editor.CodeSnippets;
import java.io.*;import java.nio.file.*;import java.util.*;

/** Real folder links, legacy files, standard MDK sharing, errors and the bundled guides; no renderer. */
public final class WorkspaceUnifiedChecks133 {
 static int checks;static final UUID OWNER=new UUID(133,1),OTHER=new UUID(133,2);
 interface Work{void run()throws Exception;}
 static void ok(boolean value){checks++;if(!value)throw new AssertionError("Unified133 #"+checks);}
 static void reject(Work work)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Expected rejection "+checks);}
 static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> fields)throws Exception{var q=new TreeMap<>(fields);q.put("op",op);q.put("project",project);q.put("request",UUID.randomUUID().toString());return hub.execute(actor,true,"Player",q);}
 static void pathsAndSources(Path root)throws Exception{
  for(String path:List.of(".gitignore",".gitattributes",".editorconfig",".gitkeep",".github/workflows/build.yml","src/Папка проекта/Main.java"))ok(WorkspacePolicy121.path(path).equals(path));
  for(String path:List.of("../escape",".git/config",".env","a/../../escape","C:/outside","a|b.java","a\\b.java","a/NUL.txt"))reject(()->WorkspacePolicy121.path(path));
  var sources=new SourceProjects(root.resolve("sources"));sources.create("Existing",SourceProjects.Language.JAVA);
  var template=NeoForgeProjectTemplate.files("example");String launcher=template.get("gradlew");ok(launcher!=null&&launcher.length()>8192&&launcher.length()<=16384);
  sources.createFile("Existing","gradlew",launcher);sources.createFile("Existing",".gitignore","build/\n.gradle/\n");sources.createFile("Existing",".gitattributes","* text=auto\n");
  var mirror=new WorkspaceScripts129(sources.root(),root.resolve("bindings"));String packed=WorkspaceProject126.pack(mirror.snapshot("Existing"));
  try(var hub=new WorkspaceHub122(root.resolve("hub"))){
   var created=call(hub,OWNER,"CREATE_SCRIPT","",Map.of("source","Existing","data",packed));String room=created.get("project");ok(created.get("mode133").equals("SCRIPTS"));ok(created.get("files").contains(".gitignore|TEXT"));ok(created.get("files").contains("gradlew|TEXT"));
   var open=call(hub,OWNER,"OPEN",room,Map.of("path","gradlew"));ok(new CollaborativeDocument121(WorkspaceCodec122.document(open.get("document"))).text().equals(launcher));
   var replica=new WorkspaceReplica122(OWNER,WorkspaceCodec122.document(open.get("document")));replica.grant(open.get("lease"),Long.parseLong(open.get("first")),Long.parseLong(open.get("last")));ok(replica.prepare(launcher+"\n# changed\n"));
   while(replica.pending()){var pending=replica.first();var request=new TreeMap<>(Map.of("op","EDIT","project",room,"request",pending.request().toString(),"path","gradlew","batch",pending.batch(),"lease",pending.lease()));var ack=hub.execute(OWNER,true,"Player",request);replica.acknowledge(pending.request(),WorkspaceCodec122.document(ack.get("document")));}
   mirror.bind(OWNER,created.get("store"),UUID.fromString(room),"Existing",packed);mirror.sync(OWNER,created.get("store"),UUID.fromString(room),call(hub,OWNER,"SOURCE_PULL",room,Map.of()).get("data"));ok(sources.read("Existing","gradlew").endsWith("# changed\n"));
   var command=call(hub,OWNER,"CREATE_COMMAND133","",Map.of("source","Existing","data",WorkspaceProject126.pack(Map.of("main.mcfunction","say hello\n"))));ok(!command.get("project").equals(room));ok(command.get("mode133").equals("COMMANDS"));ok(call(hub,OWNER,"CREATE_COMMAND133","",Map.of("source","Existing","data",WorkspaceProject126.pack(Map.of("main.mcfunction","say hello\n")))).get("project").equals(command.get("project")));
   call(hub,OWNER,"MEMBER",command.get("project"),Map.of("target",OTHER.toString(),"role","VIEWER","prefix","","deny","","hidden","","rev",command.get("revision")));
   ok(call(hub,OTHER,"VIEW",command.get("project"),Map.of()).get("mode133").equals("COMMANDS"));
  }
 }
 static void folders(Path root)throws Exception{
  Path external=Files.createDirectories(root.resolve("ordinary folder"));Files.writeString(external.resolve("main.mcfunction"),"say before\n");var storage=new SourceProjects(root.resolve("folder-catalog"));
  var project=storage.linkFolder133("Map",external);ok(storage.workingDirectory(project.id()).toRealPath().equals(external.toRealPath()));ok(!Files.exists(external.resolve("genesis-project.properties")));ok(storage.read("Map","main.mcfunction").equals("say before\n"));storage.save("Map","main.mcfunction","say before\n","say after\n");ok(Files.readString(external.resolve("main.mcfunction")).equals("say after\n"));
  storage.createFile("Map","папка с пробелами/test.mr","{\n}\n");ok(Files.exists(external.resolve("папка с пробелами/test.mr")));ok(storage.linkFolder133("OtherName",external).id().equals("Map"));storage.copyEntry("Map","папка с пробелами","copied folder",Map.of("папка с пробелами/test.mr","{\n}\n"));ok(Files.readString(external.resolve("copied folder/test.mr")).equals("{\n}\n"));Path deleted=storage.trashEntry("Map","copied folder",Map.of());ok(!Files.exists(external.resolve("copied folder")));storage.restoreTrash(deleted);ok(Files.exists(external.resolve("copied folder/test.mr")));ok(storage.list().projects().size()==1);
  storage.renameEntry("Map","main.mcfunction","renamed.mcfunction",Map.of("main.mcfunction","say after\n"));ok(!Files.exists(external.resolve("main.mcfunction")));ok(Files.exists(external.resolve("renamed.mcfunction")));
  var trash=storage.trashEntry("Map","renamed.mcfunction",Map.of("renamed.mcfunction","say after\n"));ok(!Files.exists(external.resolve("renamed.mcfunction")));storage.restoreTrash(trash);ok(Files.exists(external.resolve("renamed.mcfunction")));
  Path parent=Files.createDirectories(root.resolve("new projects"));var made=storage.createFolder133("New map",parent);ok(storage.workingDirectory(made.id()).equals(parent.resolve("New map")));reject(()->storage.createFolder133("New map",parent));ok(Files.isDirectory(parent.resolve("New map")));
  reject(()->storage.linkFolder133("Wrong",storage.root()));
 }
 static void migration(Path root)throws Exception{
  Path legacy=root.resolve("legacy"),catalog=root.resolve("maps");var old=new TextRepository(legacy);old.create("first","say first\n");old.createDirectory("nested");old.create("nested/second","{ print 1 }\n");
  CommandProjects133.migrate(catalog,legacy);var sources=new SourceProjects(catalog);ok(sources.list().projects().size()==1);ok(sources.read("LegacyCommands","first.mr").equals("say first\n"));ok(sources.read("LegacyCommands","nested/second.mr").contains("print"));ok(old.read("first").equals("say first\n"));
  sources.save("LegacyCommands","first.mr","say first\n","say edited\n");CommandProjects133.migrate(catalog,legacy);ok(sources.read("LegacyCommands","first.mr").equals("say edited\n"));ok(old.read("first").equals("say first\n"));
  Path collision=root.resolve("collision");new SourceProjects(collision).createEmpty("LegacyCommands");CommandProjects133.migrate(collision,legacy);ok(new SourceProjects(collision).project("LegacyCommands2")!=null);
 }
 static void docsAndCommands(Path root)throws Exception{
  for(String lang:List.of("ru","en")){var script=ScriptHelpManual.load(lang);var commands=ScriptHelpManual.loadCommands133(lang);ok(script.articles().size()>=35);ok(commands.articles().size()>=15);for(String id:List.of("workspace133","folders133","sync-errors133","keys133","commands133")){ok(script.article(id).id().equals(id));ok(commands.article(id).id().equals(id));}ok(commands.article("start").copyText().contains("Commands"));}
  var plan=CommandFilePlan.parse("# comment\n// comment\nsay hello\n{\n print 1\n}\n");ok(plan.parts().size()==2);ok(!plan.parts().get(0).script());ok(plan.parts().get(1).script());ok(CommandFilePlan.parse("#persistent\n# comment\nsay ready").persistent());reject(()->CommandFilePlan.parse("{\n print 1\n"));
  ok(CodeSnippets.forPath("main.mcfunction").size()>=4);ok(CodeSnippets.forPath("logic.mr").get(0).id().equals("mr_block"));
  var paste=new WorkspaceReplica122(OWNER,new CollaborativeDocument121().snapshot());for(int i=0;i<8;i++)paste.grant("lease"+i,1+i*2048,(i+1)*2048);String full="a".repeat(16384);ok(paste.prepare(full));ok(paste.text().equals(full));ok(paste.pendingCount130()==64);ok(WorkspaceReplica122.restore(OWNER,paste.checkpoint(full)).text().equals(full));reject(()->paste.prepare(full+"b"));ok(paste.text().equals(full));
  Path log=root.resolve("logs/mared-workspace.log");String details=WorkspaceFailure133.report(log,new IOException("PATH: .gitignore"));ok(details.contains("PATH: .gitignore"));ok(Files.readString(log).contains("IOException"));
  Path impossible=root.resolve("not-a-directory");Files.writeString(impossible,"file");String failed=WorkspaceFailure133.report(impossible.resolve("log"),new IOException("original cause"));ok(failed.contains("original cause"));ok(failed.contains("Log write failed"));
 }
 public static void main(String[] args)throws Exception{Path root=Files.createTempDirectory("unified133-");try{pathsAndSources(root);folders(root);migration(root);docsAndCommands(root);}finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}System.out.println("Workspace133 checks passed: "+checks+" (standard gradlew, dotfiles, Commands mode, real folder writes, migration, MR layout, bundled help and actual logs)");}
}
