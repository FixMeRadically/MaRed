package com.fixmer.genesis.technology.scripts;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
/** Reproducible project input identity; output/cache/world folders do not count as sources. */
public final class BuildInputs  {
    private BuildInputs() {
    }
    private static final Set<String> OUTPUTS=Set.of("build","out","run","runs",".git",".gradle",".idea",".genesis-build",".genesis-gradle-cache",".genesis-recovery",".artifacts",".exports");
    public record Snapshot(Map<String,String> files) {
        public Snapshot {
            files=Map.copyOf(files);
        }
        public String fingerprint(Collection<String> excludes) {
            var hash=digest();
            for(var e:new TreeMap<>(files).entrySet()) {
                if(excludes.stream().anyMatch(p->e.getKey().equals(p)||e.getKey().startsWith(p+"/")))continue;
                hash.update(e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                hash.update((byte)0);
                hash.update(e.getValue().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                hash.update((byte)10);
            }
            return HexFormat.of().formatHex(hash.digest());
        }
    }
    public static String capture(Path project,BooleanSupplier cancel)throws IOException {
        return snapshot(project,cancel).fingerprint(List.of());
    }
    public static String capture(Path project,BooleanSupplier cancel,Collection<String> excludes)throws IOException {
        return snapshot(project,cancel).fingerprint(excludes);
    }
    public static Snapshot snapshot(Path project,BooleanSupplier cancel)throws IOException {
        Path root=project.toAbsolutePath().normalize();
        var files=new TreeMap<String,String>();
        long[] bytes= {
            0
        };
        int[] entries= {
            0
        };
        Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
            void stop()throws IOException {
                if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("Build input inspection cancelled");if(++entries[0]>50000)throw new IOException("Build input entry limit: 50000");
            }
            public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException {
                stop();if(!p.equals(root)&&OUTPUTS.contains(p.getFileName().toString().toLowerCase(Locale.ROOT)))return FileVisitResult.SKIP_SUBTREE;if(Files.isSymbolicLink(p)||!p.toRealPath().startsWith(root.toRealPath()))throw new IOException("Linked build input: "+p);return FileVisitResult.CONTINUE;
            }
            public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException {
                stop();if(p.getFileName().toString().equals(DependencyJournal.NAME))return FileVisitResult.CONTINUE;if(!a.isRegularFile()||a.isSymbolicLink())throw new IOException("Non-regular build input: "+p);if((bytes[0]+=a.size())>1024L*1024*1024)throw new IOException("Build input byte limit: 1 GiB");var hash=digest();long count=0;try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)) {
                    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1) {
                        if(cancel.getAsBoolean())throw new IOException("Build input inspection cancelled");count+=n;if(count>a.size())throw new IOException("Build input changed: "+p);hash.update(b,0,n);
                    }
                }
                var after=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(count!=a.size()||!after.isRegularFile()||!Objects.equals(a.fileKey(),after.fileKey())||!a.lastModifiedTime().equals(after.lastModifiedTime()))throw new IOException("Build input changed: "+p);files.put(root.relativize(p).toString().replace('\\','/'),HexFormat.of().formatHex(hash.digest()));return FileVisitResult.CONTINUE;
            }
        });
        return new Snapshot(files);
    }
    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch(NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
