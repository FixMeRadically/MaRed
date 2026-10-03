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
            entries.add(entry);
            trimIfNeeded();
        }
        dataRevision++;
        dirty = true;
        bumpVersion();
    }

    public void clear() {
        synchronized (entries) { entries.clear(); }
        dataRevision++;
        dirty = true;
        bumpVersion();
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

    public void ensureLoaded() {
        if (loadedFromDisk) return;
        loadFromDisk();
        loadedFromDisk = true;
    }

    private void loadFromDisk() {
        synchronized (entries) {
            if (!entries.isEmpty()) {
                Mared.LOGGER.info(
                    "[Mared] Log already has {} entries, skip disk restore",
                    entries.size());
                return;
            }
        }

        Path file = currentLogFile();
        if (!Files.exists(file)) return;

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

            synchronized (entries) {
                if (!entries.isEmpty()) return;

                int n = lines.size();
                for (int i = 0; i < n; i++) {
                    String line = lines.get(i);
                    if (line.isEmpty() || line.charAt(0) != '[') continue;
                    int close = line.indexOf(']');
                    if (close <= 0) continue;
                    String time = line.substring(1, close);
                    String text = line.substring(close + 1).trim();
                    // Structured parsing выполняется в LogEntry ctor.
                    entries.add(new LogEntry(time, text));
                }
                trimIfNeeded();
            }

            bumpVersion();
            Mared.LOGGER.info("[Mared] Log restored from disk: {} entries",
                size());
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to load current.log: {}",
                e.getMessage());
        }
    }

    // ============================================================
    //  Save
    // ============================================================

    public void save() {
        if (!dirty) return;

        long myRev = dataRevision;
        Path file = currentLogFile();

        try {
            Files.createDirectories(file.getParent());

            List<LogEntry> copy;
            synchronized (entries) { copy = new ArrayList<>(entries); }

            StringBuilder sb = new StringBuilder(4096);
            int n = copy.size();
            for (int i = 0; i < n; i++) {
                LogEntry e = copy.get(i);
                sb.append('[').append(e.time).append("] ")
                  .append(e.text).append('\n');
            }

            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);

            if (dataRevision == myRev) {
                dirty = false;
            }
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to save current.log: {}",
                e.getMessage());
        }
    }

    // ============================================================
    //  Export
    // ============================================================

    public void export() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("mared").resolve("logs");
            Files.createDirectories(dir);
            String name = LocalDateTime.now().format(EXPORT_FMT) + ".log";
            Path file = dir.resolve(name);

            List<LogEntry> all = snapshot();
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
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);

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