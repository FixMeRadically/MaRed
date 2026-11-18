package com.fixmer.mared.dev.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One-shot restart intent. No Minecraft dependencies; all writes are atomic. */
public final class DevResumeStore {
    public record Target(String name, String address, String packPolicy, String world) {
        public Target(String name, String address, String packPolicy) { this(name,address,packPolicy,""); }
        public static Target local(String world) { return new Target(world,"","PROMPT",world); }
        public boolean localWorld() { return world != null && !world.isEmpty(); }
    }
    public record Boot(boolean skipLocalWorld, Target target) { }
    private static final long TTL = 10 * 60 * 1000L;
    private DevResumeStore() { }
    public static String prepare(Path file, Target target, long pid, long now) throws IOException {
        Properties p = new Properties(); String id = UUID.randomUUID().toString();
        p.setProperty("id", id); p.setProperty("sourcePid", Long.toString(pid));
        p.setProperty("createdAt", Long.toString(now)); p.setProperty("kind", target == null ? "default" : target.localWorld() ? "world" : "server");
        if (target != null) {
            if (!target.localWorld() && (target.address() == null || target.address().isBlank())) throw new IOException("Server address is unavailable");
            if (target.localWorld() && !validWorld(target.world())) throw new IOException("Invalid world id");
            p.setProperty("world",target.world());
            p.setProperty("address", target.address()); p.setProperty("name", target.name()); p.setProperty("packPolicy", target.packPolicy());
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "resume-", ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { p.store(out, "MaRed one-shot client restart intent"); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        return id;
    }
    private static boolean validWorld(String id) {
        return id != null && !id.isBlank() && id.chars().noneMatch(c -> c < 32)
            && !id.equals(".") && !id.contains("..") && id.indexOf('/') < 0 && id.indexOf((char)92) < 0 && id.indexOf(':') < 0;
    }
    private static Properties read(Path file) throws IOException {
        if (Files.size(file) > 65536) throw new IOException("Restart intent exceeds 64 KiB");
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(in); }
        return p;
    }
    public static void cancel(Path file, String id) throws IOException {
        if (id != null && Files.isRegularFile(file) && id.equals(read(file).getProperty("id"))) Files.delete(file);
    }
    public static Boot consume(Path file, long pid, long now) throws IOException {
        if (!Files.isRegularFile(file)) return new Boot(false, null);
        Properties p;
        try { p = read(file); }
        catch (IOException | IllegalArgumentException e) {
            Files.deleteIfExists(file); return new Boot(true, null);
        }
        // The current client must not consume the intent it has just prepared.
        if (Long.toString(pid).equals(p.getProperty("sourcePid"))) return new Boot(false, null);
        Files.delete(file); // consume before connecting: no retry loop on failure or later TitleScreens
        String kind = p.getProperty("kind");
        if (!"server".equals(kind) && !"world".equals(kind)) return new Boot(false, null);
        try {
            long age = now - Long.parseLong(p.getProperty("createdAt", "0"));
            if (age < 0 || age > TTL) return new Boot(true,null);
            if ("world".equals(kind)) { String world = p.getProperty("world", ""); return new Boot(true, validWorld(world) ? Target.local(world) : null); }
            String address = p.getProperty("address", "");
            if (age < 0 || age > TTL || address.isBlank()) return new Boot(true, null);
            return new Boot(true, new Target(p.getProperty("name", address), address, p.getProperty("packPolicy", "PROMPT")));
        } catch (NumberFormatException e) { return new Boot(true, null); }
    }
}
