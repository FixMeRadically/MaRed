package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.packages.*;
import java.nio.file.*;
import java.util.*;

/** Revalidates a downloaded CLIENT candidate; emits only public metadata, never launch arguments. */
public final class RemoteInstallCli70 {
    private RemoteInstallCli70(){}
    public static void main(String[] args){try{
        if(args.length!=5||!args[0].equals("verify"))throw new IllegalArgumentException("REMOTE70_ARGUMENTS");
        Path directory=Path.of(args[2]);var client=new RemoteClient69(Path.of(args[1]),args[3],args[4]);var preview=client.cached(directory,()->false);var o=preview.offer();
        var packageInfo=PackageArchive.verify(directory.resolve("client.zip"),Map.of(o.signer(),RemoteOffer69.publicKey(preview.key())),new PackageModel.Target(o.distribution(),PackageModel.Side.CLIENT,args[3],args[4],1));
        var files=packageInfo.manifest().files().stream().map(e->"{\"path\":\""+e.path()+"\",\"sha256\":\""+e.sha256()+"\",\"bytes\":"+e.bytes()+"}").toList();
        System.out.println("{\"game\":\""+o.game()+"\",\"distribution\":\""+o.distribution()+"\",\"endpoint\":\""+o.endpoint()+"\",\"release\":\""+o.release()+"\",\"archiveSha256\":\""+o.archiveSha()+"\",\"files\":["+String.join(",",files)+"],\"installAuthority\":false}");
    }catch(Exception bad){System.err.println("Remote candidate rejected: "+RemoteUpdateCli69.safeCode(bad));System.exit(2);}}
}
