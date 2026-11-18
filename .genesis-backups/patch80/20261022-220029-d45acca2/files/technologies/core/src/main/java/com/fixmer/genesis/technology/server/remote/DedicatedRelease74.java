package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;import java.nio.file.*;import java.security.*;import java.security.spec.*;import java.util.*;import java.util.zip.*;
/** A SERVER archive signed by the SAME publisher and tied to the exact public CLIENT offer. */
public final class DedicatedRelease74 {
 private DedicatedRelease74(){}
 public record Verified(RemoteOffer69 offer,PackageArchive.Verified archive){}
 static PrivateKey privateKey(Path identity)throws Exception{return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(FirstLaunchFiles.read(identity.resolve("private.der"),256)));}
 public static void author(Path identity,Path publicExport,Path mods,Path output)throws Exception{
  identity=FirstLaunchFiles.safe(identity);publicExport=FirstLaunchFiles.safe(publicExport);mods=FirstLaunchFiles.safe(mods);output=FirstLaunchFiles.safe(output);var offer=RemoteUpdateCli69.verifyPublic(publicExport);byte[] pub=FirstLaunchFiles.read(identity.resolve("public.der"),256);if(!FirstLaunchFiles.sha(pub).equals(offer.signer())||Files.exists(output)||!Files.isDirectory(output.getParent()))throw new IOException("DEDICATED74_IDENTITY_OR_OUTPUT");
  PublicKey key=RemoteOffer69.publicKey(pub);PrivateKey secret=privateKey(identity);Map<String,Path> sources=new TreeMap<>();List<PackageModel.Entry> entries=new ArrayList<>();
  try(var paths=Files.list(mods)){for(Path file:paths.toList()){FirstLaunchFiles.safe(file);if(!Files.isRegularFile(file)||!file.getFileName().toString().endsWith(".jar"))throw new IOException("DEDICATED74_JAR_SET");try(var jar=new ZipFile(file.toFile())){if(jar.getEntry("com/fixmer/mared/Mared.class")!=null)continue;}
   String path="mods/"+file.getFileName();var entry=new PackageModel.Entry(path,PackageModel.Side.SERVER,Files.size(file),FirstLaunchFiles.hash(file,PackageModel.MAX_FILE));entries.add(entry);sources.put(path,file);
  }}
  var manifest=new PackageModel.Manifest(offer.distribution(),offer.release(),offer.parent(),"dedicated74",offer.minecraft(),"neoforge",offer.neoforge(),1,1,offer.signer(),entries);
  Files.createDirectory(output);FirstLaunchFiles.privateMode(output);Path archive=output.resolve("server.zip");PackageArchive.create(archive,manifest,sources,secret,key);var verified=PackageArchive.verify(archive,Map.of(offer.signer(),key),new PackageModel.Target(offer.distribution(),PackageModel.Side.SERVER,offer.minecraft(),offer.neoforge(),1));
  var plan=Map.of("schema","74","release",offer.release().toString(),"client",offer.archiveSha(),"server",verified.archiveSha256(),"distribution",offer.distribution().toString(),"minecraft",offer.minecraft(),"neoforge",offer.neoforge(),"signer",offer.signer());FirstLaunchFiles.write(output.resolve("pair74"),ControlWire73.sign(secret,plan),false);
  Files.createDirectory(output.resolve("public"));for(String name:List.of("public.der","offer.bin","offer.sig","client-"+offer.archiveSha()+".zip"))Files.copy(publicExport.resolve(name),output.resolve("public").resolve(name));verify(output,pub);
 }
 public static Verified verify(Path folder,byte[] trusted)throws Exception{
  folder=FirstLaunchFiles.safe(folder);if(!MessageDigest.isEqual(trusted,FirstLaunchFiles.read(folder.resolve("public/public.der"),256)))throw new IOException("DEDICATED74_PIN");var offer=RemoteUpdateCli69.verifyPublic(folder.resolve("public"));var key=RemoteOffer69.publicKey(trusted);var pair=ControlWire73.verify(key,FirstLaunchFiles.read(folder.resolve("pair74"),16384));
  if(!pair.equals(Map.of("schema","74","release",offer.release().toString(),"client",offer.archiveSha(),"server",FirstLaunchFiles.hash(folder.resolve("server.zip"),PackageModel.MAX_ARCHIVE),"distribution",offer.distribution().toString(),"minecraft",offer.minecraft(),"neoforge",offer.neoforge(),"signer",offer.signer())))throw new IOException("DEDICATED74_PAIR");
  var result=PackageArchive.verify(folder.resolve("server.zip"),Map.of(offer.signer(),key),new PackageModel.Target(offer.distribution(),PackageModel.Side.SERVER,offer.minecraft(),offer.neoforge(),1));var m=result.manifest();if(!m.release().equals(offer.release())||!Objects.equals(m.parent(),offer.parent())||m.files().stream().anyMatch(e->e.side()!=PackageModel.Side.SERVER))throw new IOException("DEDICATED74_SIDE_OR_RELEASE");return new Verified(offer,result);
 }
}
