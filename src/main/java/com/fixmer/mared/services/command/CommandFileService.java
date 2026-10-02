package com.fixmer.mared.services.command;

import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.events.MaredPersistentLoader;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.commands.storage.MaredPersistentStorage;

/**
 * Сервис работы с файлами команд.
 *
 * 0.3.0 (Stage 2): вынесен из MaredStudioScreen.
 * 0.3.0 (Phase F4): переехал из gui2.studio.services в services.command.
 * Причина: UI-слой не должен напрямую ходить в MaredCommandStorage —
 * это граница storage/service. Плюс добавлены read()/write(), которые
 * раньше WorkspaceComponent звал напрямую.
 *
 * Сервис не знает ни о UI, ни о шине событий. Возвращает Result —
 * вызывающий решает, что делать (лог + refresh).
 */
public final class CommandFileService {

    /** Результат операции для UI-feedback. */
    public record Result(boolean ok, String logLine) {}

    public CommandFileService() {}

    // ============================================================
    //  Files
    // ============================================================

    public Result create(String name, boolean persistent) {
        if (!MaredCommandStorage.createCommand(name)) {
            return new Result(false, "[studio] FAILED to create: " + name);
        }
        if (persistent) {
            markPersistent(name);
            return new Result(true, "[studio] created persistent: " + name);
        }
        return new Result(true, "[studio] created: " + name);
    }

    public Result delete(String name) {
        if (!MaredCommandStorage.exists(name)) {
            return new Result(false,
                "[studio] delete skipped: file does not exist: " + name);
        }

        boolean wasPersistent = MaredPersistentStorage.isPersistent(name);

        boolean ok = MaredCommandStorage.deleteCommand(name);
        if (!ok) {
            return new Result(false, "[studio] FAILED to delete: " + name);
        }

        if (wasPersistent) {
            MaredPersistentStorage.remove(name);
            MaredEventRegistry.clearAllPersistent();
            MaredPersistentLoader.reset();
            MaredPersistentLoader.loadAll();
            return new Result(true,
                "[studio] deleted (persistent cleaned): " + name);
        }
        return new Result(true, "[studio] deleted: " + name);
    }

    public Result rename(String oldName, String newName) {
        if (!MaredCommandStorage.rename(oldName, newName)) {
            return new Result(false,
                "[studio] FAILED to rename: " + oldName + " → " + newName);
        }
        return new Result(true,
            "[studio] renamed: " + oldName + " → " + newName);
    }

    public Result duplicate(String sourceName) {
        String copyName = suggestDuplicateName(sourceName);
        String content = MaredCommandStorage.readCommand(sourceName);
        if (content == null) content = "";

        if (!MaredCommandStorage.createCommand(copyName)) {
            return new Result(false,
                "[studio] FAILED to duplicate: " + sourceName);
        }
        if (!MaredCommandStorage.writeCommand(copyName, content)) {
            return new Result(false,
                "[studio] FAILED to write duplicate: " + copyName);
        }
        return new Result(true,
            "[studio] duplicated: " + sourceName + " → " + copyName);
    }

    // ============================================================
    //  Read / Write (Phase F4)
    // ============================================================

    /**
     * Прочитать содержимое файла команды.
     * Возвращает null, если файл не существует или недоступен.
     * Безопасен для вызова из main thread.
     */
    public String read(String name) {
        if (name == null || name.isEmpty()) return null;
        return MaredCommandStorage.readCommand(name);
    }

    /**
     * Записать содержимое файла команды.
     * Возвращает true при успехе. Атомарная запись (см. storage).
     * Безопасен для вызова из worker thread.
     */
    public boolean write(String name, String content) {
        if (name == null || name.isEmpty()) return false;
        return MaredCommandStorage.writeCommand(name, content);
    }

    // ============================================================
    //  Persistent
    // ============================================================

    public Result reloadPersistent() {
        MaredEventRegistry.clearAll();
        MaredEventRegistry.clearAllPersistent();
        MaredPersistentStorage.invalidateCache();
        MaredPersistentLoader.reset();
        MaredPersistentLoader.loadAll();
        return new Result(true, "[studio] persistent scripts reloaded");
    }

    // ============================================================
    //  Helpers для Screen
    // ============================================================

    public boolean exists(String name) {
        return MaredCommandStorage.exists(name);
    }

    public boolean isPersistent(String name) {
        return MaredPersistentStorage.isPersistent(name);
    }

    /** Имя для копии, не конфликтующее с существующими. */
    public String suggestDuplicateName(String sourceName) {
        String candidate = sourceName + "_copy";
        int n = 1;
        while (MaredCommandStorage.listCommands().contains(candidate)) {
            candidate = sourceName + "_copy" + n++;
        }
        return candidate;
    }

    // ============================================================
    //  Внутреннее
    // ============================================================

    private void markPersistent(String name) {
        String content = MaredCommandStorage.readCommand(name);
        if (content == null) content = "";
        if (!content.startsWith("#persistent")) {
            content = "#persistent\n" + content;
            MaredCommandStorage.writeCommand(name, content);
        }
        MaredPersistentStorage.add(name);
    }
}