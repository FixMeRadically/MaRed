package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Bounded explicit log export. No recursive traversal and no keys, launch argv or profile data. */
public final class ServerDiagnostics105 {
    private ServerDiagnostics105() {}
    private static final int TAIL = 512 * 1024;
    private static final Pattern SECRET = Pattern.compile("(?i)([\\\"']?(?:access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization|password)[\\\"']?\\s*[:=]\\s*)(?:Bearer\\s+)?(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}]+)");
    public static String redact(String text) {
        return SECRET.matcher(text).replaceAll("$1[redacted]")
            .replaceAll("(?i)(--(?:accessToken|clientSecret|password)\\s+)(?:\\\"[^\\\"]*\\\"|[^\\s]+)", "$1[redacted]")
            .replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1[redacted]");
    }
    private static byte[] tail(Path path) throws IOException {
        FirstLaunchFiles.safe(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("SERVER105_REPORT_FILE");
        try (var in = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = in.size(), offset = Math.max(0, size - TAIL);
            in.position(offset);
            ByteBuffer bytes = ByteBuffer.allocate((int)Math.min(size, TAIL));
            while (bytes.hasRemaining() && in.read(bytes) > 0) {}
            String text = new String(bytes.array(), 0, bytes.position(), StandardCharsets.UTF_8);
            if (offset > 0) { int line = text.indexOf('\n'); text = "[Earlier lines omitted]\n" + (line >= 0 ? text.substring(line + 1) : text); }
            return redact(text).getBytes(StandardCharsets.UTF_8);
        }
    }
    public static Path export(Path server) throws IOException {return export(server,null);}
    public static Path export(Path server,Path clientLog) throws IOException {
        if(!Files.isDirectory(FirstLaunchFiles.safe(server)))throw new IOException("SERVER105_FOLDER_REQUIRED");
        Path root = ServerApp104.root(server), logs = FirstLaunchFiles.safe(root.resolve("logs"));
        Path reports = FirstLaunchFiles.safe(root.resolve("reports"));
        Files.createDirectories(reports); FirstLaunchFiles.privateMode(reports);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Path result = reports.resolve("Genesis-server-report-" + stamp + "-" + UUID.randomUUID().toString().substring(0,8) + ".zip");
        Path temporary = Files.createTempFile(reports, ".report105-", ".tmp");
        try {
            FirstLaunchFiles.privateMode(temporary);
            var files = new ArrayList<Path>();
            for (String name : List.of("controller.log", "launch.log", "launcher.log", "setup-error.log", "helper-error106.log", "helper-warning106.log", "premain106-error.log", "bootstrap106.log", "auto-launch106.log", "console-launch106.log", "status.json", "update115.json", "update115-verification.log", "rollout118.json", "folder118-status.json")) {
                Path file = logs.resolve(name);
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) files.add(file);
            }
            if (Files.isDirectory(logs)) try (var stream = Files.list(logs)) {
                files.addAll(stream.filter(p -> p.getFileName().toString().matches("server-[a-f0-9-]{36}\\.log"))
                    .sorted(Comparator.comparingLong((Path p) -> { try { return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis(); } catch(IOException e) { return 0; } }).reversed())
                    .limit(4).toList());
            }
            Path update=logs.resolve("update115.json");
            if(Files.isRegularFile(update,LinkOption.NOFOLLOW_LINKS)&&Files.size(update)<16384){
                String text=Files.readString(FirstLaunchFiles.safe(update));
                var candidate=Pattern.compile("\"candidateLaunch\"\\s*:\\s*\"([a-f0-9-]{36})\"").matcher(text);
                if(candidate.find()) {Path file=FirstLaunchFiles.safe(logs.resolve("server-"+candidate.group(1)+".log"));if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&!files.contains(file))files.add(file);}
            }
            Path minecraft=FirstLaunchFiles.safe(server.resolve("logs/latest.log"));if(Files.isRegularFile(minecraft,LinkOption.NOFOLLOW_LINKS))files.add(minecraft);
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                zip.putNextEntry(new ZipEntry("README.txt"));
                zip.write(("Genesis server108 diagnostic logs\nOnly selected log tails are included. Known credential patterns are redacted.\nMinecraft/Windows operation is not certified by the existence of this report.\n").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                for (Path file : files) {
                    // Skip a concurrently removed/linked log rather than including another file through it.
                    byte[] data;
                    try { data = tail(file); } catch(IOException unsafeOrGone) { continue; }
                    zip.putNextEntry(new ZipEntry(file.equals(minecraft)?"minecraft-latest.log":file.getFileName().toString())); zip.write(data); zip.closeEntry();
                }
                if(clientLog!=null)try{byte[] data=tail(clientLog);zip.putNextEntry(new ZipEntry("client-latest.log"));zip.write(data);zip.closeEntry();}catch(IOException unavailable){}
            }
            Files.move(temporary, result, StandardCopyOption.ATOMIC_MOVE);
            return result;
        } finally { Files.deleteIfExists(temporary); }
    }
}
