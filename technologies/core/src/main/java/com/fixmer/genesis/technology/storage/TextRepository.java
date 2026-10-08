package com.fixmer.genesis.technology.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** One-process optimistic UTF-8 repository. Cross-process compare-and-swap is not guaranteed. */
public final class TextRepository {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private final Path root;
    private final Map<String, Long> versions = new HashMap<>();
    public record Ticket(TextRepository owner, String name, long version, String fingerprint) {}
    public TextRepository(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public static boolean validName(String name) {
        return name != null && name.length() <= 64 && name.matches("[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*");
    }
    private void checkRoot() throws IOException {
        for (Path p = root; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Repository path contains a symlink");
        Files.createDirectories(root);
        for (Path p = root; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Repository path contains a symlink");
    }
    private Path file(String name) throws IOException {
        if (!validName(name)) throw new IOException("Invalid file name");
        checkRoot();
        Path p = root.resolve(name + ".txt");
        if (Files.isSymbolicLink(p)) throw new IOException("File is a symlink: " + name);
        return p;
    }
    private byte[] bytes(Path file) throws IOException {
        try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) throw new IOException("File exceeds 4 MiB limit");
            return data;
        }
    }
    private static byte[] encode(String text) throws IOException {
        var encoded=StandardCharsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(text));
        if(encoded.remaining()>MAX_BYTES)throw new IOException("File exceeds 4 MiB limit");
        byte[] data=new byte[encoded.remaining()];encoded.get(data);return data;
    }
    private static String fingerprint(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private long bump(String name) { long v = versions.getOrDefault(name, 0L) + 1; versions.put(name, v); return v; }
    public synchronized String read(String name) throws IOException {
        byte[] data = bytes(file(name));
        return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();
    }
    public synchronized boolean exists(String name) throws IOException { return Files.isRegularFile(file(name), LinkOption.NOFOLLOW_LINKS); }
    public synchronized List<String> list() throws IOException {
        checkRoot();
        try (var files = Files.list(root)) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                .map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".txt"))
                .map(n -> n.substring(0, n.length()-4)).filter(TextRepository::validName).sorted().toList();
        }
    }
    /** Reserves a newer intent; earlier queued saves can no longer commit. */
    public synchronized Ticket capture(String name) throws IOException {
        String digest = fingerprint(bytes(file(name)));
        return new Ticket(this, name, bump(name), digest);
    }
    public synchronized Ticket capture(String name, String expectedText) throws IOException {
        return capture(name, expectedText, null);
    }
    /** Accepts a known earlier save from this document while its UI callback is pending. */
    public synchronized Ticket capture(String name, String expectedText, String earlierSave) throws IOException {
        byte[] data = bytes(file(name));
        String actual = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();
        if (!actual.equals(expectedText) && (earlierSave == null || !actual.equals(earlierSave)))
            throw new IOException("File changed on disk: " + name);
        return new Ticket(this, name, bump(name), fingerprint(data));
    }
    public synchronized void invalidate(String name) throws IOException { file(name); bump(name); }
    public synchronized boolean current(Ticket ticket) {
        return ticket.owner == this && versions.getOrDefault(ticket.name, 0L) == ticket.version;
    }
    public synchronized boolean write(Ticket ticket, String text) throws IOException {
        if (ticket.owner != this || versions.getOrDefault(ticket.name, 0L) != ticket.version) return false;
        Path target = file(ticket.name);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || !fingerprint(bytes(target)).equals(ticket.fingerprint)) return false;
        byte[] data = encode(text);
        if (data.length > MAX_BYTES) throw new IOException("File exceeds 4 MiB limit");
        Path tmp = Files.createTempFile(root, "save-", ".tmp");
        try {
            Files.write(tmp, data);
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
            bump(ticket.name);
            return true;
        } finally { Files.deleteIfExists(tmp); }
    }
    public synchronized boolean create(String name, String text) throws IOException {
        Path target = file(name);byte[] data=encode(text);
        if(data.length>MAX_BYTES)throw new IOException("File exceeds 4 MiB limit");
        try (var stream=Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { stream.write(data); }
        bump(name);return true;
    }
    public synchronized boolean delete(String name) throws IOException { boolean deleted=Files.deleteIfExists(file(name));bump(name);return deleted; }
    public synchronized boolean rename(String from,String to) throws IOException {
        Files.move(file(from),file(to));bump(from);bump(to);return true;
    }
}

