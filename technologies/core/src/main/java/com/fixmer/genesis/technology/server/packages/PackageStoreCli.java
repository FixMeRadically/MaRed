package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Public staging/status and NEW diagnostic demo. Deliberately no apply/recover command for existing games. */
public final class PackageStoreCli {
    private PackageStoreCli(){}
    public static void main(String[] args){
        try{
            if(args.length==2&&args[0].equals("demo")){demo(Path.of(args[1]));return;}
            if(args.length==8&&args[0].equals("stage")){
                var key=PackageFiles.publicKey(Path.of(args[2]));var target=target(args[3],args[4],args[5],args[6]);var stage=PackageStaging.prepare(Path.of(args[7]),Path.of(args[1]),Map.of(PackageFiles.keyId(key),key),target);
                System.out.println(PackageCli.json(Map.of("report","package_store_v39","ok",true,"release",stage.verified().manifest().release(),"archive",stage.verified().archiveSha256(),"writtenObjects",stage.writtenObjects(),"reusedObjects",stage.reusedObjects(),"gameFilesChanged",false)));return;
            }
            if(args.length==8&&args[0].equals("status")){
                Path profile=Path.of(args[1]);var key=PackageFiles.publicKey(Path.of(args[3]));var target=target(args[4],args[5],args[6],args[7]);String[] marker=InstallIo.unframe(PackageFiles.read(profile.resolve(".genesis-install/sandbox"),1024),5).split("\n");
                if(!marker[0].equals("GENESIS-INSTALL-SANDBOX-1")||!marker[2].equals(target.distribution().toString())||!marker[3].equals(target.side().name())||!marker[4].equals(target.minecraft()))throw new IOException("NOT_MATCHING_DIAGNOSTIC_PROFILE");
                UUID id=UUID.fromString(marker[1]);Path receipt=profile.resolve(".genesis-install/installed");boolean pending=Files.exists(profile.resolve(".genesis-install/active"),LinkOption.NOFOLLOW_LINKS);ProfileCoordinator.Snapshot admission=null;if(Files.exists(profile.resolve(".genesis-admission"),LinkOption.NOFOLLOW_LINKS)){admission=ProfileCoordinator.inspect(profile);if(!admission.profile().equals(id)||admission.scope()!=ProfileCoordinator.Scope.DIAGNOSTIC_NO_GAME)throw new IOException("ADMISSION_PROFILE_OR_SCOPE_MISMATCH");pending=pending||admission.phase()!=ProfileCoordinator.Phase.IDLE;}
                InstalledReceipt installed=Files.exists(receipt,LinkOption.NOFOLLOW_LINKS)?InstalledReceipt.read(receipt,Path.of(args[2]),Map.of(PackageFiles.keyId(key),key),target,id):null;
                var result=new LinkedHashMap<String,Object>();result.put("report","package_store_v39");result.put("diagnosticOnly",true);result.put("readOnly",true);result.put("profile",id);result.put("state",pending?"recovery_required":installed==null?"empty":"receipt_recorded");result.put("recordedRelease",installed==null?null:installed.verified().manifest().release());result.put("gameLaunchAllowed",false);result.put("ownerEpoch",admission==null?0:admission.epoch());result.put("admissionPhase",admission==null?"not_initialized":admission.phase());System.out.println(PackageCli.json(result));return;
            }
            throw new IllegalArgumentException("Usage: demo <NEW-directory> | stage <zip> <pinned-public.der> <distributionUUID> <client|server> <minecraft> <neoforge> <cache> | status <diagnostic-profile> <cache> <pinned-public.der> <distributionUUID> <client|server> <minecraft> <neoforge>. No live-game apply command.");
        }catch(Exception bad){System.err.println(PackageCli.json(Map.of("ok",false,"error",bad.getMessage()==null?bad.getClass().getSimpleName():bad.getMessage(),"gameFilesChanged",false)));System.exit(2);}
    }
    private static Target target(String distribution,String side,String minecraft,String loader){return new Target(UUID.fromString(distribution),switch(side){case "client"->Side.CLIENT;case "server"->Side.SERVER;default->throw new IllegalArgumentException("TARGET_SIDE_REQUIRED");},minecraft,loader,PROTOCOL);}
    private static void demo(Path root)throws Exception{
        root=root.toAbsolutePath().normalize();PackageFiles.safe(root);Files.createDirectory(root);PackageFiles.privateMode(root,true);var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Files.write(root.resolve("public.der"),key.getPublic().getEncoded(),StandardOpenOption.CREATE_NEW);PackageFiles.privateMode(root.resolve("public.der"),false);var trust=Map.of(PackageFiles.keyId(key.getPublic()),key.getPublic());UUID distribution=UUID.randomUUID(),first=UUID.randomUUID();var target=new Target(distribution,Side.CLIENT,"1.21.1","21.1.demo",1);
        Path oldSource=root.resolve("old.jar"),newSource=root.resolve("new.jar");Files.writeString(oldSource,"Diagnostic old payload; not a Minecraft mod");Files.writeString(newSource,"Diagnostic NEW payload; not a Minecraft mod");
        var oldEntry=new Entry("mods/example.jar",Side.BOTH,Files.size(oldSource),PackageFiles.digest(oldSource,MAX_FILE));var newEntry=new Entry("mods/example.jar",Side.BOTH,Files.size(newSource),PackageFiles.digest(newSource,MAX_FILE));
        var old=new Manifest(distribution,first,null,"demo-1","1.21.1","neoforge","21.1.demo",1,1,PackageFiles.keyId(key.getPublic()),List.of(oldEntry));var next=new Manifest(distribution,UUID.randomUUID(),first,"demo-2","1.21.1","neoforge","21.1.demo",1,1,PackageFiles.keyId(key.getPublic()),List.of(newEntry));
        Path one=root.resolve("one.zip"),two=root.resolve("two.zip");PackageArchive.create(one,old,Map.of(oldEntry.path(),oldSource),key.getPrivate(),key.getPublic());PackageArchive.create(two,next,Map.of(newEntry.path(),newSource),key.getPrivate(),key.getPublic());Path cache=root.resolve("cache");var a=PackageStaging.prepare(cache,one,trust,target);var b=PackageStaging.prepare(cache,two,trust,target);var reused=PackageStaging.prepare(cache,two,trust,target);
        try(var session=SandboxInstaller.create(root.resolve("diagnostic-profile"),cache,trust,target)){
            session.apply(a);session.apply(b);var receipt=session.receipt();System.out.println(PackageCli.json(Map.of("report","install_sandbox_v39","diagnosticOnly",true,"distribution",distribution,"release",receipt.verified().manifest().release(),"receiptVerified",true,"reusedObjects",reused.reusedObjects(),"pendingTransaction",Files.exists(session.meta.resolve("active")),"minecraftStarted",false,"realGameFilesChanged",false,"ownerEpoch",session.admission().epoch())));
        }
    }
}
