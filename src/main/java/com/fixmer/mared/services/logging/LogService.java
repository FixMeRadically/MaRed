package com.fixmer.mared.services.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fixmer.mared.Mared;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Сервис лога.
 *
 * 0.3.1:
 *   - addStructured(Level, category, message) — новый API.
 *     Caller'ы, знающие уровень, не платят за повторный парсинг.
 *   - add(String) — обратная совместимость; парсит один раз при
 *     создании LogEntry (внутри конструктора).
 *   - snapshot(), countVisible() используют LogEntry.level/category —
 *     без повторного парсинга текста на каждом рендере.
 *   - save() — revision-based, при ошибке dirty не сбрасывается.
 *   - loadFromDisk() — restore не блокируется сессионными add().
 */
public final class LogService {

    private static final int MAX_ENTRIES = 2000;
    private static final int TRIM_BATCH  = 200;
    private static final int DISK_BUDGET142 = 8 * 1024 * 1024;

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter EXPORT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final LogService INSTANCE = new LogService();

    public static LogService get() { return INSTANCE; }

    private final List<LogEntry> entries =
        Collections.synchronizedList(new ArrayList<>());

    private volatile boolean dirty = false;
    private volatile long dataRevision = 0;
    private volatile int version = 0;

    private boolean loadedFromDisk = false;

    private LogService() {}

    public int version() { return version; }
    private void bumpVersion() { version++; }

    // ============================================================
    //  Mutations
    // ============================================================

    /** Legacy path. Парсит level/category из строки. */
    public void add(String line) {
        long now = System.currentTimeMillis();
        String time = LocalTime.now().format(TIME_FMT);
        LogEntry entry = new LogEntry(time, now, line, null);
        appendEntry(entry);
    }

    /**
     * 0.3.1: structured path. Уровень и категория известны заранее —
     * никакого повторного парсинга.
     */
    public void addStructured(LogSettings.Level level,
                              String category,
                              String message) {
        long now = System.currentTimeMillis();
        String time = LocalTime.now().format(TIME_FMT);
        LogEntry entry = new LogEntry(time, now, level, category, message, null);
        appendEntry(entry);
    }

    public void addStructured(LogSettings.Level level,
                              String category,
                              String message,
                              String source) {
        long now = System.currentTimeMillis();
        String time = LocalTime.now().format(TIME_FMT);
        LogEntry entry = new LogEntry(time, now, level, category, message, source);
        appendEntry(entry);
    }

    private void appendEntry(LogEntry entry) {
        synchronized (entries) {
            entry=bounded142(entry);
            entries.add(entry);
            trimIfNeeded();
            dataRevision++;dirty=true;bumpVersion();
        }
    }

    public void clear() {
        ensureLoaded();
        if(!loadedFromDisk)return;
        synchronized(entries){entries.clear();dataRevision++;dirty=true;bumpVersion();}
        save();
    }

    // ============================================================
    //  Queries
    // ============================================================

    public List<LogEntry> snapshot() {
        synchronized (entries) { return new ArrayList<>(entries); }
    }

    public int size() {
        synchronized (entries) { return entries.size(); }
    }

    /**
     * 0.3.1: используем structured-поля — без повторного парсинга.
     */
    public int countVisible() {
        LogSettingsService svc = LogSettingsService.get();
        int count = 0;
        synchronized (entries) {
            for (LogEntry e : entries) {
                if (svc.shouldShow(e.level, e.category)) count++;
            }
        }
        return count;
    }

    // ============================================================
    //  Load
    // ============================================================

    public synchronized void ensureLoaded() {
        if (loadedFromDisk) return;
        loadedFromDisk = loadFromDisk();
    }

