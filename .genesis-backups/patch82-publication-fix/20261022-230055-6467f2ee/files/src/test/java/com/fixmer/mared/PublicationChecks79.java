package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import com.fixmer.genesis.technology.server.packages.*;
import java.nio.file.*;import java.security.*;import java.security.spec.*;import java.util.*;import java.util.zip.*;

/** Actual Ed25519 and package bytes; no game, native Job or UI claims. */
public class PublicationChecks79 {
 public static class LockProbe {
  public static void main(String[] args)throws Exception{try(var c=java.nio.channels.FileChannel.open(Path.of(args[0]),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=c.lock()){System.out.println("READY");System.out.flush();System.in.read();}}
 }
 static int checks;
 static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
 interface Work{void run()throws Exception;}
 static void rejects(Work w)throws Exception{try{w.run();}catch(Exception expected){checks++;return;}throw new AssertionError("accepted invalid input");}
 static void jar(Path p,String value)throws Exception{try(var z=new ZipOutputStream(Files.newOutputStream(p))){z.putNextEntry(new ZipEntry("test.txt"));z.write(value.getBytes());z.closeEntry();}}
 static String ticket(Path id,Path seed,Path c,Path s,String label)throws Exception{return (String)Publication79.preview(id,seed,c,s,label,3600).get("ticket");}
 public static void main(String[] args)throws Exception{
  Path root=args.length==0?Files.createTempDirectory("publication79-test"):Files.createDirectories(Path.of(args[0])),id=root.resolve("identity"),c=Files.createDirectory(root.resolve("client")),s=Files.createDirectory(root.resolve("server")),releases=Files.createDirectory(root.resolve("releases"));
  try{
  RemotePublisher69.initialize(id,"localhost:25565","https://localhost:8443/");var key=RemoteOffer69.publicKey(Files.readAllBytes(id.resolve("public.der")));var secret=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(id.resolve("private.der"))));String signer=FirstLaunchFiles.sha(key.getEncoded());UUID distribution=UUID.randomUUID(),release=UUID.randomUUID();
  jar(c.resolve("client.jar"),"client-v1");jar(s.resolve("server.jar"),"server-v1");Path seed=Files.createDirectory(root.resolve("seed"));var e=new PackageModel.Entry("mods/client.jar",PackageModel.Side.CLIENT,Files.size(c.resolve("client.jar")),FirstLaunchFiles.hash(c.resolve("client.jar"),PackageModel.MAX_FILE));var m=new PackageModel.Manifest(distribution,release,null,"seed","1.21.1","neoforge","21.1.264",1,1,signer,List.of(e));Path archive=seed.resolve("client.zip");PackageArchive.create(archive,m,Map.of(e.path(),c.resolve("client.jar")),secret,key);String hash=FirstLaunchFiles.hash(archive,PackageModel.MAX_ARCHIVE);Files.move(archive,seed.resolve("client-"+hash+".zip"));long now=System.currentTimeMillis();
  // An authentic expired baseline may seed NEW authoring, but is never accepted by network verification.
  var old=new RemoteOffer69("localhost:25565","https://localhost:8443/",distribution,release,null,"1.21.1","21.1.264",7,now-120000,now-60000,Files.size(seed.resolve("client-"+hash+".zip")),hash,signer);Files.write(seed.resolve("offer.bin"),old.encode());Files.write(seed.resolve("offer.sig"),old.sign(secret));Files.write(seed.resolve("public.der"),key.getEncoded());
  rejects(()->RemoteUpdateCli69.verifyPublic(seed));ok(RemoteUpdateCli69.verifyPublic(seed,false).equals(old));ok(PublicationStatus79.inspect(seed,now).get("state").equals("EXPIRED"));
  var preview=Publication79.preview(id,seed,c,s,"r1",3600);ok(preview.get("sequence").equals(8L));ok(!Files.exists(id.resolve("counter79")));String originalTicket=(String)preview.get("ticket");
  jar(c.resolve("client.jar"),"changed");rejects(()->Publication79.author(id,seed,c,s,releases,"r1",3600,originalTicket));ok(!Files.exists(releases.resolve("r1")));ok(!Files.exists(id.resolve("counter79")));
  String t=ticket(id,seed,c,s,"r1");var created=Publication79.author(id,seed,c,s,releases,"r1",3600,t);var pair=DedicatedRelease74.verify(releases.resolve("r1"),key.getEncoded());ok(pair.offer().sequence()==8);ok(pair.offer().parent().equals(release));ok(pair.archive().manifest().files().get(0).side()==PackageModel.Side.SERVER);ok(pair.offer().expires()>now);ok(pair.offer().distribution().equals(distribution));ok(created.get("release").equals(pair.offer().release().toString()));ok(Files.exists(releases.resolve("r1/snapshot79")));ok(ControlWire73.verify(key,Files.readAllBytes(releases.resolve("r1/snapshot79"))).get("ticket").equals(t));
  String t2=ticket(id,seed,c,s,"r2");Publication79.author(id,seed,c,s,releases,"r2",3600,t2);ok(DedicatedRelease74.verify(releases.resolve("r2"),key.getEncoded()).offer().sequence()==9);rejects(()->Publication79.author(id,seed,c,s,releases,"r3",3600,t2));
  String labelTicket=ticket(id,seed,c,s,"r1");rejects(()->Publication79.author(id,seed,c,s,releases,"r1",3600,labelTicket));
  Path add=c.resolve("added.jar");String beforeAdd=ticket(id,seed,c,s,"r3");jar(add,"new");rejects(()->Publication79.author(id,seed,c,s,releases,"r3",3600,beforeAdd));Files.delete(add);
  String beforeDelete=ticket(id,seed,c,s,"r3");Files.delete(c.resolve("client.jar"));rejects(()->Publication79.author(id,seed,c,s,releases,"r3",3600,beforeDelete));jar(c.resolve("client.jar"),"restored");
  Path text=c.resolve("readme.txt");Files.writeString(text,"not a jar");rejects(()->ticket(id,seed,c,s,"r3"));Files.delete(text);
  Path nested=Files.createDirectory(c.resolve("nested"));rejects(()->ticket(id,seed,c,s,"r3"));Files.delete(nested);
  Path collision=c.resolve("CLIENT.jar");jar(collision,"case");rejects(()->ticket(id,seed,c,s,"r3"));Files.delete(collision);
  Path boot=c.resolve("mared.jar");try(var z=new ZipOutputStream(Files.newOutputStream(boot))){z.putNextEntry(new ZipEntry("com/fixmer/mared/Mared.class"));z.write(new byte[]{0});z.closeEntry();}ok(((List<?>)Publication79.preview(id,seed,c,s,"r3",3600).get("excludedClient")).size()==1);
  String bt=ticket(id,seed,c,s,"r3");Files.copy(boot,c.resolve("mared2.jar"));rejects(()->ticket(id,seed,c,s,"r3"));Files.delete(c.resolve("mared2.jar"));Publication79.author(id,seed,c,s,releases,"r3",3600,bt);var finalOffer=RemoteUpdateCli69.verifyPublic(releases.resolve("r3/public"));try(var zip=new ZipFile(releases.resolve("r3/public/client-"+finalOffer.archiveSha()+".zip").toFile())){ok(zip.getEntry("files/mods/mared.jar")==null);}
  String counter=FirstLaunchFiles.hash(id.resolve("counter79"),16384);rejects(()->Publication79.preview(id,seed,c,s,"../escape",3600));rejects(()->Publication79.preview(id,seed,c,s,"r4",59));ok(counter.equals(FirstLaunchFiles.hash(id.resolve("counter79"),16384)));
  byte[] ledger=Files.readAllBytes(id.resolve("counter79"));Files.writeString(id.resolve("counter79"),"tampered");rejects(()->ticket(id,seed,c,s,"r4"));Files.write(id.resolve("counter79"),ledger);
  Path other=Files.createDirectory(root.resolve("other"));var otherKey=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Files.write(other.resolve("public.der"),otherKey.getPublic().getEncoded());rejects(()->ticket(other,seed,c,s,"r4"));
  Path inherited=Files.createDirectory(root.resolve("inherited"));for(String name:List.of("public.der","private.der","counter79"))Files.copy(id.resolve(name),inherited.resolve(name));ok(Publication79.preview(inherited,seed,c,s,"r4",3600).get("sequence").equals(11L));
  Files.writeString(root.resolve("recovered77"),"claimed");String last=ticket(id,seed,c,s,"r4");rejects(()->Publication79.author(id,seed,c,s,releases,"r4",3600,last));Files.delete(root.resolve("recovered77"));
  Files.writeString(root.resolve("import77.pending"),"pending");rejects(()->Publication79.author(id,seed,c,s,releases,"r4",3600,last));Files.delete(root.resolve("import77.pending"));
  System.out.println("Publication79 actual Ed25519/package/snapshot checks passed: "+checks);
  }finally{if(args.length==0)try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
