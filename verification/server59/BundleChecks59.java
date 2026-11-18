package com.fixmer.mared.technology.server.setup;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class BundleChecks59 {
    private static int checks;
    static byte[] zip(Map<String, byte[]> entries) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    static Map<String, byte[]> valid() {
        var entries = new LinkedHashMap<String, byte[]>();
        for (String name : List.of("tools/server_setup.py", "tools/first_launch.py", "runtime/core.jar")) entries.put(name, new byte[]{1,2,3});
        return entries;
    }
    static void rejected(Path base, Map<String, byte[]> entries) throws Exception {
        Path root = Files.createTempDirectory(base, "bad-");
        try { ServerSetupBundle59.unpack(root, new ByteArrayInputStream(zip(entries))); throw new AssertionError("Accepted invalid bundle"); }
        catch (IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("server59-bundle-");
        try {
            Path root = Files.createDirectory(base.resolve("valid"));
            ServerSetupBundle59.unpack(root, new ByteArrayInputStream(zip(valid())));
            for (String file : valid().keySet()) { if (!Arrays.equals(Files.readAllBytes(root.resolve(file)), new byte[]{1,2,3})) throw new AssertionError(); checks++; }
            var traversal = valid(); traversal.put("../escape.py", new byte[]{1}); rejected(base,traversal);
            if (Files.exists(base.resolve("escape.py"))) throw new AssertionError("Escaped extraction root"); checks++;
            var absolute = valid(); absolute.put("/escape.py", new byte[]{1}); rejected(base,absolute);
            var drive = valid(); drive.put("C:/escape.py", new byte[]{1}); rejected(base,drive);
            var backslash = valid(); backslash.put("tools\\escape.py", new byte[]{1}); rejected(base,backslash);
            var executable = valid(); executable.put("tools/run.bat", new byte[]{1}); rejected(base,executable);
            var incomplete = valid(); incomplete.remove("runtime/core.jar"); rejected(base,incomplete);
            var oversized = valid(); oversized.put("tools/huge.py", new byte[16*1024*1024]); rejected(base,oversized);
            Path linked = base.resolve("linked"); Files.createSymbolicLink(linked,root);
            try { ServerSetupBundle59.ensureDirectory(linked.resolve("child")); throw new AssertionError("Accepted symlink"); }
            catch (IOException expected) { checks++; }
            System.out.println("Pure-JDK bundle checks passed: " + checks);
        } finally {
            try (var walk=Files.walk(base)) { for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path); }
        }
    }
}
