package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.ZipFile;

/** A persistent publisher identity, independent of one-use local pilot keys. */
public final class RemotePublisher69 {
    private RemotePublisher69(){}
    public static void initialize(Path identity,String game,String endpoint)throws Exception{
        identity=FirstLaunchFiles.safe(identity);if(Files.exists(identity)||!Files.isDirectory(identity.getParent()))throw new IOException("REMOTE_IDENTITY_EXISTS");
        String address=RemoteOffer69.gameAddress(game),base=RemoteOffer69.base(endpoint);Files.createDirectory(identity);FirstLaunchFiles.privateMode(identity);
        var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();FirstLaunchFiles.write(identity.resolve("public.der"),keys.getPublic().getEncoded(),false);FirstLaunchFiles.write(identity.resolve("private.der"),keys.getPrivate().getEncoded(),false);
        FirstLaunchFiles.write(identity.resolve("publisher69.txt"),(address+"\n"+base+"\n"+UUID.randomUUID()+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII),false);
        FirstLaunchFiles.write(identity.resolve("head69.txt"),"0\nnone\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),false);
    }
    public static RemoteOffer69 publish(Path identity,Path packages,Path output,long ttlSeconds)throws Exception{
        identity=FirstLaunchFiles.safe(identity);packages=FirstLaunchFiles.safe(packages);output=FirstLaunchFiles.safe(output);
        if(ttlSeconds<60||ttlSeconds>7*86400||Files.exists(output)||!Files.isDirectory(output.getParent()))throw new IOException("REMOTE_PUBLISH_ARGUMENTS");
        Path lockFile=FirstLaunchFiles.safe(identity.resolve("publisher.lock"));
        try(var channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){FirstLaunchFiles.privateMode(lockFile);try(var lock=channel.lock()){
            String[] settings=new String(FirstLaunchFiles.read(identity.resolve("publisher69.txt"),8192),java.nio.charset.StandardCharsets.US_ASCII).split("\n");String[] head=new String(FirstLaunchFiles.read(identity.resolve("head69.txt"),256),java.nio.charset.StandardCharsets.US_ASCII).split("\n");
            if(settings.length!=3||head.length!=2)throw new IOException("REMOTE_IDENTITY_FORMAT");long sequence=Long.parseLong(head[0]);if(sequence<0||sequence==Long.MAX_VALUE)throw new IOException("REMOTE_SEQUENCE");UUID parent=head[1].equals("none")?null:UUID.fromString(head[1]);if((sequence==0)!=(parent==null))throw new IOException("REMOTE_IDENTITY_FORMAT");
            byte[] publicBytes=FirstLaunchFiles.read(identity.resolve("public.der"),256);PublicKey key=RemoteOffer69.publicKey(publicBytes);PrivateKey privateKey=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(FirstLaunchFiles.read(identity.resolve("private.der"),256)));
            PublicKey sourceKey=RemoteOffer69.publicKey(FirstLaunchFiles.read(packages.resolve("public.der"),256));Path source=packages.resolve("client-1.zip");PackageModel.Manifest m;
            try(var zip=new ZipFile(source.toFile())){var entry=zip.getEntry(PackageArchive.MANIFEST);if(entry==null)throw new IOException("REMOTE_MANIFEST_MISSING");try(var in=zip.getInputStream(entry)){m=PackageCodec.decode(in.readNBytes(PackageModel.MAX_MANIFEST+1));}}
            var verified=PackageArchive.verify(source,Map.of(FirstLaunchFiles.sha(sourceKey.getEncoded()),sourceKey),new PackageModel.Target(m.distribution(),PackageModel.Side.CLIENT,m.minecraft(),m.loaderVersion(),1));m=verified.manifest();
            if(m.files().stream().anyMatch(e->e.side()!=PackageModel.Side.CLIENT))throw new IOException("REMOTE_CLIENT_PACKAGE_REQUIRED");
            Path stage=Files.createTempDirectory(output.getParent(),".genesis-remote69-");FirstLaunchFiles.privateMode(stage);Path payload=stage.resolve("payload");Files.createDirectory(payload);FirstLaunchFiles.privateMode(payload);boolean published=false;
            List<Path> created=new ArrayList<>();
            try{
                Map<String,Path> sources=new TreeMap<>();try(var zip=new ZipFile(source.toFile())){for(var entry:m.files()){
                    Path file=payload.resolve(entry.path().substring(5));created.add(file);try(var in=zip.getInputStream(zip.getEntry("files/"+entry.path()));var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){FirstLaunchFiles.privateMode(file);byte[] buffer=new byte[65536];long count=0;for(int n;(n=in.read(buffer))!=-1;){count+=n;if(count>entry.bytes())throw new IOException("REMOTE_SOURCE_CHANGED");out.write(buffer,0,n);}}
                    sources.put(entry.path(),file);
                }}
                UUID release=UUID.randomUUID();var manifest=new PackageModel.Manifest(UUID.fromString(settings[2]),release,parent,m.version(),m.minecraft(),m.loader(),m.loaderVersion(),m.minProtocol(),m.maxProtocol(),FirstLaunchFiles.sha(publicBytes),m.files());
                Path archive=stage.resolve("client.zip");PackageArchive.create(archive,manifest,sources,privateKey,key);created.add(archive);
                var check=PackageArchive.verify(archive,Map.of(FirstLaunchFiles.sha(publicBytes),key),new PackageModel.Target(manifest.distribution(),PackageModel.Side.CLIENT,m.minecraft(),m.loaderVersion(),1));
                long now=System.currentTimeMillis();var offer=new RemoteOffer69(settings[0],settings[1],manifest.distribution(),release,parent,m.minecraft(),m.loaderVersion(),sequence+1,now,now+ttlSeconds*1000,Files.size(archive),check.archiveSha256(),FirstLaunchFiles.sha(publicBytes));byte[] signature=offer.sign(privateKey);offer.verify(key,signature);
                Path finalArchive=stage.resolve("client-"+offer.archiveSha()+".zip");Files.move(archive,finalArchive);created.add(finalArchive);
                for(var entry:Map.of("public.der",publicBytes,"offer.bin",offer.encode(),"offer.sig",signature).entrySet()){Path file=stage.resolve(entry.getKey());FirstLaunchFiles.write(file,entry.getValue(),false);created.add(file);}
                for(Path file:sources.values())Files.delete(file);Files.delete(payload);
                // Advance the durable sequence BEFORE exposing the candidate. A failed move may
                // leave a gap, but a later publication can never reuse a previously signed number.
                FirstLaunchFiles.write(identity.resolve("head69.txt"),(offer.sequence()+"\n"+release+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII),true);
                Files.move(stage,output);published=true;return offer;
            }finally{if(!published){for(Path file:created)try{Files.deleteIfExists(file);}catch(IOException ignored){}try{Files.deleteIfExists(payload);Files.deleteIfExists(stage);}catch(IOException ignored){}}}
        }}
    }
}
