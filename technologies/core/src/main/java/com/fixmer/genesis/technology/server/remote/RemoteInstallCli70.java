package com.fixmer.genesis.technology.server.remote;

import java.nio.file.*;

/** Revalidates a downloaded CLIENT candidate; emits only public metadata, never launch arguments. */
public final class RemoteInstallCli70 {
    private RemoteInstallCli70(){}
    public static void main(String[] args){try{
        if(args.length!=5||!args[0].equals("verify"))throw new IllegalArgumentException("REMOTE70_ARGUMENTS");
        Path directory=Path.of(args[2]);
        var client=RemoteClient69.forCandidate(Path.of(args[1]),args[3],args[4],directory);
        var candidate=client.cachedVerified(directory,()->false);
        var o=candidate.preview().offer();
        if(Files.exists(directory.resolve("bootstrap76.cfg")))
            Bootstrap76.bindCandidate(directory,Bootstrap76.pollCandidate(directory,args[3],args[4],()->false),candidate.archive());
        var files=candidate.archive().manifest().files().stream().map(e->"{\"path\":\""+e.path()+"\",\"sha256\":\""+e.sha256()+"\",\"bytes\":"+e.bytes()+"}").toList();
        System.out.println("{\"game\":\""+o.game()+"\",\"distribution\":\""+o.distribution()+"\",\"endpoint\":\""+o.endpoint()+"\",\"release\":\""+o.release()+"\",\"archiveSha256\":\""+o.archiveSha()+"\",\"files\":["+String.join(",",files)+"],\"installAuthority\":false}");
    }catch(Exception bad){System.err.println("Remote candidate rejected: "+RemoteUpdateCli69.safeCode(bad));System.exit(2);}}
}
