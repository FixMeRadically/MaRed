package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.packages.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** HTTPS download and verification only. No archive extraction, JVM shutdown or executable scripts. */
public final class RemoteClient69 {
    public record Preview(RemoteOffer69 offer,byte[] key,byte[] signature,boolean trusted){
        public Preview{key=key.clone();signature=signature.clone();}
        @Override public byte[] key(){return key.clone();}@Override public byte[] signature(){return signature.clone();}
    }
    public record Download(Path directory,Path archive,RemoteOffer69 offer){}
    private record Pin(String game,String endpoint,UUID distribution,String signer,long sequence,String offerHash){}
    private final Path root;private final String minecraft,neoforge;
    public RemoteClient69(Path root,String minecraft,String neoforge)throws IOException{
        this.root=FirstLaunchFiles.safe(root);this.minecraft=minecraft;this.neoforge=neoforge;
        Files.createDirectories(this.root);FirstLaunchFiles.privateMode(this.root);
        for(String name:List.of("trust","downloads")){Path dir=FirstLaunchFiles.safe(this.root.resolve(name));Files.createDirectories(dir);FirstLaunchFiles.privateMode(dir);}
    }
    private static void check(BooleanSupplier cancel)throws IOException{if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("REMOTE_CANCELLED");}
    private static void fetch(URI uri,long max,OutputStream out,BooleanSupplier cancel,LongConsumer progress)throws IOException{
        check(cancel);HttpsURLConnection c=(HttpsURLConnection)uri.toURL().openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(5000);c.setRequestProperty("Accept-Encoding","identity");c.setUseCaches(false);
        long deadline=System.nanoTime()+600_000_000_000L;
        try{
            if(c.getResponseCode()!=200)throw new IOException("REMOTE_HTTP_STATUS");
            String encoding=c.getHeaderField("Content-Encoding");if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("REMOTE_ENCODING");
            long declared=c.getContentLengthLong();if(declared>max)throw new IOException("REMOTE_DOWNLOAD_LIMIT");long count=0;
            try(var in=c.getInputStream()){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;){check(cancel);if(System.nanoTime()>deadline)throw new IOException("REMOTE_DOWNLOAD_TIMEOUT");count+=n;if(count>max)throw new IOException("REMOTE_DOWNLOAD_LIMIT");out.write(buffer,0,n);progress.accept(count);}}
            if(declared>=0&&count!=declared)throw new IOException("REMOTE_DOWNLOAD_TRUNCATED");check(cancel);
        }finally{c.disconnect();}
    }
    private static byte[] small(URI uri,int max,BooleanSupplier cancel)throws IOException{var out=new ByteArrayOutputStream();fetch(uri,max,out,cancel,n->{});return out.toByteArray();}
    private Path pinPath(String game,String endpoint)throws IOException{return FirstLaunchFiles.safe(root.resolve("trust").resolve(FirstLaunchFiles.sha((game+"\n"+endpoint).getBytes(java.nio.charset.StandardCharsets.UTF_8))+".pin"));}
    private static byte[] encode(Pin pin)throws IOException{var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.writeUTF("GENESIS-TRUST69");for(String s:List.of(pin.game,pin.endpoint,pin.distribution.toString(),pin.signer))out.writeUTF(s);out.writeLong(pin.sequence);out.writeUTF(pin.offerHash);}return bytes.toByteArray();}
    private Pin read(String game,String endpoint)throws IOException{
        Path file=pinPath(game,endpoint);if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null;byte[] raw=FirstLaunchFiles.read(file,8192);
        try(var in=new DataInputStream(new ByteArrayInputStream(raw))){
            if(!in.readUTF().equals("GENESIS-TRUST69"))throw new IOException("REMOTE_TRUST_FORMAT");Pin p=new Pin(in.readUTF(),in.readUTF(),UUID.fromString(in.readUTF()),in.readUTF(),in.readLong(),in.readUTF());
            if(in.read()!=-1||!p.game.equals(game)||!p.endpoint.equals(endpoint)||p.distribution.equals(new UUID(0,0))||!RemoteOffer69.hash(p.signer)||p.sequence<0||!RemoteOffer69.hash(p.offerHash)||!Arrays.equals(raw,encode(p)))throw new IOException("REMOTE_TRUST_FORMAT");return p;
        }catch(IllegalArgumentException bad){throw new IOException("REMOTE_TRUST_FORMAT",bad);}
    }
    private static void matches(Pin pin,RemoteOffer69 offer)throws IOException{
        if(!pin.signer.equals(offer.signer())||!pin.distribution.equals(offer.distribution()))throw new IOException("REMOTE_TRUST_CHANGED");
        if(offer.sequence()<pin.sequence)throw new IOException("REMOTE_ROLLBACK");
        if(offer.sequence()==pin.sequence&&!pin.offerHash.equals(FirstLaunchFiles.sha(offer.encode())))throw new IOException("REMOTE_EQUIVOCATION");
    }
    public Preview inspect(String address,String endpoint,BooleanSupplier cancel)throws IOException{
        String game=RemoteOffer69.gameAddress(address),base=RemoteOffer69.base(endpoint);Pin pin=read(game,base);
        byte[] encodedKey=small(URI.create(base+"public.der"),256,cancel);var key=RemoteOffer69.publicKey(encodedKey);
        if(pin!=null&&!pin.signer.equals(FirstLaunchFiles.sha(encodedKey)))throw new IOException("REMOTE_TRUST_CHANGED");
        var offer=RemoteOffer69.decode(small(URI.create(base+"offer.bin"),8192,cancel));byte[] signature=small(URI.create(base+"offer.sig"),64,cancel);offer.verify(key,signature);
        if(!offer.game().equals(game)||!offer.endpoint().equals(base))throw new IOException("REMOTE_WRONG_SERVER");
        if(!offer.minecraft().equals(minecraft)||!offer.neoforge().equals(neoforge))throw new IOException("REMOTE_WRONG_VERSION");
        offer.validAt(System.currentTimeMillis());if(pin!=null)matches(pin,offer);return new Preview(offer,encodedKey,signature,pin!=null);
    }
    public void trust(Preview preview)throws IOException{
        var offer=preview.offer;offer.verify(RemoteOffer69.publicKey(preview.key),preview.signature);offer.validAt(System.currentTimeMillis());
        withLock(offer,()->{Pin existing=read(offer.game(),offer.endpoint());if(existing!=null){matches(existing,offer);return;}FirstLaunchFiles.write(pinPath(offer.game(),offer.endpoint()),encode(new Pin(offer.game(),offer.endpoint(),offer.distribution(),offer.signer(),0,"0".repeat(64))),false);});
    }
    private interface Work{void run()throws IOException;}
    private void withLock(RemoteOffer69 offer,Work work)throws IOException{
        Path file=FirstLaunchFiles.safe(pinPath(offer.game(),offer.endpoint()).resolveSibling(pinPath(offer.game(),offer.endpoint()).getFileName()+".lock"));
        try(var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){FirstLaunchFiles.privateMode(file);try(var lock=channel.lock()){work.run();}}
    }
    private void validateTrusted(Preview preview)throws IOException{
        Pin pin=read(preview.offer.game(),preview.offer.endpoint());if(pin==null)throw new IOException("REMOTE_TRUST_REQUIRED");matches(pin,preview.offer);
        preview.offer.verify(RemoteOffer69.publicKey(preview.key),preview.signature);preview.offer.validAt(System.currentTimeMillis());
    }
    private void current(Preview preview,BooleanSupplier cancel)throws IOException{
        Preview latest=inspect(preview.offer.game(),preview.offer.endpoint(),cancel);validateTrusted(latest);
        if(!Arrays.equals(latest.offer.encode(),preview.offer.encode())||!Arrays.equals(latest.key,preview.key))throw new IOException("REMOTE_OFFER_CHANGED");
    }
    /** Revalidate a cached candidate including current HTTPS offer. Receipt text is not authority. */
    public Preview cached(Path directory,BooleanSupplier cancel)throws IOException{
        directory=FirstLaunchFiles.safe(directory);if(!directory.getParent().equals(root.resolve("downloads")))throw new IOException("REMOTE_CACHE_PATH");
        try{UUID.fromString(directory.getFileName().toString());}catch(IllegalArgumentException bad){throw new IOException("REMOTE_CACHE_PATH",bad);}
        var offer=RemoteOffer69.decode(FirstLaunchFiles.read(directory.resolve("offer.bin"),8192));var p=new Preview(offer,FirstLaunchFiles.read(directory.resolve("public.der"),256),FirstLaunchFiles.read(directory.resolve("offer.sig"),64),true);validateTrusted(p);check(cancel);
        Path archive=directory.resolve("client.zip");if(Files.size(archive)!=offer.bytes()||!FirstLaunchFiles.hash(archive,PackageModel.MAX_ARCHIVE).equals(offer.archiveSha()))throw new IOException("REMOTE_ARCHIVE_HASH");
        var verified=PackageArchive.verify(archive,Map.of(offer.signer(),RemoteOffer69.publicKey(p.key)),new PackageModel.Target(offer.distribution(),PackageModel.Side.CLIENT,minecraft,neoforge,1));
        if(!verified.manifest().release().equals(offer.release())||!Objects.equals(verified.manifest().parent(),offer.parent())||verified.manifest().files().stream().anyMatch(e->e.side()!=PackageModel.Side.CLIENT))throw new IOException("REMOTE_PACKAGE_BINDING");
        current(p,cancel);return p;
    }
    public Download download(Preview preview,BooleanSupplier cancel,LongConsumer progress)throws IOException{
        validateTrusted(preview);current(preview,cancel);check(cancel);var offer=preview.offer;Path directory=FirstLaunchFiles.safe(root.resolve("downloads").resolve(UUID.randomUUID().toString()));Files.createDirectory(directory);FirstLaunchFiles.privateMode(directory);boolean done=false;
        try{
            Path part=directory.resolve("client.part");try(var out=Files.newOutputStream(part,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){FirstLaunchFiles.privateMode(part);fetch(offer.resource("client-"+offer.archiveSha()+".zip"),offer.bytes(),out,cancel,progress);}
            if(Files.size(part)!=offer.bytes()||!FirstLaunchFiles.hash(part,PackageModel.MAX_ARCHIVE).equals(offer.archiveSha()))throw new IOException("REMOTE_ARCHIVE_HASH");check(cancel);
            var verified=PackageArchive.verify(part,Map.of(offer.signer(),RemoteOffer69.publicKey(preview.key)),new PackageModel.Target(offer.distribution(),PackageModel.Side.CLIENT,minecraft,neoforge,1));var manifest=verified.manifest();
            if(!manifest.release().equals(offer.release())||!Objects.equals(manifest.parent(),offer.parent())||manifest.files().stream().anyMatch(e->e.side()!=PackageModel.Side.CLIENT))throw new IOException("REMOTE_PACKAGE_BINDING");
            current(preview,cancel);check(cancel);Path archive=directory.resolve("client.zip");Files.move(part,archive);
            FirstLaunchFiles.write(directory.resolve("offer.bin"),offer.encode(),false);FirstLaunchFiles.write(directory.resolve("offer.sig"),preview.signature,false);FirstLaunchFiles.write(directory.resolve("public.der"),preview.key,false);
            FirstLaunchFiles.write(directory.resolve("receipt69.txt"),("schema=69\ninstallAuthority=false\nrelease="+offer.release()+"\narchiveSha256="+offer.archiveSha()+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII),false);
            withLock(offer,()->{check(cancel);validateTrusted(preview);FirstLaunchFiles.write(pinPath(offer.game(),offer.endpoint()),encode(new Pin(offer.game(),offer.endpoint(),offer.distribution(),offer.signer(),offer.sequence(),FirstLaunchFiles.sha(offer.encode()))),true);});
            done=true;return new Download(directory,archive,offer);
        }finally{
            if(!done){for(String name:List.of("client.part","client.zip","offer.bin","offer.sig","public.der","receipt69.txt"))try{Files.deleteIfExists(directory.resolve(name));}catch(IOException ignored){}try{Files.deleteIfExists(directory);}catch(IOException ignored){}}
        }
    }
}
