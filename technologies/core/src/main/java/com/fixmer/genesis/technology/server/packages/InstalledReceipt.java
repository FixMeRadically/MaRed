package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Local ownership receipt bound to a retained, externally signature-verified archive. Not a remote client claim. */
public final class InstalledReceipt {
    private final UUID profile;private final Side side;private final PackageStaging.Prepared release;
    private InstalledReceipt(UUID profile,Side side,PackageStaging.Prepared release){this.profile=profile;this.side=side;this.release=release;}
    public UUID profile(){return profile;}public Side side(){return side;}public PackageArchive.Verified verified(){return release.verified();}
    static byte[] encode(UUID profile,PackageStaging.Prepared prepared){var v=prepared.verified();String body="GENESIS-INSTALLED-1\n"+profile+"\n"+prepared.target().side()+"\n"+v.manifest().release()+"\n"+v.archiveSha256()+"\n"+v.manifestSha256()+"\n"+v.manifest().loaderVersion()+"\n";return InstallIo.framed(body);}
    public static InstalledReceipt read(Path file,Path cache,Map<String,PublicKey> trust,Target target,UUID expectedProfile)throws IOException{
        return decode(PackageFiles.read(file,2048),cache,trust,target,expectedProfile);
    }
    static void validateLocal(byte[] bytes,UUID profile,Side side)throws IOException{
        String[] f=InstallIo.unframe(bytes,7).split("\n");try{if(!f[0].equals("GENESIS-INSTALLED-1")||!UUID.fromString(f[1]).equals(profile)||!f[2].equals(side.name()))throw new IOException("RECEIPT_PROFILE_OR_SIDE");PackageModel.nonzero(UUID.fromString(f[3]));PackageModel.hash(f[4]);PackageModel.hash(f[5]);PackageModel.text(f[6]);}catch(IllegalArgumentException bad){throw new IOException("INVALID_RECEIPT",bad);}
    }
    static InstalledReceipt decode(byte[] bytes,Path cache,Map<String,PublicKey> trust,Target target,UUID expectedProfile)throws IOException{
        String[] f=InstallIo.unframe(bytes,7).split("\n");
        try{
            if(!f[0].equals("GENESIS-INSTALLED-1"))throw new IOException("RECEIPT_SCHEMA");UUID profile=UUID.fromString(f[1]);PackageModel.nonzero(profile);if(!profile.equals(expectedProfile)||!f[2].equals(target.side().name()))throw new IOException("RECEIPT_PROFILE_OR_SIDE");UUID id=UUID.fromString(f[3]);PackageModel.hash(f[4]);PackageModel.hash(f[5]);PackageModel.text(f[6]);
            var oldTarget=new Target(target.distribution(),target.side(),target.minecraft(),f[6],target.protocol());var prepared=PackageStaging.metadata(cache,f[4],trust,oldTarget);if(!id.equals(prepared.verified().manifest().release())||!f[5].equals(prepared.verified().manifestSha256()))throw new IOException("RECEIPT_RELEASE_MISMATCH");return new InstalledReceipt(profile,target.side(),prepared);
        }catch(IllegalArgumentException bad){throw new IOException("INVALID_RECEIPT",bad);}
    }
}