    private boolean loadFromDisk() {
        Path file = currentLogFile();
        if (!Files.exists(file)) return true;

        try {
            if(Files.size(file)>16L*1024*1024)throw new IOException("Log file exceeds 16 MiB");
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

            synchronized (entries) {
                var recent=new ArrayList<>(entries);entries.clear();
                int n = lines.size();
                for (int i = 0; i < n; i++) {
                    String line = lines.get(i);
                    if(line.startsWith("{140 ")){try{String json=line.substring(5);LogEntry entry=new com.google.gson.Gson().fromJson(json,LogEntry.class);if(entry==null||entry.text==null||entry.time==null||entry.level==null||entry.category==null||entry.message==null)throw new IllegalArgumentException();entries.add(bounded142(entry));continue;}catch(RuntimeException invalid){continue;}}
                    if (line.isEmpty() || line.charAt(0) != '[') continue;
                    int close = line.indexOf(']');
                    if (close <= 0) continue;
                    String time = line.substring(1, close);
                    String text = line.substring(close + 1).trim();
                    // Structured parsing выполняется в LogEntry ctor.
                    entries.add(new LogEntry(time, text));
                }
                entries.addAll(recent);trimIfNeeded();bumpVersion();
            }
            Mared.LOGGER.info("[Mared] Log restored from disk: {} entries",
                size());
            return true;
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to load current.log: {}",
                e.getMessage());
            return false;
        }
    }

    // ============================================================
    //  Save
    // ============================================================

    private static String cap142(String value,int maximum){
        if(value==null)return null;
        if(value.length()<=maximum)return value;
        int end=maximum-1;
        if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
        return value.substring(0,end)+"…";
    }
    private static LogEntry bounded142(LogEntry entry){
        if(entry.text.length()<=4096&&entry.message.length()<=4096&&entry.time.length()<=64
            &&entry.category.length()<=64&&(entry.source==null||entry.source.length()<=256))return entry;
        return new LogEntry(cap142(entry.time,64),entry.epochMillis,entry.level,cap142(entry.category,64),cap142(entry.message,4096),cap142(entry.source,256));
    }
    private static String diskLine142(com.google.gson.Gson gson,LogEntry entry){
        return "{140 "+gson.toJson(entry)+"\n";
    }
    public synchronized void save() {
        ensureLoaded();
        if(!loadedFromDisk||!dirty)return;
        long myRev=dataRevision;
        try {
            var retained=new java.util.ArrayDeque<String>();
            var gson=new com.google.gson.Gson();int bytes=0,omitted=0;
            for(LogEntry entry:snapshot()){
                String line=diskLine142(gson,bounded142(entry));
                int size=line.getBytes(StandardCharsets.UTF_8).length;
                while(!retained.isEmpty()&&bytes+size>DISK_BUDGET142-4096){
                    bytes-=retained.removeFirst().getBytes(StandardCharsets.UTF_8).length;omitted++;
                }
                retained.addLast(line);bytes+=size;
            }
            var text=new StringBuilder(Math.min(bytes+4096,DISK_BUDGET142));
            if(omitted>0)text.append(diskLine142(gson,new LogEntry("",System.currentTimeMillis(),LogSettings.Level.WARN,"other",omitted+" older log entries omitted by the 8 MiB disk budget",null)));
            for(String line:retained)text.append(line);
            com.fixmer.genesis.technology.storage.AtomicSettings111.write(currentLogFile(),text.toString());
            synchronized(entries){if(dataRevision==myRev)dirty=false;}
        } catch(IOException error){Mared.LOGGER.warn("[Mared] Failed to save current.log: {}",error.getMessage());}
    }

    // ============================================================
    //  Export
    // ============================================================

    public void export() { export(snapshot()); }

    public void export(List<LogEntry> entriesToExport) {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("mared").resolve("logs");
            Files.createDirectories(dir);
            String name = LocalDateTime.now().format(EXPORT_FMT) + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 8) + ".log";
            Path file = dir.resolve(name);

            List<LogEntry> all = List.copyOf(entriesToExport);
            StringBuilder sb = new StringBuilder(4096);
            sb.append("# Mared log export\n");
            sb.append("# Time: ").append(LocalDateTime.now()).append("\n");
            sb.append("# Lines: ").append(all.size()).append("\n");
            sb.append("# Levels: ");
            for (LogSettings.Level l : LogSettings.getEnabledLevels()) {
                sb.append(l.name()).append(' ');
            }
            sb.append("\n# Categories: ");
            for (String c : LogSettings.getEnabledCategories()) {
                sb.append(c).append(' ');
            }
            sb.append("\n\n");

            int n = all.size();
            for (int i = 0; i < n; i++) {
                LogEntry e = all.get(i);
                sb.append('[').append(e.time).append("] ")
                  .append(e.text).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW);

            addStructured(LogSettings.Level.INFO, "info",
                "log exported: " + file.getFileName()
                    + " (" + all.size() + " lines)");
        } catch (IOException e) {
            Mared.LOGGER.error("[Mared] Failed to export log", e);
            addStructured(LogSettings.Level.ERROR, "error",
                "export failed: " + e.getMessage());
        }
    }

    // ============================================================
    //  Internals
    // ============================================================

    private static Path currentLogFile() {
        return FMLPaths.CONFIGDIR.get()
            .resolve("mared").resolve("logs").resolve("current.log");
    }

    private void trimIfNeeded() {
        int size = entries.size();
        if (size <= MAX_ENTRIES) return;
        int toRemove = size - MAX_ENTRIES + TRIM_BATCH;
        if (toRemove > size) toRemove = size;
        entries.subList(0, toRemove).clear();
    }
}