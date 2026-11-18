package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local diagnostic model and read-only snapshots. No endpoint grants LIVE installation. */
public final class AdmissionCli {
    private AdmissionCli(){}
    public static void main(String[] args){try{
        if(args.length==2&&args[0].equals("status")){var s=ProfileCoordinator.inspect(Path.of(args[1]));var row=new LinkedHashMap<String,Object>();row.put("report","profile_admission_v39");row.put("readOnly",true);row.put("snapshotOnly",true);row.put("ownerLivenessVerified",false);row.put("profile",s.profile());row.put("scope",s.scope());row.put("epoch",s.epoch());row.put("revision",s.revision());row.put("phase",s.phase());row.put("reason",s.reason());row.put("installationAvailable",false);System.out.println(PackageCli.json(row));return;}
        if(args.length==2&&args[0].equals("demo")){demo(Path.of(args[1]));return;}
        throw new IOException("Usage: demo <NEW-directory> | status <profile>. No LIVE install or override command.");
    }catch(Exception bad){System.err.println(PackageCli.json(Map.of("ok",false,"error",bad.getMessage()==null?bad.getClass().getSimpleName():bad.getMessage())));System.exit(2);}}
    private static void demo(Path root)throws Exception{
        root=root.toAbsolutePath().normalize();PackageFiles.safe(root);Files.createDirectory(root);PackageFiles.privateMode(root,true);UUID profile=UUID.randomUUID(),distribution=UUID.randomUUID(),transaction=UUID.randomUUID();String hash="a".repeat(64);ProfileCoordinator.InstallPermit oldPermit;long epoch;
        try(var owner=ProfileCoordinator.openDiagnostic(root,profile,distribution,PackageModel.Side.CLIENT)){
            var launch=owner.reserveLaunch(UUID.randomUUID());owner.markRunning(launch);boolean blocked=false;try{owner.reserveDiagnosticInstall(transaction,hash);}catch(IOException expected){blocked=true;}if(!blocked)throw new IOException("EXCLUSION_FAILED");owner.observedStopped(launch);oldPermit=owner.reserveDiagnosticInstall(transaction,hash);owner.markInstalling(oldPermit);epoch=owner.snapshot().epoch();
        }
        try(var owner=ProfileCoordinator.openDiagnostic(root,profile,distribution,PackageModel.Side.CLIENT)){
            if(owner.snapshot().phase()!=ProfileCoordinator.Phase.RECOVERY_REQUIRED||owner.snapshot().epoch()<=epoch)throw new IOException("GENERATION_FENCE_FAILED");boolean stale=false;try{owner.validateInstall(oldPermit);}catch(IOException expected){stale=true;}if(!stale)throw new IOException("STALE_PERMIT_ACCEPTED");
            // Pure model demonstration, no process was created and no payload/profile mutation occurred.
            var recovery=owner.beginDiagnosticRecovery(transaction);owner.completeDiagnosticRecovery(recovery);var current=owner.reserveDiagnosticInstall(UUID.randomUUID(),hash);owner.markInstalling(current);owner.completeInstall(current);
            System.out.println(PackageCli.json(Map.of("report","profile_admission_v39","modelOnly",true,"diagnosticOnly",true,"simulatedProcess",true,"minecraftStarted",false,"filesInstalled",0,"stalePermitRejected",true,"epoch",owner.snapshot().epoch(),"phase",owner.snapshot().phase(),"liveInstallationAvailable",false)));
        }
    }
}
