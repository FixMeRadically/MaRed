package com.fixmer.mared.commands.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Хранилище списка persistent-скриптов.
 *
 * 0.3.2 (audit #88):
 *   save() использует atomic write через .tmp + ATOMIC_MOVE.
 *   Раньше Files.write(...) без .tmp — при краше JVM файл мог
 *   остаться частично записанным, и persistent-скрипты терялись.
 *
 * Кэш: список держится в памяти, инвалидируется при add/remove/save.
 */
public final class MaredPersistentStorage {

    private MaredPersistentStorage() {}

    private static volatile List<String> cached = null;
    private static final String TMP_SUFFIX = ".tmp";

    private static Path listFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("persistent.txt");
    }

    public static synchronized List<String> load() {
        if (cached != null) return new ArrayList<>(cached);

        List<String> result = new ArrayList<>();
        Path file = listFile();
        if (!Files.exists(file)) {
            cached = result;
            return new ArrayList<>(result);
        }

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                result.add(s);
            }
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to read persistent.txt: {}",
                e.getMessage());
        }

        cached = result;
        return new ArrayList<>(result);
    }

    public static synchronized void save(List<String> names) {
        Path file = listFile();
        try {
            Files.createDirectories(file.getParent());

            // 0.3.2: atomic write — .tmp + move.
            Path tmp = file.resolveSibling(
                file.getFileName().toString() + TMP_SUFFIX);
            Files.write(tmp, names, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            cached = new ArrayList<>(names);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to write persistent.txt: {}",
                e.getMessage());
        }
    }

    public static synchronized void add(String name) {
        if (name == null || name.isEmpty()) return;
        List<String> list = load();
        if (!list.contains(name)) {
            list.add(name);
            save(list);
        }
    }

    public static synchronized void remove(String name) {
        if (name == null) return;
        List<String> list = load();
        if (list.remove(name)) {
            save(list);
        }
    }

    public static synchronized boolean isPersistent(String name) {
        if (name == null) return false;
        return load().contains(name);
    }

    public static synchronized void invalidateCache() {
        cached = null;
    }
}