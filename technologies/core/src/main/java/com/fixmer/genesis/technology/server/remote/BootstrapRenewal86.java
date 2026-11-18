package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;import com.fixmer.genesis.technology.server.packages.*;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.function.*;
/** A fresh identical-code release can replace install input; historical bytes alone never authorize install. */
public final class BootstrapRenewal86 {
 private BootstrapRenewal86(){}
 private static IOException fail(String c){return new IOException("BOOTSTRAP86_"+c);}
 private static PackageArchive.Verified archive(Path candidate,RemoteClient69.Preview p,String mc,String neo)throws Exception{return PackageArchive.verify(candidate.resolve("client.zip"),Map.of(p.offer().signer(),RemoteOffer69.publicKey(p.key())),new PackageModel.Target(p.offer().distribution(),PackageModel.Side.CLIENT,mc,neo,1));}
 public static void compatible(Map<String,String> before,RemoteOffer69 old,byte[] oldKey,String inventory,Bootstrap76.State current)throws Exception{
  for(String k:List.of("owner","generation","game","endpoint","distribution","signer","inventory"))if(!Objects.equals(before.get(k),current.values().get(k)))throw fail("CONTEXT_CHANGED");
  if(!Arrays.equals(oldKey,current.key())||!inventory.equals(current.values().get("inventory"))||!before.get("release").equals(old.release().toString())||!before.get("archive").equals(old.archiveSha())||Long.parseLong(current.values().get("serial"))<Long.parseLong(before.get("serial"))||current.phase().equals("UNAVAILABLE"))throw fail("CONTEXT_CHANGED");
  if(current.offer().sequence()<old.sequence()||current.offer().sequence()==old.sequence()&&!Arrays.equals(current.offer().encode(),old.encode())||current.offer().release().equals(old.release())&&!Arrays.equals(current.offer().encode(),old.encode()))throw fail("ROLLBACK");
 }
 public static void connection(Path cache,Bootstrap76.State expected,Bootstrap76.State current)throws Exception{
  compatible(expected.values(),expected.offer(),expected.key(),expected.values().get("inventory"),current);
  RemoteClient69.bootstrap(cache,current.offer().minecraft(),current.offer().neoforge(),current.offer().release()).validateFresh86(new RemoteClient69.Preview(current.offer(),current.key(),current.signature(),true));
 }
 public static Bootstrap76.State join(Path cache,Path candidate,String mc,String neo,BooleanSupplier cancel)throws Exception{
  var config=Bootstrap76.config(candidate);var client=RemoteClient69.forCandidate(cache,mc,neo,candidate);var old=client.historicalCandidate86(candidate,cancel);var verified=archive(candidate,old,mc,neo);var current=Bootstrap76.inspect(config.get("game"),config.get("endpoint"),mc,neo,cancel);compatible(config,old.offer(),old.key(),Bootstrap76.inventory(verified),current);client.validateFresh86(new RemoteClient69.Preview(current.offer(),current.key(),current.signature(),true));return current;
 }
 public static Map<String,Object> refresh(Path cache,Path candidate,String mc,String neo,BooleanSupplier cancel,LongConsumer progress)throws Exception{
  candidate=FirstLaunchFiles.safe(candidate);if(Files.exists(candidate.resolve("control73.cfg")))throw fail("PRIVATE_ROUND");var before=Bootstrap76.config(candidate);var historical=RemoteClient69.forCandidate(cache,mc,neo,candidate).historicalCandidate86(candidate,cancel);var previous=archive(candidate,historical,mc,neo);var current=join(cache,candidate,mc,neo,cancel);
  var client=RemoteClient69.bootstrap(cache,mc,neo,current.offer().release());var preview=client.inspect(current.offer().game(),current.offer().endpoint(),cancel);if(!preview.trusted()||!Arrays.equals(preview.offer().encode(),current.offer().encode())||!Arrays.equals(preview.key(),historical.key()))throw fail("CONTEXT_CHANGED");
  var download=client.download(preview,cancel,progress);var next=archive(download.directory(),preview,mc,neo);
  // Entire manifests, not a top-level modId or ZIP checksum: paths, sides, byte sizes and SHA must match.
  if(!previous.manifest().files().equals(next.manifest().files())||!Bootstrap76.inventory(next).equals(current.values().get("inventory")))throw fail("CONTENT_CHANGED");
  if(!Bootstrap76.config(candidate).equals(before))throw fail("CONTEXT_CHANGED");var fresh=Bootstrap76.inspect(current.offer().game(),current.offer().endpoint(),mc,neo,cancel);Bootstrap76.same(current,fresh);Bootstrap76.mark(download,fresh);
  var o=download.offer();var result=new TreeMap<String,Object>();result.putAll(Map.of("candidate",download.directory().toString(),"game",o.game(),"distribution",o.distribution().toString(),"endpoint",o.endpoint(),"release",o.release().toString(),"archiveSha256",o.archiveSha(),"files",next.manifest().files().stream().map(e->Map.of("path",e.path(),"sha256",e.sha256(),"bytes",e.bytes())).toList(),"installAuthority",false));return result;
 }
 public static void main(String[] a){try{if(a.length!=5||!Set.of("join","refresh").contains(a[0]))throw fail("ARGUMENTS");if(a[0].equals("join")){var s=join(Path.of(a[1]),Path.of(a[2]),a[3],a[4],()->false);var d=new TreeMap<>(s.values());d.put("sequence86",Long.toString(s.offer().sequence()));System.out.println(Base64.getEncoder().encodeToString(ControlWire73.encode(d)));}else System.out.println(DedicatedTools74.json(refresh(Path.of(a[1]),Path.of(a[2]),a[3],a[4],()->false,n->{})));}catch(Exception e){String c=e.getMessage();System.err.println(c!=null&&c.matches("(?:BOOTSTRAP86|REMOTE|BOOTSTRAP76)_[A-Z0-9_]{1,64}")?c:"BOOTSTRAP86_REJECTED");System.exit(2);}}
}
