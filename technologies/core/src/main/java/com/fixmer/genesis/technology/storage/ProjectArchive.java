package com.fixmer.genesis.technology.storage;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Portable, data-only Commands ZIP format. No execution, extraction or Minecraft dependencies. */
public final class ProjectArchive {
    public static final int MAX_ENTRIES = 2000;
    public static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    private static final String MANIFEST = "genesis-project.properties";
    private static final String HEADER = "format=genesis-commands\nversion=1\n";
    private ProjectArchive() {}
    public record Plan(Map<String,String> documents, Set<String> directories, long bytes) {
        public Plan {
            documents = Collections.unmodifiableMap(new TreeMap<>(documents));
            directories = Collections.unmodifiableSet(new TreeSet<>(directories));
        }
        public Plan beneath(String prefix) throws IOException {
            if (prefix.isEmpty()) return this;
            if (!RepositoryPath.valid(prefix)) throw new IOException("Invalid destination folder");
            var files = new TreeMap<String,String>(); var folders = new TreeSet<String>();
            addParents(prefix, folders); folders.add(prefix);
            for (var entry : documents.entrySet()) files.put(prefix + "/" + entry.getKey(), entry.getValue());
            for (String directory : directories) folders.add(prefix + "/" + directory);
            return validated(files, folders, bytes);
        }
    }
    private static void interrupted() throws IOException { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Archive operation cancelled"); }
    private static void addParents(String path, Set<String> folders) {
        for (String p = RepositoryPath.parent(path); !p.isEmpty(); p = RepositoryPath.parent(p)) folders.add(p);
    }
    private static Plan validated(Map<String,String> files, Set<String> folders, long bytes) throws IOException {
        if (files.size() + folders.size() > MAX_ENTRIES || bytes > MAX_TOTAL_BYTES) throw new IOException("Project exceeds archive limits");
        Set<String> physical = new HashSet<>();
        for (String folder : folders) {
            if (!RepositoryPath.valid(folder) || !physical.add(folder.toLowerCase(Locale.ROOT))) throw new IOException("Invalid or ambiguous folder: " + folder);
        }
        for (String file : files.keySet()) {
            if (!RepositoryPath.valid(file) || !physical.add((file + ".txt").toLowerCase(Locale.ROOT))) throw new IOException("Invalid or ambiguous file: " + file);
        }
        return new Plan(files, folders, bytes);
    }
    public static Plan snapshot(TextRepository repository) throws IOException {
        synchronized (repository) {
            var files = new TreeMap<String,String>(); var folders = new TreeSet<String>();
            var pending = new ArrayDeque<String>(); pending.add(""); long bytes = 0;
            while (!pending.isEmpty()) {
                interrupted();
                for (var entry : repository.entries(pending.remove())) {
                    if (entry.directory()) { folders.add(entry.path()); pending.add(entry.path()); }
                    else { String text = repository.read(entry.path()); bytes += text.getBytes(StandardCharsets.UTF_8).length; files.put(entry.path(),text); }
                    if (files.size() + folders.size() > MAX_ENTRIES || bytes > MAX_TOTAL_BYTES) throw new IOException("Project exceeds archive limits");
                }
            }
            return validated(files, folders, bytes);
        }
    }
    /** Write to a fresh name; publish only a complete archive. Parent folder must exist. */
    public static void write(Path destination, Plan plan) throws IOException {
        plan = validated(plan.documents(), plan.directories(), plan.bytes());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archive already exists");
        Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), "genesis-export-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                put(zip, MANIFEST, HEADER.getBytes(StandardCharsets.UTF_8));
                long bytes = 0;
                for (String folder : plan.directories()) { interrupted(); put(zip, "commands/" + folder + "/", new byte[0]); }
                for (var file : plan.documents().entrySet()) {
                    interrupted(); StandardCharsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(file.getValue()));
                    byte[] data;
                    // Encoder buffers may have unused capacity; use exact UTF-8 bytes after strict validation.
                    data = file.getValue().getBytes(StandardCharsets.UTF_8); bytes += data.length;
                    if (data.length > TextRepository.MAX_BYTES || bytes > MAX_TOTAL_BYTES) throw new IOException("Project exceeds archive limits");
                    put(zip, "commands/" + file.getKey() + ".txt", data);
                }
            }
            Files.move(temporary, destination); // no replace; do not clobber a concurrently created export
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void put(ZipOutputStream zip, String path, byte[] data) throws IOException {
        var entry = new ZipEntry(path); entry.setTime(0); zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
    }
    /** Read bounded entry streams. Archive paths are identifiers, never extraction destinations. */
    public static Plan read(Path archive) throws IOException {
        if (Files.isSymbolicLink(archive) || !Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid archive file");
        if (Files.size(archive) > 64L * 1024 * 1024) throw new IOException("Compressed archive exceeds 64 MiB limit");
        final int directoryEntries;
        try(var directory=new ZipFile(archive.toFile(),StandardCharsets.UTF_8)) {
            directoryEntries=directory.size();
            if(directoryEntries>MAX_ENTRIES+1)throw new IOException("Too many archive entries");
        } catch(IllegalArgumentException error) { throw new IOException("Invalid ZIP directory",error); }
        var files = new TreeMap<String,String>(); var folders = new TreeSet<String>();
        Set<String> names = new HashSet<>(); boolean manifest = false; long total = 0; int count = 0;
        try (var input = Files.newInputStream(archive,LinkOption.NOFOLLOW_LINKS); var zip = new ZipInputStream(input,StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                interrupted(); if (++count > MAX_ENTRIES + 1) throw new IOException("Too many archive entries");
                String name = entry.getName();
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate archive entry: " + name);
                int limit = name.equals(MANIFEST) ? 4096 : entry.isDirectory() ? 0 : TextRepository.MAX_BYTES;
                byte[] data = zip.readNBytes(limit + 1);
                if (data.length > limit) throw new IOException("Archive entry exceeds size limit: " + name);
                total += data.length; if (total > MAX_TOTAL_BYTES + 4096L) throw new IOException("Archive exceeds total size limit");
                if (name.equals(MANIFEST)) {
                    if (!new String(data,StandardCharsets.UTF_8).equals(HEADER)) throw new IOException("Unsupported project format/version");
                    manifest = true;
                } else {
                    if (!name.startsWith("commands/")) throw new IOException("Unknown archive entry: " + name);
                    String relative = name.substring(9);
                    if (entry.isDirectory()) {
                        if (relative.isEmpty()) continue;
                        relative = relative.substring(0,relative.length()-1);
                        if (!RepositoryPath.valid(relative)) throw new IOException("Invalid folder path: " + name);
                        folders.add(relative); addParents(relative,folders);
                    } else {
                        if (!relative.endsWith(".txt")) throw new IOException("Only text documents can be imported");
                        relative = relative.substring(0,relative.length()-4);
                        if (!RepositoryPath.valid(relative)) throw new IOException("Invalid file path: " + name);
                        files.put(relative,StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString());
                        addParents(relative,folders);
                    }
                }
                zip.closeEntry();
            }
        } catch (IllegalArgumentException error) { throw new IOException("Invalid ZIP encoding",error); }
        if (count != directoryEntries) throw new IOException("ZIP directory does not match entry stream");
        if (!manifest) throw new IOException("Missing Genesis project manifest");
        return validated(files,folders,total - HEADER.getBytes(StandardCharsets.UTF_8).length);
    }
}
