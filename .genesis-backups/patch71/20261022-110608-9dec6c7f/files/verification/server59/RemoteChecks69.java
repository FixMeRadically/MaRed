package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Real signature/HTTP/TLS harness; it never starts Minecraft or installs into a game profile. */
public final class RemoteChecks69 {
    private static int checks;
    private static void check(boolean yes){if(!yes)throw new AssertionError();checks++;}
    private interface Work{void run()throws Exception;}
    private static void refused(Work work)throws Exception{try{work.run();throw new AssertionError("Unexpected acceptance");}catch(java.io.IOException|IllegalArgumentException expected){checks++;}}
    private static RemoteOffer69 changed(RemoteOffer69 o,long issued,long expires){return new RemoteOffer69(o.game(),o.endpoint(),o.distribution(),o.release(),o.parent(),o.minecraft(),o.neoforge(),o.sequence(),issued,expires,o.bytes(),o.archiveSha(),o.signer());}
    private static void variant(Path identity,Path source,Path dest,boolean expired)throws Exception{
        Files.createDirectory(dest);try(var files=Files.list(source)){for(Path file:files.toList())Files.copy(file,dest.resolve(file.getFileName()));}
        var o=RemoteOffer69.decode(Files.readAllBytes(source.resolve("offer.bin")));long now=System.currentTimeMillis();var next=expired?changed(o,now-120000,now-1000):changed(o,o.issued()+1,o.expires()+1);
        var privateKey=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(identity.resolve("private.der"))));Files.write(dest.resolve("offer.bin"),next.encode());Files.write(dest.resolve("offer.sig"),next.sign(privateKey));
    }
    private static void fixture(Path root,String endpoint)throws Exception{
        Path server=Files.createDirectories(root.resolve("server-mods")),client=Files.createDirectories(root.resolve("client-mods"));
        byte[] bytes=new byte[1024*1024];new Random(69).nextBytes(bytes);
        try(var zip=new ZipOutputStream(Files.newOutputStream(client.resolve("example.jar")))){zip.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));zip.write("modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"example\"\nversion=\"1\"".getBytes());zip.closeEntry();zip.putNextEntry(new ZipEntry("random.bin"));zip.write(bytes);zip.closeEntry();}
        try(var zip=new ZipOutputStream(Files.newOutputStream(client.resolve("mared.jar")))){zip.putNextEntry(new ZipEntry("com/fixmer/mared/Mared.class"));zip.write("explicit test bootstrap, not executable bytecode".getBytes());zip.closeEntry();}
        FirstLaunchArtifacts.author(root.resolve("packages"),List.of(server,client,server,client),"1.21.1","21.1.264");Path id=root.resolve("identity");RemotePublisher69.initialize(id,"localhost:25565",endpoint);
        var a=RemotePublisher69.publish(id,root.resolve("packages"),root.resolve("public1"),3600);var b=RemotePublisher69.publish(id,root.resolve("packages"),root.resolve("public2"),3600);
        try(var zip=new ZipFile(root.resolve("public1/client-"+a.archiveSha()+".zip").toFile())){check(zip.getEntry("files/mods/mared.jar")==null);}
        check(a.signer().equals(b.signer())&&a.distribution().equals(b.distribution())&&b.parent().equals(a.release())&&a.sequence()==1&&b.sequence()==2);
        try(var names=Files.list(root.resolve("public1"))){check(names.count()==4);}check(!Files.exists(root.resolve("public1/private.der")));
        check(RemoteUpdateCli69.verifyPublic(root.resolve("public1")).equals(a));
        refused(()->RemotePublisher69.publish(id,root.resolve("packages"),root.resolve("public1"),3600));
        refused(()->RemotePublisher69.initialize(id,"localhost:25565",endpoint));
        variant(id,root.resolve("public2"),root.resolve("equivocation"),false);variant(id,root.resolve("public2"),root.resolve("expired"),true);
        Files.createDirectories(root.resolve("wrong-key"));Files.write(root.resolve("wrong-key/public.der"),KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        System.out.println("Fixture authored with persistent identity; public export contains no private key");
    }
    private static void unit()throws Exception{
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();long now=System.currentTimeMillis();var o=new RemoteOffer69("localhost:25565","https://localhost/genesis/",UUID.randomUUID(),UUID.randomUUID(),null,"1.21.1","21.1.264",1,now,now+60000,100,"a".repeat(64),FirstLaunchFiles.sha(pair.getPublic().getEncoded()));
        check(RemoteOffer69.decode(o.encode()).equals(o));o.verify(pair.getPublic(),o.sign(pair.getPrivate()));checks++;
        byte[] signature=o.sign(pair.getPrivate());signature[0]^=1;refused(()->o.verify(pair.getPublic(),signature));refused(()->o.verify(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic(),o.sign(pair.getPrivate())));
        check(RemoteOffer69.gameAddress("PLAY.Example.org").equals("play.example.org:25565"));check(RemoteOffer69.gameAddress("[::1]:25565").equals("[::1]:25565"));check(RemoteOffer69.base("HTTPS://Example.org:443/genesis").equals("https://example.org/genesis/"));
        for(String s:List.of("http://example.org/","https://user:pass@example.org/","https://example.org/?token=x","https://example.org/#fragment","https://example.org/a/../","https://example.org/%2e/","file:///tmp"))refused(()->RemoteOffer69.base(s));
        for(String s:List.of("host:70000","user@host:25565","host/path","host?token=x","host#x","host:0"))refused(()->RemoteOffer69.gameAddress(s));
        refused(()->o.resource("../private.der"));refused(()->o.resource("https://other/"));refused(()->changed(o,now-120000,now-1000).validAt(now));refused(()->o.validAt(now-180000));
        refused(()->RemoteOffer69.decode(Arrays.copyOf(o.encode(),o.encode().length-1)));refused(()->RemoteOffer69.decode(new byte[8193]));
        byte[] extra=Arrays.copyOf(pair.getPublic().getEncoded(),pair.getPublic().getEncoded().length+1);refused(()->RemoteOffer69.publicKey(extra));
        System.out.println("Remote69 pure-JDK checks passed: "+checks);
    }
    public static void main(String[] args)throws Exception{
        if(args.length==0){unit();return;}if(args[0].equals("fixture")){fixture(Path.of(args[1]),args[2]);return;}
        var client=new RemoteClient69(Path.of(args[1]),"1.21.1","21.1.264");String mode=args[0],endpoint=args[2];
        if(mode.equals("network")){var p=client.inspect("localhost:25565",endpoint,()->false);client.trust(p);var trusted=client.inspect("localhost:25565",endpoint,()->false);var result=client.download(trusted,()->false,n->{});check(Files.isRegularFile(result.archive()));check(Files.readString(result.directory().resolve("receipt69.txt")).contains("installAuthority=false"));System.out.println("VERIFIED "+result.offer().sequence());return;}
        if(mode.equals("reject-untrusted")){var p=client.inspect("localhost:25565",endpoint,()->false);try{client.download(p,()->false,n->{});throw new AssertionError();}catch(java.io.IOException expected){check(expected.getMessage().equals("REMOTE_TRUST_REQUIRED"));System.out.println(expected.getMessage());return;}}
        if(mode.equals("cancel-body")){var p=client.inspect("localhost:25565",endpoint,()->false);client.trust(p);p=client.inspect("localhost:25565",endpoint,()->false);var cancelled=new AtomicBoolean();try{client.download(p,cancelled::get,n->cancelled.set(true));throw new AssertionError();}catch(java.io.IOException expected){check(expected.getMessage().equals("REMOTE_CANCELLED"));System.out.println(expected.getMessage());return;}}
        if(mode.equals("cancel-before")){try{client.inspect("localhost:25565",endpoint,()->true);throw new AssertionError();}catch(java.io.IOException expected){check(expected.getMessage().equals("REMOTE_CANCELLED"));System.out.println(expected.getMessage());return;}}
        if(mode.equals("expect")){try{client.inspect("localhost:25565",endpoint,()->false);throw new AssertionError();}catch(java.io.IOException expected){System.out.println(RemoteUpdateCli69.safeCode(expected));return;}}
        if(mode.equals("download-error")){try{var p=client.inspect("localhost:25565",endpoint,()->false);client.trust(p);client.download(p,()->false,n->{});throw new AssertionError();}catch(java.io.IOException expected){System.out.println(RemoteUpdateCli69.safeCode(expected));return;}}
        throw new AssertionError("Unknown harness mode");
    }
}
