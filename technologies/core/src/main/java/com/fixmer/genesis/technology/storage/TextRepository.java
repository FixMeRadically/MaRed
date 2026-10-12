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
    public static boolean validName(String name) { return RepositoryPath.valid(name); }
    public record Entry(String path, boolean directory) {}
    public static final int MAX_ENTRIES = 10_000;
    private void checkRoot() throws IOException {
        for (Path p = root; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Repository path contains a symlink");
        Files.createDirectories(root);
        for (Path p = root; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Repository path contains a symlink");
    }
    private Path checked(String name, boolean document) throws IOException { return checked(name, document, false); }
    private Path checked(String name, boolean document, boolean allowMissingParents) throws IOException {
        if (!validName(name) && !(name != null && name.isEmpty() && !document))
            throw new IOException("Invalid repository path");
        checkRoot();
        Path target = root.resolve(document ? name + ".txt" : name).normalize();
        if (!target.startsWith(root)) throw new IOException("Path escapes repository");
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Repository path contains a symlink: " + name);
            if (!current.equals(target) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)
                && (!allowMissingParents || Files.exists(current, LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("Parent folder does not exist: " + name);
        }
        return target;
    }
    private Path file(String name) throws IOException { return checked(name, true); }
    public synchronized boolean directoryExists(String name) throws IOException {
        return Files.isDirectory(checked(name, false), LinkOption.NOFOLLOW_LINKS);
    }
    public synchronized void createDirectory(String name) throws IOException {
        if (name == null || name.isEmpty()) throw new IOException("Cannot create repository root");
        Files.createDirectory(checked(name, false));
    }
    /** Lists one folder without following links. Empty folders are included. */
    public synchronized List<Entry> entries(String folder) throws IOException {
        Path directory = checked(folder, false);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Folder does not exist: " + folder);
        List<Entry> result = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(directory)) {
            int count = 0;
            for (Path path : stream) {
                if (++count > MAX_ENTRIES) throw new IOException("Folder exceeds entry limit");
                String leaf = path.getFileName().toString();
                boolean dir = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
                if (!dir) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !leaf.endsWith(".txt")) continue;
                    leaf = leaf.substring(0, leaf.length() - 4);
                }
                String name = RepositoryPath.child(folder, leaf);
                if (validName(name)) result.add(new Entry(name, dir));
            }
        }
        result.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::path));
        return List.copyOf(result);
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
    /** Recursive identifiers for runtime lookup; no symlinks are traversed. */
    public synchronized List<String> list() throws IOException {
        checkRoot();
        List<String> names = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            private int visited;
            private void count() throws IOException {
                if (++visited > MAX_ENTRIES) throw new IOException("Repository exceeds entry limit");
            }
            private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }
            @Override public FileVisitResult preVisitDirectory(Path path, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                count();
                return path.equals(root) || validName(relative(path)) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
            }
            @Override public FileVisitResult visitFile(Path path, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                count();
                String name = relative(path);
                if (attrs.isRegularFile() && name.endsWith(".txt")) {
                    name = name.substring(0, name.length() - 4);
                    if (validName(name)) names.add(name);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        names.sort(String::compareTo);
        return List.copyOf(names);
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
        Path tmp = Files.createTempFile(target.getParent(), "save-", ".tmp");
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
    /** Validate all paths without creating folders or replacing existing documents. */
    public synchronized List<String> previewBatch(Map<String,String> documents, Set<String> directories) throws IOException {
        var conflicts = new ArrayList<String>();
        var siblings = new HashMap<Path,Set<String>>();
        for (String folder : directories) {
            Path dest = checked(folder, false, true);
            if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(dest, LinkOption.NOFOLLOW_LINKS)
                || caseAlias(dest, siblings)) conflicts.add(folder + "/");
        }
        for (var entry : documents.entrySet()) {
            encode(entry.getValue());
            Path dest = checked(entry.getKey(), true, true);
            if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS) || caseAlias(dest, siblings)) conflicts.add(entry.getKey());
        }
        return List.copyOf(conflicts);
    }

    private boolean caseAlias(Path target, Map<Path,Set<String>> cache) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false;
        Path parent=target.getParent();
        if (!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS)) return false;
        Set<String> names=cache.get(parent);
        if(names==null){
            names=new HashSet<>();int count=0;
            try(var entries=Files.newDirectoryStream(parent)){for(Path path:entries){
                if(++count>MAX_ENTRIES)throw new IOException("Folder exceeds entry limit");
                names.add(path.getFileName().toString().toLowerCase(Locale.ROOT));
            }}
            cache.put(parent,names);
        }
        return names.contains(target.getFileName().toString().toLowerCase(Locale.ROOT));
    }
    /** Single-process all-or-rollback import. Existing folders may be reused; files never overwrite. */
    public synchronized void createBatch(Map<String,String> documents, Set<String> directories) throws IOException {
        documents = new TreeMap<>(documents); directories = new TreeSet<>(directories);
        if (!previewBatch(documents, directories).isEmpty()) throw new IOException("Import conflicts with existing paths");
        var createdFiles = new ArrayList<Path>(); var createdDirectories = new ArrayList<Path>();
        try {
            for (String folder : directories.stream().sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo)).toList()) {
                Path dest = checked(folder, false);
                if (!Files.isDirectory(dest, LinkOption.NOFOLLOW_LINKS)) { Files.createDirectory(dest); createdDirectories.add(dest); }
            }
            for (var entry : documents.entrySet()) {
                Path dest = file(entry.getKey()); byte[] data = encode(entry.getValue());
                try (var stream = Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    createdFiles.add(dest); stream.write(data);
                }
            }
            for (String name : documents.keySet()) bump(name);
        } catch (IOException | RuntimeException error) {
            Collections.reverse(createdFiles); Collections.reverse(createdDirectories);
            for (Path dest : createdFiles) try { Files.deleteIfExists(dest); } catch (IOException rollback) { error.addSuppressed(rollback); }
            for (Path dest : createdDirectories) try { Files.delete(dest); } catch (IOException rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }
    public synchronized boolean rename(String from,String to) throws IOException {
        Files.move(file(from),file(to));bump(from);bump(to);return true;
    }
}

