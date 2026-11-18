package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;import java.util.*;import java.util.zip.*;
/** Actual crypto/archives and separate-JVM control steps; game/save/Job are explicit test inputs. */
public final class FolderChecks118 {
 static void jar(Path file,String id,String version,boolean boot)throws Exception{
  Files.createDirectories(file.getParent());try(var z=new ZipOutputStream(Files.newOutputStream(file))){
   z.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));z.write(("modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\""+id+"\"\nversion=\""+version+"\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();
   z.putNextEntry(new ZipEntry("version.txt"));z.write(version.getBytes());z.closeEntry();
   if(boot){z.putNextEntry(new ZipEntry("com/fixmer/mared/Mared.class"));z.write(new byte[]{0});z.closeEntry();z.putNextEntry(new ZipEntry("com/fixmer/mared/technology/server/firstlaunch/RemoteClient70.class"));z.write(new byte[]{0});z.closeEntry();}
  }
 }
 public static void main(String[] args)throws Exception{
  Path base=Path.of(args[1]),server=base.resolve("server"),root=server.resolve(".genesis/server104"),id=root.resolve("publisher73");String op=args[0];
  if(op.startsWith("setup")){
   jar(server.resolve("mods/mared.jar"),"mared","1",true);jar(server.resolve("mods/demo.jar"),"demo","1",false);
   Path c=Files.createDirectories(base.resolve("clientMods")),s=Files.createDirectories(base.resolve("serverMods"));jar(c.resolve("demo.jar"),"demo","1",false);jar(s.resolve("demo.jar"),"demo","1",false);
   Publication83.initialize(id);var p=Publication83.preview(id,root.resolve("releases"),c,s,"r1",3600,"localhost:25565","https://localhost:8443/","1.21.1","21.1.264");Publication83.author(id,root.resolve("releases"),c,s,"r1",3600,"localhost:25565","https://localhost:8443/","1.21.1","21.1.264",p.get("ticket").toString());
   if(op.equals("setup-baseline120"))return;
   jar(c.resolve("demo.jar"),"demo","2",false);jar(s.resolve("demo.jar"),"demo",op.equals("setup-client-only")?"1":"2",false);
   var n=Publication79.preview(id,root.resolve("releases/r1/public"),c,s,"r2",3600);Publication79.author(id,root.resolve("releases/r1/public"),c,s,root.resolve("releases"),"r2",3600,n.get("ticket").toString());return;
  }
  byte[] key=Files.readAllBytes(root.resolve("owner74.key"));var session=LocalApply67.verify(key,Files.readAllBytes(FolderRuntime118.channel(root,"1").resolve("server-1.session")));
  if(op.equals("consent")){
   var offer=DedicatedRelease74.verify(root.resolve("releases/r2"),Files.readAllBytes(id.resolve("public.der"))).offer();FolderRuntime118.prepared(root,session,offer);
   var accounts=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());var room=ControlRoom73.create(root.resolve("control73"),id,offer,UUID.randomUUID(),UUID.randomUUID(),accounts);
   for(UUID account:accounts){var m=room.member(account,true);room.decision(account,"ACCEPTED");room.receipt(LocalApply67.sign(m.secret,Map.of("schema","73","round",room.round.toString(),"client",m.id.toString(),"request",UUID.randomUUID().toString(),"action","PREPARED","archive",offer.archiveSha(),"inventory","a".repeat(64))));}room.publish(System.currentTimeMillis());return;
  }
  if(op.equals("publish")){byte[] data=new BootstrapPublisher76(root).publish(System.currentTimeMillis());var state=Bootstrap76.verify(data,Files.readAllBytes(id.resolve("public.der")),"localhost:25565","https://localhost:8443/","1.21.1","21.1.264",System.currentTimeMillis());if(!state.phase().equals("READY")||!state.values().get("generation").equals("2"))throw new AssertionError("target bootstrap missing");return;}
  var room=ControlRoom73.restore(root.resolve("control73"),id);
  if(op.equals("commit"))room.commit();
  else if(op.equals("cancel"))room.cancel();
  else if(op.equals("install"))for(var m:room.members())room.receipt(LocalApply67.sign(m.secret,Map.of("schema","73","round",room.round.toString(),"client",m.id.toString(),"request",UUID.randomUUID().toString(),"action","INSTALLED","archive",room.offer.archiveSha(),"inventory",m.inventory)));
  else if(op.equals("joined"))for(var m:room.members())room.receipt(LocalApply67.sign(m.secret,Map.of("schema","73","round",room.round.toString(),"client",m.id.toString(),"request",UUID.randomUUID().toString(),"action","JOINED","archive",room.offer.archiveSha(),"inventory",m.inventory)));
  else if(op.equals("unprepared")){var o=room.offer;try{FolderRuntime118.prepared(root,session,o);throw new AssertionError("accepted changed prepared set");}catch(java.io.IOException expected){}return;}
  else throw new IllegalArgumentException(op);
  room.publish(System.currentTimeMillis());
 }
}
