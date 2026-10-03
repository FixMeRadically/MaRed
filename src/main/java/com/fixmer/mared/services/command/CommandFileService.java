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
 * 0.3.0 (Stage 2): вынесен из MaredStudioScreen.
 * 0.3.0 (Phase F4): переехал из gui2.studio.services в services.command.
 *
 * 0.3.1:
 *   - create/rename/duplicate валидируют имя через
 *     MaredCommandStorage.validateName() (единый контракт с Storage).
 *   - rename() переносит persistent-флаг. Раньше GUI rename мог
 *     сломать persistent-контракт: файл физически переименовывался,
 *     но persistent.txt продолжал указывать на старое имя, а
 *     зарегистрированные обработчики — на удалённый файл.
 *   - duplicate() откатывает placeholder при провале записи. Раньше
 *     оставался мусорный file_copy с шаблонным текстом.
 *   - suggestDuplicateName() делает один listing и дальше работает
 *     in-memory. Раньше цикл с listCommands() внутри делал N обходов
 *     ФС на N копий.
 *   - rename(a, a) отклоняется — раньше молча «успех» без изменений.
 *   - duplicate() валидирует sourceName до чтения.
 *
 * Сервис не знает ни о UI, ни о шине событий.
 * Возвращает Result — вызывающий сам публикует LogEvent и делает
 * refreshExplorer.
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
    //  Rename   ← P0-6
    // ============================================================

    /**
     * 0.3.1 (P0-6): переносит persistent-флаг.
     *
     * Сценарий, который раньше ломался:
     *   1. Пользователь создал persistent-файл "test" (в persistent.txt).
     *   2. Explorer: Rename test → hello.
     *   3. CommandFileService.rename вызывал MaredCommandStorage.rename,
     *      который физически переименовывал файл.
     *   4. persistent.txt продолжал указывать на "test".
     *   5. MaredPersistentLoader.loadAll() на следующем старте пытался
     *      читать несуществующий test.txt, ругался, и persistent-обработчики
     *      терялись.
     *
     * Фикс: если oldName был persistent — переносим запись в
     * persistent.txt и перезагружаем обработчики. Слушатели
     * перестают ссылаться на удалённый файл.
     */
    public Result rename(String oldName, String newName) {
        String code = MaredCommandStorage.validateName(newName);
        if (code != null) {
            return new Result(false,
                "[studio] invalid name '" + newName + "': " + code);
        }

        // 0.3.1: rename на себя — не no-op, а явная ошибка.
        // Иначе получим "успех" без изменения файла и потенциальный
        // FileRenamedEvent(old==new) в Screen.
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
            // Переносим флаг. Порядок: сначала remove старого, потом add
            // нового — чтобы transient-состояние не содержало оба.
            MaredPersistentStorage.remove(oldName);
            MaredPersistentStorage.add(newName);

            // Перезагружаем обработчики с чистого листа. Все ранее
            // зарегистрированные on/every/after из этого файла
            // указывали на старый контекст — теперь они будут
            // загружены из нового имени.
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
    //  Duplicate   ← P0-7
    // ============================================================

    /**
     * 0.3.1 (P0-7): копирование с откатом.
     *
     * Сценарий, который раньше ломался:
     *   1. Explorer: Duplicate "src".
     *   2. createCommand("src_copy") создаёт файл с шаблонным
     *      комментарием (// Write Minecraft commands here...).
     *   3. writeCommand("src_copy", content) падает — например,
     *      нет прав на запись / диск полон.
     *   4. Метод возвращает failure, но placeholder-файл с шаблоном
     *      остаётся на диске.
     *
     * Фикс: при провале writeCommand — deleteCommand(copyName).
     *
     * Плюс: явная валидация sourceName. Раньше невалидное имя
     * (со слешем, слишком длинное) отсекалось только в exists(),
     * но suggestion строился от него.
     */
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

        if (!MaredCommandStorage.createCommand(copyName)) {
            return new Result(false,
                "[studio] FAILED to duplicate (create step): " + sourceName);
        }

        if (!MaredCommandStorage.writeCommand(copyName, content)) {
            // 0.3.1: rollback. Если этого не сделать — в списке
            // остаётся placeholder с шаблонным текстом, который
            // пользователь увидит как "успешно созданную копию".
            MaredCommandStorage.deleteCommand(copyName);
            return new Result(false,
                "[studio] FAILED to write duplicate: " + copyName
                    + " (rollback done)");
        }

        return new Result(true,
            "[studio] duplicated: " + sourceName + " → " + copyName);
    }

    // ============================================================
    //  Read / Write
    // ============================================================

    /**
     * Прочитать содержимое файла команды.
     * Возвращает null, если имя невалидно или файл недоступен.
     */
    public String read(String name) {
        if (name == null || name.isEmpty()) return null;
        return MaredCommandStorage.readCommand(name);
    }

    /**
     * Записать содержимое файла команды.
     * Атомарная запись (см. MaredCommandStorage.writeCommand).
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

    /**
     * 0.3.1: один listing ФС, дальше in-memory.
     *
     * Раньше:
     *   while (MaredCommandStorage.listCommands().contains(candidate)) {
     *       candidate = sourceName + "_copy" + n++;
     *   }
     * На 1000 существующих копий — 1000 directory listing'ов.
     *
     * Теперь: один снимок в Set, дальше цикл в памяти.
     */
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

    /**
     * Пометить файл как persistent.
     * - добавляет #persistent в начало содержимого, если его нет;
     * - регистрирует имя в persistent.txt.
     */
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