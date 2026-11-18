package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;

/** Complete successor side sets from a verified pair + one current Script build. Local host only. */
public final class ScriptRelease84 {
 private ScriptRelease84(){}
 private static IOException fail(String code){return new IOException("SCRIPT84_"+code);}
 private static Set<String> sides(String side)throws IOException{return switch(side){case "CLIENT"->Set.of("client");case "SERVER"->Set.of("server");case "BOTH"->Set.of("client","server");default->throw fail("SIDE_REQUIRED");};}
 private static String seal(Path folder)throws Exception{var c=Publication79.scan(folder.resolve("client"),PackageModel.Side.CLIENT);var s=Publication79.scan(folder.resolve("server"),PackageModel.Side.SERVER);return FirstLaunchFiles.sha(ControlWire73.encode(Map.of("client",c.seal(),"server",s.seal())));}
 private static void extract(Path archive,List<PackageModel.Entry> files,Path output)throws Exception{
  Files.createDirectory(output);try(var zip=new ZipFile(archive.toFile())){for(var e:files){Path target=output.resolve(e.path().substring(5));try(var in=zip.getInputStream(zip.getEntry("files/"+e.path()));var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){byte[] b=new byte[65536];long count=0;int n;while((n=in.read(b))!=-1){count+=n;if(count>e.bytes())throw fail("BASELINE_CHANGED");out.write(b,0,n);}if(count!=e.bytes())throw fail("BASELINE_CHANGED");}if(!FirstLaunchFiles.hash(target,PackageModel.MAX_FILE).equals(e.sha256()))throw fail("BASELINE_CHANGED");}}
 }
 private static List<Map<String,String>> inject(Path folder,BuildSnapshot84.Snapshot snapshot,boolean selected)throws Exception{
  var changes=new ArrayList<Map<String,String>>();var byId=new HashMap<String,Path>();var matches=new HashSet<Path>();var ids=new HashSet<>(snapshot.modIds());
  try(var paths=Files.newDirectoryStream(folder)){for(Path jar:paths){var info=JarInspection.inspect(jar,()->false);for(String id:info.modIds()){if(byId.putIfAbsent(id,jar)!=null)throw fail("DUPLICATE_MOD_ID");if(selected&&ids.contains(id))matches.add(jar);}}}
  if(!selected)return changes;
  if(matches.size()>1)throw fail("AMBIGUOUS_REPLACEMENT");
  String name=snapshot.modIds().get(0)+"-script-"+snapshot.artifact().sha256().substring(0,12)+".jar";
  if(!matches.isEmpty()){
   Path old=matches.iterator().next();var oldIds=new HashSet<>(JarInspection.inspect(old,()->false).modIds());if(!oldIds.equals(ids))throw fail("PARTIAL_MOD_REPLACEMENT");
   name=old.getFileName().toString();changes.add(Map.of("action","replace","file",name,"oldSha",FirstLaunchFiles.hash(old,PackageModel.MAX_FILE),"newSha",snapshot.artifact().sha256()));Files.delete(old);
  }else changes.add(Map.of("action","add","file",name,"newSha",snapshot.artifact().sha256()));
  Path target=folder.resolve(name);if(Files.exists(target))throw fail("NAME_COLLISION");Files.copy(snapshot.directory().resolve("artifact.jar"),target);
  if(!FirstLaunchFiles.hash(target,PackageModel.MAX_FILE).equals(snapshot.artifact().sha256()))throw fail("BINARY_CHANGED");return changes;
 }
 public static Map<String,Object> prepare(Path identity,Path baseline,Path snapshotFolder,Path drafts,String side)throws Exception{
  sides(side);identity=FirstLaunchFiles.safe(identity);baseline=FirstLaunchFiles.safe(baseline);drafts=FirstLaunchFiles.safe(drafts);var snapshot=BuildSnapshot84.verify(snapshotFolder,()->false);
  if(drafts.startsWith(snapshot.project()))throw fail("OUTPUT_IN_PROJECT");byte[] pub=FirstLaunchFiles.read(identity.resolve("public.der"),256);var pair=DedicatedRelease74.verifyHistorical(baseline,pub);var offer=pair.offer();
  var clientArchive=baseline.resolve("public/client-"+offer.archiveSha()+".zip");var client=PackageArchive.verify(clientArchive,Map.of(offer.signer(),RemoteOffer69.publicKey(pub)),new PackageModel.Target(offer.distribution(),PackageModel.Side.CLIENT,offer.minecraft(),offer.neoforge(),1));
  Files.createDirectories(drafts);FirstLaunchFiles.privateMode(drafts);Path stage=Files.createTempDirectory(drafts,"script84-");FirstLaunchFiles.privateMode(stage);boolean done=false;
  try{
   extract(clientArchive,client.manifest().files(),stage.resolve("client"));extract(baseline.resolve("server.zip"),pair.archive().manifest().files(),stage.resolve("server"));
   var changes=new TreeMap<String,Object>();for(String s:List.of("client","server"))changes.put(s,inject(stage.resolve(s),snapshot,sides(side).contains(s)));
   String content=seal(stage);if(!BuildSnapshot84.verify(snapshotFolder,()->false).seal().equals(snapshot.seal()))throw fail("INPUTS_CHANGED");DedicatedRelease74.verifyHistorical(baseline,pub);
   var receipt=Map.of("schema","84","snapshot",snapshotFolder.toAbsolutePath().normalize().toString(),"snapshotSeal",snapshot.seal(),"baseline",baseline.toString(),"release",offer.release().toString(),"side",side,"content",content,"signer",offer.signer());
   FirstLaunchFiles.write(stage.resolve("draft84"),ControlWire73.encode(receipt),false);done=true;var result=new TreeMap<String,Object>();result.putAll(Map.of("draft",stage.toString(),"client",stage.resolve("client").toString(),"server",stage.resolve("server").toString(),"release",offer.release().toString(),"project",snapshot.projectId(),"task",snapshot.task(),"buildId",snapshot.artifact().buildId(),"sha256",snapshot.artifact().sha256(),"modIds",snapshot.modIds(),"side",side));result.put("changes",changes);return result;
  }finally{if(!done)try(var paths=Files.walk(stage)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 public static Map<String,String> verify(Path identity,Path baseline,Path draft)throws Exception{
  identity=FirstLaunchFiles.safe(identity);baseline=FirstLaunchFiles.safe(baseline);draft=FirstLaunchFiles.safe(draft);var d=ControlWire73.decode(FirstLaunchFiles.read(draft.resolve("draft84"),16384));
  if(!d.keySet().equals(Set.of("schema","snapshot","snapshotSeal","baseline","release","side","content","signer"))||!d.get("schema").equals("84")||!d.get("baseline").equals(baseline.toString()))throw fail("DRAFT_CHANGED");sides(d.get("side"));
  byte[] pub=FirstLaunchFiles.read(identity.resolve("public.der"),256);var pair=DedicatedRelease74.verifyHistorical(baseline,pub);if(!pair.offer().release().toString().equals(d.get("release"))||!pair.offer().signer().equals(d.get("signer")))throw fail("BASELINE_CHANGED");
  if(!BuildSnapshot84.verify(Path.of(d.get("snapshot")),()->false).seal().equals(d.get("snapshotSeal")))throw fail("SNAPSHOT_CHANGED");if(!seal(draft).equals(d.get("content")))throw fail("DRAFT_CHANGED");return d;
 }
 public static Map<String,Object> publication(Path identity,Path baseline,Path draft,Path releases,String label,long ttl,String ticket)throws Exception{
  var d=verify(identity,baseline,draft);if(ticket==null)return Publication79.preview(identity,baseline.resolve("public"),draft.resolve("client"),draft.resolve("server"),label,ttl);
  // Recheck build and side-set provenance at the packaging commit, not only before preview.
  var snapshot=BuildSnapshot84.verify(Path.of(d.get("snapshot")),()->false);var evidence=new TreeMap<String,String>();evidence.putAll(Map.of("schema","84","buildId",snapshot.artifact().buildId(),"projectId",snapshot.projectId(),"task",snapshot.task(),"inputs",snapshot.artifact().inputs(),"binary",snapshot.artifact().sha256(),"side",d.get("side"),"baseline",d.get("release"),"snapshot",snapshot.seal()));evidence.put("modIds",String.join(",",snapshot.modIds()));
  return Publication79.authorChecked(identity,baseline.resolve("public"),draft.resolve("client"),draft.resolve("server"),releases,label,ttl,ticket,()->{try{verify(identity,baseline,draft);}catch(Exception e){throw new IOException(e.getMessage(),e);}},evidence);
 }
 public static void main(String[] a){try{Object r;if(a.length==6&&a[0].equals("prepare"))r=prepare(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),a[5]);else if((a.length==7||a.length==8)&&a[0].equals("publication"))r=publication(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),a[5],Long.parseLong(a[6]),a.length==8?a[7]:null);else throw fail("ARGUMENTS");System.out.println(DedicatedTools74.json(r));}catch(Exception e){String c=e.getMessage();System.err.println(c!=null&&c.matches("(?:SCRIPT84|PUBLICATION79)_[A-Z_]{1,60}")?c:"SCRIPT84_REJECTED");System.exit(2);}}
}
