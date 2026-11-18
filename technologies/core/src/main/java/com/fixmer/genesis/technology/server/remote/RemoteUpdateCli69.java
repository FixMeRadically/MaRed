package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.packages.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Author a public HTTPS directory from an existing signed CLIENT package; never exports private.der. */
public final class RemoteUpdateCli69 {
    private RemoteUpdateCli69(){}
    public static RemoteOffer69 verifyPublic(Path folder)throws Exception{
        return verifyPublic(folder,true);
    }
    /** Historical verification is for local authoring and listener initialization; network consumers require freshness. */
    public static RemoteOffer69 verifyPublic(Path folder,boolean fresh)throws Exception{
        folder=FirstLaunchFiles.safe(folder);byte[] key=FirstLaunchFiles.read(folder.resolve("public.der"),256);var offer=RemoteOffer69.decode(FirstLaunchFiles.read(folder.resolve("offer.bin"),8192));offer.verify(RemoteOffer69.publicKey(key),FirstLaunchFiles.read(folder.resolve("offer.sig"),64));if(fresh)offer.validAt(System.currentTimeMillis());
        String archive="client-"+offer.archiveSha()+".zip";try(var files=Files.list(folder)){if(!files.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("public.der","offer.bin","offer.sig",archive)))throw new java.io.IOException("REMOTE_PUBLIC_DIRECTORY");}
        Path path=FirstLaunchFiles.safe(folder.resolve(archive));if(Files.size(path)!=offer.bytes())throw new java.io.IOException("REMOTE_ARCHIVE_HASH");
        var result=PackageArchive.verify(path,Map.of(offer.signer(),RemoteOffer69.publicKey(key)),new PackageModel.Target(offer.distribution(),PackageModel.Side.CLIENT,offer.minecraft(),offer.neoforge(),1));
        if(!result.archiveSha256().equals(offer.archiveSha())||!result.manifest().release().equals(offer.release())||!Objects.equals(result.manifest().parent(),offer.parent())||result.manifest().files().stream().anyMatch(e->e.side()!=PackageModel.Side.CLIENT))throw new java.io.IOException("REMOTE_PACKAGE_BINDING");return offer;
    }
    public static void main(String[] args){try{
        if(args.length==4&&args[0].equals("init")){RemotePublisher69.initialize(Path.of(args[1]),args[2],args[3]);System.out.println("Persistent remote publisher identity created. Keep private.der private.");return;}
        if(args.length==5&&args[0].equals("publish")){var o=RemotePublisher69.publish(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),Long.parseLong(args[4]));System.out.println("Published CLIENT offer: "+o.release()+"\nSequence: "+o.sequence()+"\nSHA256 public key: "+o.signer()+"\nPrivate key was not exported. Install authority: false");return;}
        if(args.length==2&&Set.of("verify-public","verify-public-history").contains(args[0])){var o=verifyPublic(Path.of(args[1]),args[0].equals("verify-public"));System.out.println("{\"endpoint\":\""+o.endpoint()+"\",\"archive\":\"client-"+o.archiveSha()+".zip\",\"sequence\":"+o.sequence()+",\"expires\":"+o.expires()+",\"release\":\""+o.release()+"\"}");return;}
        if(args.length==6&&args[0].equals("check")){var c=new RemoteClient69(Path.of(args[1]),args[4],args[5]);var p=c.inspect(args[2],args[3],()->false);System.out.println("Release: "+p.offer().release()+"\nKey: "+p.offer().signer()+"\nTrusted: "+p.trusted());return;}
        throw new IllegalArgumentException("REMOTE_ARGUMENTS");
    }catch(Exception bad){System.err.println("Remote operation failed: "+safeCode(bad)+". No game profile was installed.");System.exit(2);}}
    public static String safeCode(Exception error){String code=error.getMessage();return code!=null&&code.matches("REMOTE_[A-Z_]{1,64}")?code:error instanceof javax.net.ssl.SSLException?"REMOTE_TLS":error instanceof java.net.SocketTimeoutException?"REMOTE_TIMEOUT":error instanceof java.io.InterruptedIOException?"REMOTE_CANCELLED":"REMOTE_IO";}
}
