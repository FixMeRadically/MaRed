package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Select a bounded immutable JAR snapshot; identical copies do not create false ambiguity. */
public final class ServerArtifact105 {
    private ServerArtifact105() {}
    public record Artifact(Path path, byte[] bytes) {}
    private static final String MARKER = "com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class";
    private static final String BUNDLE = "genesis/server59/controller.zip";
    private static boolean entry(ZipFile zip, String name, byte[] expected) throws IOException {
        var e = zip.getEntry(name);
        if(e == null || e.isDirectory() || e.getSize() != expected.length) return false;
        try(var in = zip.getInputStream(e)) { return Arrays.equals(expected, in.readNBytes(expected.length + 1)); }
    }
    public static Artifact select(Collection<Path> candidates, Path preferred, byte[] marker, byte[] bundle) throws IOException {
        Artifact accepted = null; String digest = null;
        var ordered = new LinkedHashSet<Path>();
        if(preferred != null) ordered.add(preferred.toAbsolutePath().normalize());
        for(Path p : candidates) ordered.add(p.toAbsolutePath().normalize());
        if(ordered.size() > 512) throw new IOException("SERVER105_ARTIFACT_LIMIT");
        for(Path p : ordered) {
            byte[] snapshot;
            try {
                FirstLaunchFiles.safe(p);
                if(!Files.isRegularFile(p) || Files.size(p) > 64L*1024*1024) continue;
                try(var zip = new ZipFile(p.toFile())) {
                    if(zip.getEntry("com/fixmer/mared/Mared.class") == null || !entry(zip, MARKER, marker) || !entry(zip, BUNDLE, bundle)) continue;
                    var names = new HashSet<String>();
                    var entries = zip.entries();
                    boolean duplicate = false;
                    int count=0;
                    while(entries.hasMoreElements()) if(++count>50000 || !names.add(entries.nextElement().getName())) { duplicate = true; break; }
                    if(duplicate) continue;
                }
                snapshot = FirstLaunchFiles.read(p,64*1024*1024);
                // Inspect an immutable bounded snapshot through ZipFile: skipping a ZIP entry must
                // not inflate an unrelated compressed bomb through ZipInputStream.getNextEntry().
                Path temporary=Files.createTempFile("genesis-artifact105-",".jar");
                try {
                    FirstLaunchFiles.privateMode(temporary); Files.write(temporary,snapshot);
                    try(var z=new ZipFile(temporary.toFile())) {
                        if(z.getEntry("com/fixmer/mared/Mared.class")==null || !entry(z,MARKER,marker) || !entry(z,BUNDLE,bundle)) continue;
                        var names=new HashSet<String>();var iterator=z.entries();boolean valid=true;int count=0;
                        while(iterator.hasMoreElements()) if(++count>50000 || !names.add(iterator.nextElement().getName())) { valid=false;break; }
                        if(!valid) continue;
                    }
                } finally { Files.deleteIfExists(temporary); }

            } catch(IOException invalid) { continue; }
            String hash = FirstLaunchFiles.sha(snapshot);
            if(preferred != null && p.equals(preferred.toAbsolutePath().normalize())) return new Artifact(p,snapshot);
            if(accepted != null && !hash.equals(digest)) throw new IOException("SERVER105_AMBIGUOUS_BUILD");
            if(accepted == null) { accepted = new Artifact(p,snapshot); digest = hash; }
        }
        if(accepted == null) throw new IOException("SERVER104_BUILD_JAR_REQUIRED");
        return accepted;
    }
}
