package com.fixmer.mared.gui2.studio.events;

import java.util.List;

import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry;
import com.fixmer.mared.services.logging.LogSettings;

/**
 * События Studio.
 *
 * 0.3.2:
 *   - RequestCloseDirtyDocumentEvent — Workspace просит Screen
 *     показать confirm-диалог перед закрытием грязного таба.
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
    //  Запросы
    // ============================================================

    public record RequestNewFileEvent() implements StudioEvent {}
    public record RequestReloadPersistentEvent() implements StudioEvent {}

    public record RequestContextMenuForFileEvent(int x, int y, String fileName)
        implements StudioEvent {}

    public record RequestContextMenuEvent(int x, int y,
                                          List<ContextMenuEntry> entries)
        implements StudioEvent {
        public RequestContextMenuEvent {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    /**
     * 0.3.2: грязный таб пытаются закрыть. Screen показывает
     * confirm-диалог Save / Discard / Cancel.
     */
    public record RequestCloseDirtyDocumentEvent(String documentTitle)
        implements StudioEvent {}

    // ============================================================
    //  Логи
    // ============================================================

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