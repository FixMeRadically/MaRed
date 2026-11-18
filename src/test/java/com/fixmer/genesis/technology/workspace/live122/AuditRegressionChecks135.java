package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;
import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.storage.*;
import com.fixmer.genesis.technology.editor.DocumentSearch;
import com.fixmer.mared.technology.runtime.CommandFilePlan;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Authenticated hub and filesystem regressions; does not simulate a running Minecraft client. */
public final class AuditRegressionChecks135 {
    private static final UUID OWNER=new UUID(135,1),GUEST=new UUID(135,2),ROOM=new UUID(135,3);
    private static int passed,failed;
    private static Path root;
    @FunctionalInterface interface Scenario {void run()throws Exception;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void rejects(Scenario scenario)throws Exception{try{scenario.run();}catch(IOException|IllegalArgumentException|IllegalStateException|SecurityException expected){return;}throw new AssertionError("Invalid action accepted");}
    private static void test(String name,Scenario scenario){try{scenario.run();passed++;System.out.println("PASS "+name);}catch(Throwable failure){failed++;System.out.println("FAIL "+name+": "+failure);}}
    private static Member rights(Role role){return new Member(role,Set.of(),Set.of(),List.of());}
    private static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> fields)throws IOException{
        var request=new TreeMap<>(fields);request.put("op",op);request.put("project",project);request.put("request",UUID.randomUUID().toString());return hub.execute(actor,true,"Player",request);
    }
    private static Map<String,String> create(WorkspaceHub122 hub)throws IOException{return call(hub,OWNER,"CREATE_SCRIPT","",Map.of("source","Map","data",WorkspaceProject126.pack(Map.of("main.mr","say hello"))));}
    private static void member(WorkspaceHub122 hub,String project,String role,String denied,String hidden)throws IOException{
        var view=call(hub,OWNER,"VIEW",project,Map.of());call(hub,OWNER,"MEMBER",project,Map.of("target",GUEST.toString(),"role",role,"prefix","","deny",denied,"hidden",hidden,"rev",view.get("revision")));
    }
    private static UUID invite(WorkspaceHub122 hub,String project)throws IOException{
        call(hub,OWNER,"INVITE131",project,Map.of("target",GUEST.toString(),"role","DEVELOPER","prefix","","deny","","hidden",""));
        String row=call(hub,GUEST,"INBOX131","",Map.of()).get("inbox131").lines().filter(s->s.contains("|INVITE|")).findFirst().orElseThrow();return UUID.fromString(row.split("\\|",2)[0]);
    }
    private static WorkspaceInvitations131 invitations(String name)throws IOException{return new WorkspaceInvitations131(root.resolve(name));}
    private static void brokenFile(Path folder,Scenario action)throws Exception{
        Files.createDirectories(folder);Path file=folder.resolve("invitations131"),backup=folder.resolve("saved");boolean existed=Files.exists(file);
        if(existed)Files.move(file,backup);Files.createDirectory(file);
        try{action.run();}finally{Files.delete(file);if(existed)Files.move(backup,file);}
    }
    private static void invitations()throws Exception{
        test("failed invitation creation is not published in memory",()->{
            var store=invitations("issue");brokenFile(root.resolve("issue"),()->rejects(()->store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.VIEWER))));require(store.inbox(GUEST).isEmpty(),"Failed issue remained usable");
        });
        test("failed invitation consumption retains the consent",()->{
            var store=invitations("consume");var offer=store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.VIEWER));brokenFile(root.resolve("consume"),()->rejects(()->store.consume(GUEST,offer.id())));require(store.get(GUEST,offer.id()).equals(offer),"Failed consume removed offer");
        });
        test("failed invitation replacement preserves prior rights",()->{
            var store=invitations("replace");var offer=store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.VIEWER));brokenFile(root.resolve("replace"),()->rejects(()->store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.DEVELOPER))));require(store.get(GUEST,offer.id()).rights().role()==Role.VIEWER,"Failed save changed prior rights");
        });
        test("failed cancellation retains durable invitation",()->{
            var store=invitations("cancel");var offer=store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.VIEWER));brokenFile(root.resolve("cancel"),()->rejects(()->store.cancel(ROOM,GUEST)));require(store.get(GUEST,offer.id()).equals(offer),"Memory differs from disk after cancel failure");
        });
        test("failed project-offer deletion retains durable state",()->{
            var store=invitations("remove");var offer=store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.VIEWER));brokenFile(root.resolve("remove"),()->rejects(()->store.removeProject(ROOM)));require(store.get(GUEST,offer.id()).equals(offer),"Memory differs from disk after deletion failure");
        });
        test("multiple permission scopes survive durable invitation roundtrip",()->{
            var store=invitations("rules");var rights=new Member(Role.MAINTAINER,Set.of(Capability.PUBLISH),Set.of(Panel.BUILD),List.of(new Rule("secret",Set.of(Capability.READ),Set.of()),new Rule("code",Set.of(Capability.DELETE),Set.of(Panel.RELEASES))));
            var offer=store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights);require(new WorkspaceInvitations131(root.resolve("rules")).get(GUEST,offer.id()).rights().equals(rights),"Stored permission scopes were dropped");require(WorkspaceInvitations131.rights(WorkspaceInvitations131.fields(rights)).equals(rights),"Consent wire dropped scopes");
        });
        test("invitations cannot encode ownership transfer",()->{var store=invitations("owner");rejects(()->store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.OWNER)));require(store.inbox(GUEST).isEmpty(),"Invalid owner offer retained");});
        test("failed oversized offer retains existing offer",()->{var store=invitations("oversize");var old=store.issue(ROOM,OWNER,GUEST,"Owner","Map",WorkspaceInvitations131.Kind.INVITE,rights(Role.VIEWER));rejects(()->store.issue(ROOM,OWNER,GUEST,"Owner","x".repeat(9000),WorkspaceInvitations131.Kind.INVITE,rights(Role.DEVELOPER)));require(store.get(GUEST,old.id()).equals(old),"Oversize save changed memory");});
    }
    private static void hub()throws Exception{
        test("hidden Members panel does not expose member-name catalog",()->{try(var hub=new WorkspaceHub122(root.resolve("members"))){String project=create(hub).get("project");member(hub,project,"VIEWER","","MEMBERS");var view=call(hub,GUEST,"VIEW",project,Map.of());require(view.get("members").isEmpty()&&view.get("memberNames131").isEmpty(),"Hidden member list leaked");}});
        test("changing a role invalidates earlier pending consent",()->{try(var hub=new WorkspaceHub122(root.resolve("stale-invite"))){String project=create(hub).get("project");UUID offer=invite(hub,project);member(hub,project,"VIEWER","","");rejects(()->call(hub,GUEST,"ACCEPT131","",Map.of("offer",offer.toString())));require(call(hub,GUEST,"VIEW",project,Map.of()).get("role").equals("VIEWER"),"Old consent restored stronger role");}});
        test("revocation invalidates earlier pending consent",()->{try(var hub=new WorkspaceHub122(root.resolve("revoke-invite"))){String project=create(hub).get("project");UUID offer=invite(hub,project);member(hub,project,"NONE","","");rejects(()->call(hub,GUEST,"ACCEPT131","",Map.of("offer",offer.toString())));rejects(()->call(hub,GUEST,"VIEW",project,Map.of()));}});
        test("empty or fully restricted join manifest is supported",()->{try(var hub=new WorkspaceHub122(root.resolve("empty-join"))){String project=create(hub).get("project");member(hub,project,"VIEWER","READ","");var manifest=call(hub,GUEST,"JOIN_MANIFEST131",project,Map.of());require(manifest.get("sourceFiles").isEmpty(),"Unexpected permitted files");}});
        test("failed source binding load releases the journal lock",()->{
            Path folder=root.resolve("room-lock");String project;try(var hub=new WorkspaceHub122(folder)){project=create(hub).get("project");}
            Path marker=folder.resolve(project).resolve("source129");byte[] original=Files.readAllBytes(marker);Files.write(marker,new byte[]{1,2,3});
            try(var hub=new WorkspaceHub122(folder)){rejects(()->call(hub,OWNER,"VIEW",project,Map.of()));Files.write(marker,original);require(call(hub,OWNER,"VIEW",project,Map.of()).get("result").equals("OK"),"Broken load leaked exclusive journal lock");}
        });
        test("player-name cache failure does not block authorized project reads",()->{
            Path folder=root.resolve("name-cache");String project;try(var hub=new WorkspaceHub122(folder)){project=create(hub).get("project");}
            Files.delete(folder.resolve("players131"));Files.createDirectory(folder.resolve("players131"));
            try(var hub=new WorkspaceHub122(folder)){require(call(hub,OWNER,"VIEW",project,Map.of()).get("result").equals("OK"),"Optional cache blocked project");}
        });
        test("failed workspace archive move preserves pending invitations",()->{
            Path folder=root.resolve("remove-failure");try(var hub=new WorkspaceHub122(folder)){String project=create(hub).get("project");UUID offer=invite(hub,project);Files.writeString(folder.resolve("removed129"),"blocked");var view=call(hub,OWNER,"VIEW",project,Map.of());rejects(()->call(hub,OWNER,"REMOVE_WORKSPACE",project,Map.of("rev",view.get("revision"))));require(call(hub,GUEST,"INBOX131","",Map.of()).get("inbox131").contains(offer.toString()),"Failed deletion removed invitations");require(call(hub,OWNER,"VIEW",project,Map.of()).get("result").equals("OK"),"Failed deletion left a closed room cached");}
        });
        test("committed workspace removal is reported even if offer cleanup fails",()->{
            Path folder=root.resolve("remove-cleanup");try(var hub=new WorkspaceHub122(folder)){String project=create(hub).get("project");invite(hub,project);var view=call(hub,OWNER,"VIEW",project,Map.of());brokenFile(folder,()->require(call(hub,OWNER,"REMOVE_WORKSPACE",project,Map.of("rev",view.get("revision"))).get("result").equals("OK"),"Successful removal reported as failure"));require(!Files.exists(folder.resolve(project)),"Workspace remains active");require(call(hub,GUEST,"INBOX131","",Map.of()).get("inbox131").isEmpty(),"Deleted workspace still offers membership");}
        });
        test("workspace removal immediately notifies other members of access loss",()->{try(var hub=new WorkspaceHub122(root.resolve("removed-push"))){String project=create(hub).get("project");member(hub,project,"DEVELOPER","","");var view=call(hub,OWNER,"VIEW",project,Map.of());call(hub,OWNER,"REMOVE_WORKSPACE",project,Map.of("rev",view.get("revision")));var events=hub.events130(OWNER,project,"REMOVE_WORKSPACE","");require(events.size()==1&&events.get(0).recipient().equals(GUEST)&&events.get(0).data().get("result").equals("DENIED"),"Peer kept an apparently active workspace");require(hub.events130(OWNER,project,"REMOVE_WORKSPACE","").isEmpty(),"Deletion events repeated");}});
        test("all workspace creation variants require mayCreate",()->{try(var hub=new WorkspaceHub122(root.resolve("creation"))){for(String op:List.of("CREATE","CREATE_SCRIPT","CREATE_COMMAND133")){var q=new TreeMap<String,String>();q.put("op",op);q.put("project","");q.put("request",UUID.randomUUID().toString());if(op.equals("CREATE"))q.put("text","Room");else{q.put("source","Map");q.put("data",WorkspaceProject126.pack(Map.of("main.mr","say hi")));}rejects(()->hub.execute(GUEST,false,"Guest",q));}}});
    }
    private static void replicas()throws Exception{
        test("older document snapshot cannot roll back visible text",()->{var empty=new CollaborativeDocument121();var replica=new WorkspaceReplica122(OWNER,empty.snapshot());empty.apply(empty.replace(OWNER,0,0,"new"));replica.merge(empty.snapshot());replica.merge(new CollaborativeDocument121().snapshot());require(replica.text().equals("new"),"Stale snapshot replaced newer state");});
        test("invalid acknowledgement retains the entire local queue",()->{var replica=new WorkspaceReplica122(OWNER,new CollaborativeDocument121().snapshot());replica.grant("lease",1,128);require(replica.prepare("a")&&replica.prepare("ab"),"Prepare failed");UUID request=replica.first().request();rejects(()->replica.acknowledge(request,new CollaborativeDocument121().snapshot()));require(replica.pendingCount130()==2&&replica.first().request().equals(request)&&replica.text().equals("ab"),"Failed ACK destroyed pending batch");});
        test("duplicate lease does not double-count available clocks",()->{var replica=new WorkspaceReplica122(OWNER,new CollaborativeDocument121().snapshot());replica.grant("lease",1,128);replica.grant("lease",1,128);require(!replica.prepare("x".repeat(129)),"Duplicate lease reused clocks");require(!replica.pending()&&replica.text().isEmpty(),"Failed preflight changed local state");});
        test("stale ACK preserves a newer snapshot with foreign changes",()->{var base=new CollaborativeDocument121();var replica=new WorkspaceReplica122(OWNER,base.snapshot());replica.grant("lease",1,128);replica.prepare("a");var pending=replica.first();base.apply(WorkspaceCodec122.batch(OWNER,pending.batch()));var ack=base.snapshot();base.apply(base.replace(GUEST,1,1,"b"));replica.merge(base.snapshot());replica.acknowledge(pending.request(),ack);require(replica.text().equals("ab")&&!replica.pending(),"Stale ACK erased a peer edit");});
    }
    private static void migration()throws Exception{
        test("maximum legacy filename is migrated with a recorded mapping",()->{Path old=root.resolve("long-name"),destination=root.resolve("long-name-map");var repository=new TextRepository(old);String name="n".repeat(64);repository.create(name,"say long");CommandProjects133.migrate(destination,old);var projects=new SourceProjects(destination);var files=projects.files("LegacyCommands");require(files.size()==1&&projects.read("LegacyCommands",files.get(0)).equals("say long"),"Long name was lost");require(Files.exists(destination.resolve("LegacyCommands/.genesis/migration-paths135.properties")),"Name mapping missing");require(repository.read(name).equals("say long"),"Original changed");});
        test("maximum legacy path remains readable after adding .mr",()->{Path old=root.resolve("long-path"),destination=root.resolve("long-path-map");var repository=new TextRepository(old);String parent="";for(String name:List.of("a".repeat(64),"b".repeat(64),"c".repeat(64),"d".repeat(57))){parent=RepositoryPath.child(parent,name);repository.createDirectory(parent);}repository.create(parent+"/x","say deep");CommandProjects133.migrate(destination,old);var projects=new SourceProjects(destination);String file=projects.files("LegacyCommands").get(0);require(ProjectPath.valid(file)&&projects.read("LegacyCommands",file).equals("say deep"),"Deep path cannot roundtrip");});
        test("legacy document and .mr folder both survive physical-name collision",()->{Path old=root.resolve("name-collision"),destination=root.resolve("collision-map");var repository=new TextRepository(old);repository.create("main","say root");repository.createDirectory("main.mr");repository.create("main.mr/child","say child");CommandProjects133.migrate(destination,old);var projects=new SourceProjects(destination);var texts=new HashSet<String>();for(String path:projects.files("LegacyCommands"))texts.add(projects.read("LegacyCommands",path));require(texts.equals(Set.of("say root","say child")),"Colliding source was lost");});
        test("malformed existing project is not overwritten during migration",()->{Path old=root.resolve("malformed-old"),destination=Files.createDirectories(root.resolve("malformed-map/LegacyCommands"));Files.writeString(destination.resolve("keep.bin"),"keep");new TextRepository(old).create("main","say hello");CommandProjects133.migrate(destination.getParent(),old);require(Files.readString(destination.resolve("keep.bin")).equals("keep"),"Existing folder overwritten");require(new SourceProjects(destination.getParent()).project("LegacyCommands2")!=null,"No safe migration target");});
        test("invalid migration marker is reported rather than silently accepted",()->{Path old=root.resolve("marker-old"),destination=Files.createDirectories(root.resolve("marker-map/.legacy-commands133"));new TextRepository(old).create("main","say hi");rejects(()->CommandProjects133.migrate(destination.getParent(),old));});
        test("migration refuses an overlapping source and destination",()->{Path old=root.resolve("overlap");var repository=new TextRepository(old);repository.create("main","keep");rejects(()->CommandProjects133.migrate(old.resolve("nested"),old));require(repository.read("main").equals("keep"),"Original changed");});
        test("renamed migration remains idempotent after marker loss",()->{Path old=root.resolve("repeat-old"),destination=root.resolve("repeat-map");new TextRepository(old).create("n".repeat(64),"original");CommandProjects133.migrate(destination,old);Files.delete(destination.resolve(".legacy-commands133"));CommandProjects133.migrate(destination,old);require(new SourceProjects(destination).list().projects().size()==1,"Repeated migration duplicated project");});
    }
    private static void formats()throws Exception{
        test("transport rejects malformed UTF-16 instead of replacing it",()->{rejects(()->WorkspaceCodec122.encode(Map.of("text","\uD800"),1024));});
        test("transport roundtrips Unicode and refuses bounded oversized values",()->{String value="Привет 😀";require(WorkspaceCodec122.decode(WorkspaceCodec122.encode(Map.of("text",value),1024),1024).get("text").equals(value),"Unicode changed");rejects(()->WorkspaceCodec122.encode(Map.of("text","я".repeat(600)),1024));});
        test("source chunks require canonical base64",()->{var file=new WorkspaceRelease121.File("main.mr",1,new byte[]{'f'});var files=Map.of(file.path(),file);var download=new WorkspaceSources125.Download(WorkspaceRelease121.digest(files),WorkspaceSources125.manifest(files));rejects(()->download.accept("main.mr",0,"Zh=="));});
        test("command files accept BOM and CR-only line endings",()->{var plan=CommandFilePlan.parse("\uFEFF#persistent\rsay one\rsay two\r");require(plan.persistent()&&plan.parts().size()==2&&plan.parts().get(1).line()==3,"BOM/CR lines were misparsed");});
        test("project search preserves CR-only line boundaries",()->{var result=DocumentSearch.search(List.of("file.mr"),p->"one\rtwo\rthree","two",DocumentSearch.Mode.CONTENT,true,()->false);require(result.hits().size()==1&&result.hits().get(0).line()==1&&result.hits().get(0).column()==0,"Search returned wrong location");});
        test("single repository creation refuses portable case aliases",()->{var repository=new TextRepository(root.resolve("portable"));repository.create("Mixed","keep");rejects(()->repository.create("mixed","other"));require(repository.read("Mixed").equals("keep"),"Original changed");});
        test("single repository folder creation refuses portable case aliases",()->{var repository=new TextRepository(root.resolve("portable-folders"));repository.createDirectory("Mixed");rejects(()->repository.createDirectory("mixed"));});
        test("single repository rename refuses a case-aliased destination",()->{var repository=new TextRepository(root.resolve("portable-rename"));repository.create("Mixed","keep");repository.create("other","source");rejects(()->repository.rename("other","mixed"));require(repository.read("other").equals("source"),"Rename destroyed source");});
        test("scheduler starts when monotonic clock is negative",()->{var schedule=new WorkspaceSchedule130();require(schedule.next(-1000000000L,true,false,false,false,false)==WorkspaceSchedule130.Lane.EDIT,"Initial negative clock starved all lanes");require(schedule.next(-999999999L,true,false,false,false,false)==null,"Interval not enforced");require(schedule.next(-900000000L,true,false,false,false,false)==WorkspaceSchedule130.Lane.EDIT,"Negative deadline never became due");});
        test("scheduler deadlines survive monotonic clock wrap",()->{var schedule=new WorkspaceSchedule130();long now=Long.MAX_VALUE-50000000;require(schedule.next(now,true,false,false,false,false)!=null,"Initial request not scheduled");require(schedule.next(now+1000000,true,false,false,false,false)==null,"Wrapped deadline triggered too early");require(schedule.next(now+100000000,true,false,false,false,false)!=null,"Wrapped deadline stalled");});
        test("log rotation does not overwrite the active file for a custom log name",()->{Path log=root.resolve("mared-workspace.previous.log");Files.writeString(log,"x".repeat(1024*1024+1));WorkspaceFailure133.report(log,new IOException("second"));require(Files.size(log)<10000&&Files.exists(log.resolveSibling("mared-workspace.previous.log.previous")),"Custom log rotated into itself");});
    }
    public static void main(String[] args)throws Exception{
        root=Files.createTempDirectory("audit135-");try{invitations();hub();replicas();migration();formats();}finally{try(var files=Files.walk(root)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
        System.out.println("Audit135 scenarios passed="+passed+" failed="+failed);if(failed>0)throw new AssertionError("Audit failures: "+failed);
    }
}
