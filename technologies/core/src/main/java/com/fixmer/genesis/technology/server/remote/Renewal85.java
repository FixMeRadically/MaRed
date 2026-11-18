package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
/** Local immutable-code renewal. Historical integrity is not an extension of network install authority. */
public final class Renewal85 {
 private Renewal85(){}
 private static IOException fail(String code){return new IOException("RENEWAL85_"+code);}
 private static PackageArchive.Verified client(Path pair,RemoteOffer69 o,byte[] pub)throws Exception{return PackageArchive.verify(pair.resolve("public/client-"+o.archiveSha()+".zip"),Map.of(o.signer(),RemoteOffer69.publicKey(pub)),new PackageModel.Target(o.distribution(),PackageModel.Side.CLIENT,o.minecraft(),o.neoforge(),1));}
 private static String seal(List<PackageModel.Entry> files)throws Exception{var text=new StringBuilder();for(var e:files)text.append(e.path()).append(':').append(e.bytes()).append(':').append(e.sha256()).append('\n');return FirstLaunchFiles.sha(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public static Map<String,Object> inspect(Path id,Path pair)throws Exception{
  byte[] pub=FirstLaunchFiles.read(id.resolve("public.der"),256);var v=DedicatedRelease74.verifyHistorical(pair,pub);var o=v.offer();var c=client(pair,o,pub);var d=new TreeMap<String,Object>();d.putAll(Map.of("release",o.release().toString(),"sequence",o.sequence(),"issued",o.issued(),"expires",o.expires(),"clientSeal",seal(c.manifest().files()),"serverSeal",seal(v.archive().manifest().files()),"signer",o.signer(),"game",o.game(),"endpoint",o.endpoint(),"distribution",o.distribution().toString()));d.put("minecraft",o.minecraft());d.put("neoforge",o.neoforge());return d;
 }
 private static void extract(Path archive,List<PackageModel.Entry> entries,Path folder)throws Exception{
  Files.createDirectory(folder);try(var z=new ZipFile(archive.toFile())){for(var e:entries){Path p=folder.resolve(e.path().substring(5));try(var in=z.getInputStream(z.getEntry("files/"+e.path()));var out=Files.newOutputStream(p,StandardOpenOption.CREATE_NEW)){byte[] b=new byte[65536];long count=0;int n;while((n=in.read(b))!=-1){count+=n;if(count>e.bytes())throw fail("SOURCE_CHANGED");out.write(b,0,n);}if(count!=e.bytes())throw fail("SOURCE_CHANGED");}if(!FirstLaunchFiles.hash(p,PackageModel.MAX_FILE).equals(e.sha256()))throw fail("SOURCE_CHANGED");}}
 }
 public static void same(Path id,Path source,Path target)throws Exception{
  var a=inspect(id,source);var b=inspect(id,target);for(String k:List.of("clientSeal","serverSeal","signer","game","endpoint","distribution","minecraft","neoforge"))if(!a.get(k).equals(b.get(k)))throw fail("CODE_OR_IDENTITY_CHANGED");
 }
 public static boolean head(Path id,Path source,Path releases)throws Exception{return head(id,source,releases,"none");}
 public static boolean head(Path id,Path source,Path releases,String ignored)throws Exception{
  if(!ignored.equals("none")&&!ignored.matches("[A-Za-z0-9_-]{1,40}"))throw fail("ARGUMENTS");
  var current=inspect(id,source);byte[] pub=FirstLaunchFiles.read(id.resolve("public.der"),256);try(var folders=Files.list(releases)){var list=folders.toList();if(list.size()>128)throw fail("RETENTION_REQUIRED");for(Path folder:list){if(folder.getFileName().toString().startsWith(".author"))continue;if(!folder.getFileName().toString().matches("[A-Za-z0-9_-]{1,40}"))throw fail("RELEASE_DIRECTORY");var o=DedicatedRelease74.verifyHistorical(folder,pub).offer();if(!o.distribution().toString().equals(current.get("distribution")))throw fail("IDENTITY");if(o.sequence()>(long)current.get("sequence")){if(folder.getFileName().toString().equals(ignored)){same(id,source,folder);continue;}return false;}}}return true;
 }
 public static Map<String,Object> renew(Path id,Path source,Path releases,String label,long ttl)throws Exception{
  return renew(id,source,releases,label,ttl,"none");
 }
 public static Map<String,Object> renew(Path id,Path source,Path releases,String label,long ttl,String ignored)throws Exception{
  id=FirstLaunchFiles.safe(id);source=FirstLaunchFiles.safe(source);releases=FirstLaunchFiles.safe(releases);if(!label.matches("[A-Za-z0-9_-]{1,40}")||ttl<3600||ttl>604800)throw fail("ARGUMENTS");var before=inspect(id,source);if((long)before.get("issued")>System.currentTimeMillis()+120000)throw fail("CLOCK");
  byte[] pub=FirstLaunchFiles.read(id.resolve("public.der"),256);var v=DedicatedRelease74.verifyHistorical(source,pub);var o=v.offer();var c=client(source,o,pub);Path work=Files.createTempDirectory(releases.getParent(),".renew85-");FirstLaunchFiles.privateMode(work);
  final Path identity=id,seed=source,releaseRoot=releases;
  try{extract(source.resolve("public/client-"+o.archiveSha()+".zip"),c.manifest().files(),work.resolve("client"));extract(source.resolve("server.zip"),v.archive().manifest().files(),work.resolve("server"));
   var p=Publication79.preview(id,source.resolve("public"),work.resolve("client"),work.resolve("server"),label,ttl);
   var made=Publication79.authorChecked(id,source.resolve("public"),work.resolve("client"),work.resolve("server"),releases,label,ttl,(String)p.get("ticket"),()->{try{if(!inspect(identity,seed).equals(before))throw fail("SOURCE_CHANGED");if(!head(identity,seed,releaseRoot,ignored))throw fail("UNAPPLIED_RELEASE");}catch(Exception e){throw new IOException(e.getMessage(),e);}});
   same(id,source,releases.resolve(label));var out=new TreeMap<String,Object>(made);out.put("source",o.release().toString());return out;
  }finally{try(var paths=Files.walk(work)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 public static Map<String,Object> renewReturn120(Path root,Path source,Path failed,Path releases,String label,long ttl)throws Exception{
  root=FirstLaunchFiles.safe(root);Path id=root.resolve("publisher73");var context=ReturnAdmission120.context(root,System.currentTimeMillis());
  if(!failed.getFileName().toString().equals(context.intent().get("failedLabel")))throw fail("RETURN_BINDING");same(id,source,root.resolve("releases").resolve(context.intent().get("label")));ReturnAdmission120.publish(root,System.currentTimeMillis());
  if(!head(id,failed,releases))throw fail("UNAPPLIED_RELEASE");byte[] pub=FirstLaunchFiles.read(id.resolve("public.der"),256);var v=DedicatedRelease74.verifyHistorical(source,pub);var c=client(source,v.offer(),pub);Path work=Files.createTempDirectory(releases.getParent(),".renew120-");FirstLaunchFiles.privateMode(work);
  final Path owner=root;
  try{extract(source.resolve("public/client-"+v.offer().archiveSha()+".zip"),c.manifest().files(),work.resolve("client"));extract(source.resolve("server.zip"),v.archive().manifest().files(),work.resolve("server"));var preview=Publication79.preview(id,failed.resolve("public"),work.resolve("client"),work.resolve("server"),label,ttl);
   var made=Publication79.authorChecked(id,failed.resolve("public"),work.resolve("client"),work.resolve("server"),releases,label,ttl,(String)preview.get("ticket"),()->{try{ReturnAdmission120.publish(owner,System.currentTimeMillis());if(!head(id,failed,releases))throw fail("UNAPPLIED_RELEASE");}catch(Exception e){throw new IOException("RENEWAL120_RETURN_CHANGED",e);}});same(id,source,releases.resolve(label));return made;
  }finally{try(var files=Files.walk(work)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 /** Creates a newer signed successor with the exact code of a verified baseline. */
 public static Map<String,Object> trialReturn(Path id,Path source,Path trial,Path releases,String label)throws Exception {
  id=FirstLaunchFiles.safe(id);source=FirstLaunchFiles.safe(source);trial=FirstLaunchFiles.safe(trial);releases=FirstLaunchFiles.safe(releases);
  var before=inspect(id,source);var temporary=inspect(id,trial);
  for(String field:List.of("signer","game","endpoint","distribution","minecraft","neoforge"))
   if(!before.get(field).equals(temporary.get(field)))throw fail("IDENTITY");
  Path destination=releases.resolve(label);
  if(!label.matches("return-[0-9a-f]{32}"))throw fail("ARGUMENTS");
  byte[] pub=FirstLaunchFiles.read(id.resolve("public.der"),256);
  var verified=DedicatedRelease74.verifyHistorical(source,pub);
  var offer=verified.offer();
  if(Files.exists(destination)){
   same(id,source,destination);
   var existing=DedicatedRelease74.verifyHistorical(destination,pub).offer();
   if(!Objects.equals(existing.parent(),UUID.fromString((String)temporary.get("release"))))throw fail("SOURCE_CHANGED");
   return Map.of("release",existing.release().toString());
  }
  var client=client(source,offer,pub);
  Path work=Files.createTempDirectory(releases.getParent(),".trial-return-");
  try {
   FirstLaunchFiles.privateMode(work);
   extract(source.resolve("public/client-"+offer.archiveSha()+".zip"),client.manifest().files(),work.resolve("client"));
   extract(source.resolve("server.zip"),verified.archive().manifest().files(),work.resolve("server"));
   Path seed=trial.resolve("public");
   var preview=Publication79.preview(id,seed,work.resolve("client"),work.resolve("server"),label,604800);
   Publication79.author(id,seed,work.resolve("client"),work.resolve("server"),releases,label,604800,(String)preview.get("ticket"));
   same(id,source,destination);
   return Map.of("release",DedicatedRelease74.verifyHistorical(destination,pub).offer().release().toString());
  } finally {
   try(var paths=Files.walk(work)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
  }
 }
 public static void main(String[] a){try{Object d;if(a.length==6&&a[0].equals("trial-return"))d=trialReturn(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),a[5]);else if(a.length==7&&a[0].equals("renew-return"))d=renewReturn120(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),a[5],Long.parseLong(a[6]));else if((a.length==4||a.length==5)&&a[0].equals("head"))d=Map.of("current",head(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),a.length==5?a[4]:"none"));else if(a.length==3&&a[0].equals("inspect"))d=inspect(Path.of(a[1]),Path.of(a[2]));else if(a.length==4&&a[0].equals("same")){same(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]));d=Map.of("same",true);}else if((a.length==6||a.length==7)&&a[0].equals("renew"))d=renew(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),a[4],Long.parseLong(a[5]),a.length==7?a[6]:"none");else throw fail("ARGUMENTS");System.out.println(DedicatedTools74.json(d));}catch(Exception e){String c=e.getMessage();System.err.println(c!=null&&c.matches("(?:RENEWAL85|PUBLICATION79)_[A-Z_]{1,60}")?c:"RENEWAL85_REJECTED");System.exit(2);}}
}
