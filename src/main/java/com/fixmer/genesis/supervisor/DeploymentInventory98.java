package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Content inventory, not a NeoForge resolver. No timestamps used as content identity. */
final class DeploymentInventory98 {
    private static final int MAX_FILES=512;
    private static final long MAX_TOTAL=2L*1024*1024*1024;
    private DeploymentInventory98() {}
    private static void safe(Path path)throws IOException {
        for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent()) {
            if(Files.isSymbolicLink(p))throw new IOException("Linked mod inventory is unsupported");
            if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&!p.toRealPath().equals(p.toAbsolutePath().normalize()))throw new IOException("Aliased mod inventory is unsupported");
        }
    }
    private static List<Path> files(Path mods)throws IOException {
        safe(mods);var result=new ArrayList<Path>();
        try(var paths=Files.newDirectoryStream(mods)) {
            int entries=0;
            for(Path p:paths) {
                if(++entries>4096)throw new IOException("Mod directory entry limit exceeded");
                if(p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    safe(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Mod JAR is not a regular file");
                    if(result.size()>=MAX_FILES)throw new IOException("Mod inventory exceeds 512 JARs");
                    result.add(p);
                }
            }
        }
        result.sort(Comparator.comparing(p->p.getFileName().toString()));return List.copyOf(result);
    }
    static String capture(Path mods)throws IOException {
        var paths=files(mods);long total=0;
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
        try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))) {
            out.writeUTF("Genesis mods inventory98");out.writeInt(paths.size());
            for(Path p:paths) {
                if(Thread.currentThread().isInterrupted())throw new IOException("Mod review interrupted");
                var before=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if((total+=before.size())>MAX_TOTAL)throw new IOException("Mod inventory exceeds 2 GiB");
                String hash=ModDeployment.hash(p);
                var after=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                safe(p);
                if(!after.isRegularFile()||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("Mod JAR changed during review");
                out.writeUTF(p.getFileName().toString());out.writeLong(after.size());out.writeUTF(hash);
            }
            if(!paths.equals(files(mods)))throw new IOException("Mod directory changed during review");
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
