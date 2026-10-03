package com.fixmer.mared.services.command;

import java.util.HashSet;
import java.util.Set;

import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.events.MaredPersistentLoader;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.commands.storage.MaredPersistentStorage;

/**
 * Сервис работы с файлами команд.
 *
 * 0.3.2 (audit #58):
 *   duplicate() удаляет первую строку "#persistent" из копии.
 *   Раньше копия сохраняла маркер, но не регистрировалась в
 *   persistent.txt — получался «призрачный» persistent: в файле
 *   маркер есть, но обработчики не загружаются. Если пользователь
 *   потом случайно делал copy → persistent через UI, registry мог
 *   получить дубликаты on/every-обработчиков.
 *
 *   Продуктовое решение: копия — обычный файл. Пользователь сам
 *   решает, делать ли её persistent.
 */
public final class CommandFileService {

    /** Результат операции для UI-feedback. */
    public record Result(boolean ok, String logLine) {}

    public CommandFileService() {}

    // ============================================================
    //  Create
    // ============================================================

    public Result create(String name, boolean persistent) {
        String code = MaredCommandStorage.validateName(name);
        if (code != null) {
            return new Result(false,
                "[studio] invalid name '" + name + "': " + code);
        }
        if (!MaredCommandStorage.createCommand(name)) {
            return new Result(false, "[studio] FAILED to create: " + name);
        }
        if (persistent) {
            markPersistent(name);
            return new Result(true, "[studio] created persistent: " + name);
        }
        return new Result(true, "[studio] created: " + name);
    }

    // ============================================================
    //  Delete
    // ============================================================

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

    // ============================================================
    //  Rename
    // ============================================================

    public Result rename(String oldName, String newName) {
        String code = MaredCommandStorage.validateName(newName);
        if (code != null) {
            return new Result(false,
                "[studio] invalid name '" + newName + "': " + code);
        }
        if (oldName != null && oldName.equals(newName)) {
            return new Result(false,
                "[studio] rename skipped: source and target are equal: " + oldName);
        }

        boolean wasPersistent = MaredPersistentStorage.isPersistent(oldName);

        if (!MaredCommandStorage.rename(oldName, newName)) {
            return new Result(false,
                "[studio] FAILED to rename: " + oldName + " → " + newName);
        }

        if (wasPersistent) {
            MaredPersistentStorage.remove(oldName);
            MaredPersistentStorage.add(newName);

            MaredEventRegistry.clearAllPersistent();
            MaredPersistentLoader.reset();
            MaredPersistentLoader.loadAll();

            return new Result(true,
                "[studio] renamed (persistent moved): "
                    + oldName + " → " + newName);
        }
        return new Result(true,
            "[studio] renamed: " + oldName + " → " + newName);
    }

    // ============================================================
    //  Duplicate
    // ============================================================

    public Result duplicate(String sourceName) {
        String code = MaredCommandStorage.validateName(sourceName);
        if (code != null) {
            return new Result(false,
                "[studio] invalid source name '" + sourceName + "': " + code);
        }

        if (!MaredCommandStorage.exists(sourceName)) {
            return new Result(false,
                "[studio] FAILED to duplicate: source missing: " + sourceName);
        }

        String copyName = suggestDuplicateName(sourceName);
        String content = MaredCommandStorage.readCommand(sourceName);
        if (content == null) content = "";

        // 0.3.2 (audit #58): копия НЕ сохраняет #persistent-маркер.
        content = stripPersistentMarker(content);

        if (!MaredCommandStorage.createCommand(copyName)) {
            return new Result(false,
                "[studio] FAILED to duplicate (create step): " + sourceName);
        }

        if (!MaredCommandStorage.writeCommand(copyName, content)) {
            MaredCommandStorage.deleteCommand(copyName);
            return new Result(false,
                "[studio] FAILED to write duplicate: " + copyName
                    + " (rollback done)");
        }

        return new Result(true,
            "[studio] duplicated: " + sourceName + " → " + copyName);
    }

    /**
     * Убирает первую строку "#persistent", если она есть.
     * Возвращает остальное содержимое без изменений.
     */
    private static String stripPersistentMarker(String content) {
        if (content == null || content.isEmpty()) return "";
        if (!content.startsWith("#persistent")) return content;
        int nl = content.indexOf('\n');
        if (nl < 0) return "";
        return content.substring(nl + 1);
    }

    // ============================================================
    //  Read / Write
    // ============================================================

    public String read(String name) {
        if (name == null || name.isEmpty()) return null;
        return MaredCommandStorage.readCommand(name);
    }

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
    //  Helpers
    // ============================================================

    public boolean exists(String name) {
        return MaredCommandStorage.exists(name);
    }

    public boolean isPersistent(String name) {
        return MaredPersistentStorage.isPersistent(name);
    }

    public String suggestDuplicateName(String sourceName) {
        Set<String> existing =
            new HashSet<>(MaredCommandStorage.listCommands());

        String candidate = sourceName + "_copy";
        int n = 1;
        while (existing.contains(candidate)) {
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