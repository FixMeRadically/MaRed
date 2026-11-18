package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;import java.nio.channels.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.zip.*;

/** Local authoring from complete immutable input snapshots. Never installs or starts a game. */
public final class Publication79 {
 private Publication79(){}
 record Set79(Path directory,List<PackageModel.Entry> entries,Map<String,Path> sources,List<String> excluded,String seal){}
 private record Input(RemoteOffer69 seed,Set79 client,Set79 server,byte[] head,long sequence,String ticket){}
 private static IOException fail(String code){return new IOException("PUBLICATION79_"+code);}
 static Set79 scan(Path directory,PackageModel.Side side)throws Exception{
  directory=FirstLaunchFiles.safe(directory);if(!Files.isDirectory(directory))throw fail("MOD_DIRECTORY");
  var entries=new ArrayList<PackageModel.Entry>();var sources=new TreeMap<String,Path>();var excluded=new ArrayList<String>();long total=0;int count=0;
  try(var stream=Files.newDirectoryStream(directory)){for(Path file:stream){
   if(++count>PackageModel.MAX_FILES+1)throw fail("LIMIT");FirstLaunchFiles.safe(file);
   if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!file.getFileName().toString().endsWith(".jar"))throw fail("JAR_SET");
   String name="mods/"+file.getFileName();long size=Files.size(file);if(size<1||size>PackageModel.MAX_FILE||(total+=size)>PackageModel.MAX_TOTAL)throw fail("LIMIT");
   // Validate even excluded bootstrap filenames and detect corruption before preview succeeds.
   var entry=new PackageModel.Entry(name,side,size,FirstLaunchFiles.hash(file,PackageModel.MAX_FILE));
   try(var jar=new ZipFile(file.toFile())){if(jar.getEntry("com/fixmer/mared/Mared.class")!=null){excluded.add(name);continue;}}
   entries.add(entry);sources.put(name,file);
  }}
  if(excluded.size()>1)throw fail("BOOTSTRAP_DUPLICATE");
  // Manifest validation also rejects case collisions and reserved names.
  var dummy=UUID.fromString("00000000-0000-0000-0000-000000000079");
  entries=new ArrayList<>(new PackageModel.Manifest(dummy,UUID.randomUUID(),null,"snapshot79","1.21.1","neoforge","21.1.264",1,1,"0".repeat(64),entries).files());
  Collections.sort(excluded);var text=new StringBuilder(directory.toString()).append('\n');for(var e:entries)text.append(e.path()).append(':').append(e.bytes()).append(':').append(e.sha256()).append('\n');
  // Bootstrap is fixed and excluded from packages, but changes still invalidate the user's preview.
  for(String e:excluded)text.append(e).append(':').append(FirstLaunchFiles.hash(directory.resolve(e.substring(5)),PackageModel.MAX_FILE)).append('\n');
  return new Set79(directory,List.copyOf(entries),Map.copyOf(sources),List.copyOf(excluded),FirstLaunchFiles.sha(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
 private static Input input(Path identity,Path seedFolder,Path client,Path server,String label,long ttl)throws Exception{
  if(!label.matches("[A-Za-z0-9_-]{1,40}")||ttl<60||ttl>7*86400)throw fail("ARGUMENTS");
  var seed=RemoteUpdateCli69.verifyPublic(seedFolder,false);if(seed.issued()>System.currentTimeMillis()+120000)throw fail("CLOCK");byte[] pub=FirstLaunchFiles.read(identity.resolve("public.der"),256);if(!Arrays.equals(pub,FirstLaunchFiles.read(seedFolder.resolve("public.der"),256)))throw fail("IDENTITY");
  byte[] head=Files.exists(identity.resolve("counter79"))?FirstLaunchFiles.read(identity.resolve("counter79"),16384):new byte[0];long sequence=seed.sequence();
  if(head.length>0){var h=ControlWire73.verify(RemoteOffer69.publicKey(pub),head);if(!h.keySet().equals(Set.of("schema","distribution","game","endpoint","sequence"))||!"79".equals(h.get("schema"))||!seed.distribution().toString().equals(h.get("distribution"))||!seed.game().equals(h.get("game"))||!seed.endpoint().equals(h.get("endpoint")))throw fail("COUNTER_BINDING");long n=Long.parseLong(h.get("sequence"));if(n<1)throw fail("COUNTER");sequence=Math.max(sequence,n);}
  if(sequence==Long.MAX_VALUE)throw fail("COUNTER");var c=scan(client,PackageModel.Side.CLIENT);var s=scan(server,PackageModel.Side.SERVER);
  var values=Map.of("seed",Base64.getEncoder().encodeToString(seed.encode()),"head",FirstLaunchFiles.sha(head),"client",c.seal,"server",s.seal,"label",label,"ttl",Long.toString(ttl),"identity",identity.toString());
  return new Input(seed,c,s,head,sequence+1,FirstLaunchFiles.sha(ControlWire73.encode(values)));
 }
 public static Map<String,Object> preview(Path identity,Path seed,Path client,Path server,String label,long ttl)throws Exception{
  identity=FirstLaunchFiles.safe(identity);var i=input(identity,seed,client,server,label,ttl);var out=new TreeMap<String,Object>();out.put("ticket",i.ticket);out.put("sequence",i.sequence);out.put("parent",i.seed.release().toString());out.put("endpoint",i.seed.endpoint());out.put("game",i.seed.game());out.put("seedExpires",i.seed.expires());out.put("seedState",i.seed.expires()<=System.currentTimeMillis()?"EXPIRED":"VALID");out.put("ttlSeconds",ttl);
  out.put("client",i.client.entries.stream().map(e->Map.of("path",e.path(),"bytes",e.bytes(),"sha256",e.sha256())).toList());out.put("server",i.server.entries.stream().map(e->Map.of("path",e.path(),"bytes",e.bytes(),"sha256",e.sha256())).toList());out.put("excludedClient",i.client.excluded);out.put("excludedServer",i.server.excluded);return out;
 }
 public static Map<String,Object> author(Path identity,Path seedFolder,Path client,Path server,Path releases,String label,long ttl,String ticket)throws Exception{
  return authorChecked(identity,seedFolder,client,server,releases,label,ttl,ticket,()->{});
 }
 @FunctionalInterface public interface Check84 {void check()throws IOException;}
 public static Map<String,Object> authorChecked(Path identity,Path seedFolder,Path client,Path server,Path releases,String label,long ttl,String ticket,Check84 check)throws Exception{
  return authorChecked(identity,seedFolder,client,server,releases,label,ttl,ticket,check,Map.of());
 }
 public static Map<String,Object> authorChecked(Path identity,Path seedFolder,Path client,Path server,Path releases,String label,long ttl,String ticket,Check84 check,Map<String,String> evidence)throws Exception{
  evidence=Map.copyOf(evidence);if(!evidence.isEmpty()&&(!"84".equals(evidence.get("schema"))||evidence.containsKey("release")))throw fail("EVIDENCE");check.check();
  identity=FirstLaunchFiles.safe(identity);releases=FirstLaunchFiles.safe(releases);if(!Files.isDirectory(releases)||!ticket.matches("[a-f0-9]{64}"))throw fail("ARGUMENTS");Path output=FirstLaunchFiles.safe(releases.resolve(label));
  try(var channel=FileChannel.open(FirstLaunchFiles.safe(identity.resolve("publisher.lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var lock=channel.lock()){
   if(Files.exists(identity.getParent().resolve("recovered77")))throw fail("RECOVERED_ROOT");if(Files.exists(identity.getParent().resolve("import77.pending")))throw fail("INCOMPLETE_ROOT");
   var i=input(identity,seedFolder,client,server,label,ttl);if(!i.ticket.equals(ticket))throw fail("INPUT_CHANGED");if(Files.exists(output))throw fail("LABEL_EXISTS");
   byte[] pub=FirstLaunchFiles.read(identity.resolve("public.der"),256);var key=RemoteOffer69.publicKey(pub);var secret=DedicatedRelease74.privateKey(identity);var seed=i.seed;UUID release=UUID.randomUUID();
   Path stage=Files.createTempDirectory(releases,".author79-");FirstLaunchFiles.privateMode(stage);boolean exposed=false;
   try{
    Path publicDir=stage.resolve("public");Files.createDirectory(publicDir);FirstLaunchFiles.privateMode(publicDir);
    var cm=new PackageModel.Manifest(seed.distribution(),release,seed.release(),"publication79",seed.minecraft(),"neoforge",seed.neoforge(),1,1,seed.signer(),i.client.entries);
    Path archive=publicDir.resolve("client.zip");PackageArchive.create(archive,cm,i.client.sources,secret,key);String archiveSha=FirstLaunchFiles.hash(archive,PackageModel.MAX_ARCHIVE);long now=System.currentTimeMillis();
    var offer=new RemoteOffer69(seed.game(),seed.endpoint(),seed.distribution(),release,seed.release(),seed.minecraft(),seed.neoforge(),i.sequence,now,now+ttl*1000,Files.size(archive),archiveSha,seed.signer());Files.move(archive,publicDir.resolve("client-"+archiveSha+".zip"));
    FirstLaunchFiles.write(publicDir.resolve("public.der"),pub,false);FirstLaunchFiles.write(publicDir.resolve("offer.bin"),offer.encode(),false);FirstLaunchFiles.write(publicDir.resolve("offer.sig"),offer.sign(secret),false);
    var sm=new PackageModel.Manifest(seed.distribution(),release,seed.release(),"publication79",seed.minecraft(),"neoforge",seed.neoforge(),1,1,seed.signer(),i.server.entries);Path sa=stage.resolve("server.zip");PackageArchive.create(sa,sm,i.server.sources,secret,key);
    FirstLaunchFiles.write(stage.resolve("pair74"),ControlWire73.sign(secret,Map.of("schema","74","release",release.toString(),"client",archiveSha,"server",FirstLaunchFiles.hash(sa,PackageModel.MAX_ARCHIVE),"distribution",seed.distribution().toString(),"minecraft",seed.minecraft(),"neoforge",seed.neoforge(),"signer",seed.signer())),false);
    DedicatedRelease74.verify(stage,pub);
    // Re-read both complete source sets, seed and counter after packaging, before exposing anything.
    if(!input(identity,seedFolder,client,server,label,ttl).ticket.equals(ticket))throw fail("INPUT_CHANGED");
    var receipt=new TreeMap<String,String>();receipt.putAll(Map.of("schema","79","ticket",ticket,"release",release.toString(),"parent",seed.release().toString(),"sequence",Long.toString(i.sequence),"clientSeal",i.client.seal,"serverSeal",i.server.seal));FirstLaunchFiles.write(stage.resolve("snapshot79"),ControlWire73.sign(secret,receipt),false);
    if(!evidence.isEmpty()){var provenance=new TreeMap<>(evidence);provenance.put("release",release.toString());FirstLaunchFiles.write(stage.resolve("script84"),ControlWire73.sign(secret,provenance),false);}
    check.check();
    // Persist consumed numbers BEFORE exposing output. A failed rename creates a gap, never reuse.
    FirstLaunchFiles.write(identity.resolve("counter79"),ControlWire73.sign(secret,Map.of("schema","79","distribution",seed.distribution().toString(),"game",seed.game(),"endpoint",seed.endpoint(),"sequence",Long.toString(i.sequence))),true);
    Files.move(stage,output);exposed=true;return Map.of("authored",label,"release",release.toString(),"sequence",i.sequence,"expires",offer.expires(),"path",output.toString());
   }finally{if(!exposed)cleanup(stage);}
  }
 }
 private static void cleanup(Path folder){try(var stream=Files.walk(folder)){for(Path p:stream.sorted(Comparator.reverseOrder()).toList())try{Files.deleteIfExists(p);}catch(IOException ignored){}}catch(IOException ignored){}}
 public static void main(String[] a){try{Object out;if(a.length==8&&a[0].equals("preview"))out=preview(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),a[6],Long.parseLong(a[7]));else if(a.length==9&&a[0].equals("author"))out=author(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),Path.of(a[5]),a[6],Long.parseLong(a[7]),a[8]);else throw fail("ARGUMENTS");System.out.println(DedicatedTools74.json(out));}catch(Exception error){String c=error.getMessage();System.err.println(c!=null&&c.matches("PUBLICATION79_[A-Z_]{1,60}")?c:"PUBLICATION79_REJECTED");System.exit(2);}}
}
