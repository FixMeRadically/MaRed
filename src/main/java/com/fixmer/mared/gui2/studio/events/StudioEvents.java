package com.fixmer.mared.gui2.studio.events;

import java.util.List;

import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry;
import com.fixmer.mared.services.logging.LogSettings;

/**
 * События Studio.
 *
 * 0.3.1:
 *   - LogEvent стал структурированным: level, category, message.
 *     Публикующие могут указать level/category напрямую, а не
 *     полагаться на парсинг "[error]" из строки.
 *   - LogEvent.legacy(String) — переходный путь: парсит строку.
 *     Существующие call-sites продолжают работать.
 */
public final class StudioEvents {

    private StudioEvents() {}

    // ============================================================
    //  Файлы
    // ============================================================

    public record FileSelectedEvent(String fileName) implements StudioEvent {}
    public record FileOpenedEvent(String fileName) implements StudioEvent {}
    public record FileRenamedEvent(String oldName, String newName) implements StudioEvent {}
    public record FileDeletedEvent(String fileName) implements StudioEvent {}
    public record SaveRequestedEvent() implements StudioEvent {}

    // ============================================================
    //  Команды
    // ============================================================

    public record CommandSelectedEvent(MaredCommandRegistry.CommandInfo info)
        implements StudioEvent {}

    // ============================================================
    //  Запросы на действия
    // ============================================================

    public record RequestNewFileEvent() implements StudioEvent {}
    public record RequestRenameFileEvent(String fileName) implements StudioEvent {}
    public record RequestDuplicateFileEvent(String fileName) implements StudioEvent {}
    public record RequestDeleteFileEvent(String fileName) implements StudioEvent {}
    public record RequestReloadPersistentEvent() implements StudioEvent {}

    public record RequestContextMenuEvent(int x, int y,
                                          List<ContextMenuEntry> entries)
        implements StudioEvent {
        public RequestContextMenuEvent {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    // ============================================================
    //  Логи
    // ============================================================

    /**
     * 0.3.1: структурированное событие лога.
     *
     * Публикующие:
     *   bus.publish(new LogEvent(Level.ERROR, "studio",
     *       "save failed: " + name))
     *
     * Screen перенаправляет в MaredLogPanel.addStructured(...).
     */
    public record LogEvent(LogSettings.Level level,
                           String category,
                           String message,
                           String source)
        implements StudioEvent {

        public LogEvent {
            if (level == null) level = LogSettings.Level.INFO;
            if (category == null) category = "other";
            if (message == null) message = "";
        }

        public LogEvent(LogSettings.Level level, String category, String message) {
            this(level, category, message, null);
        }

        /**
         * Переходный конструктор: строка парсится так же, как раньше
         * в MaredLogPanel.add(String).
         */
        public static LogEvent legacy(String line) {
            if (line == null) line = "";
            return new LogEvent(
                LogSettings.parseLevel(line),
                LogSettings.parseCategory(line),
                line,
                null
            );
        }
    }
}