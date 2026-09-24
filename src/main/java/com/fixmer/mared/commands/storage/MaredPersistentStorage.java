package com.fixmer.mared.commands.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Хранилище списка persistent-скриптов.
 *
 * Формат — обычный .txt, одна строка = одно имя файла (без расширения).
 * Файл: .minecraft/config/mared/persistent.txt
 *
 * Кэш: список держится в памяти, инвалидируется при add/remove/save.
 * isPersistent(name) работает за O(n) по кэшу, без I/O.
 */
public final class MaredPersistentStorage {

    private MaredPersistentStorage() {}

    /** Кэш списка. null = не загружен. */
    private static volatile List<String> cached = null;

    private static Path listFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("persistent.txt");
    }

    /** Прочитать список persistent-файлов. Возвращает копию кэша. */
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
            Mared.LOGGER.warn("[Mared] Failed to read persistent.txt: {}", e.getMessage());
        }

        cached = result;
        return new ArrayList<>(result);
    }

    /** Записать список. Кэш инвалидируется. */
    public static synchronized void save(List<String> names) {
        Path file = listFile();
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, names, StandardCharsets.UTF_8);
            cached = new ArrayList<>(names);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to write persistent.txt: {}", e.getMessage());
        }
    }

    /** Добавить имя в список (если нет). */
    public static synchronized void add(String name) {
        if (name == null || name.isEmpty()) return;
        List<String> list = load();
        if (!list.contains(name)) {
            list.add(name);
            save(list);
        }
    }

    /** Удалить имя из списка. */
    public static synchronized void remove(String name) {
        if (name == null) return;
        List<String> list = load();
        if (list.remove(name)) {
            save(list);
        }
    }

    /** Проверить, в списке ли файл. Быстро — из кэша. */
    public static synchronized boolean isPersistent(String name) {
        if (name == null) return false;
        return load().contains(name);
    }

    /** Сбросить кэш — на случай ручной правки файла. */
    public static synchronized void invalidateCache() {
        cached = null;
    }
}