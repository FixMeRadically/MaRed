package com.fixmer.mared.gui2.studio.events;

import com.fixmer.mared.commands.registry.MaredCommandRegistry;

/**
 * События Studio.
 *
 * Поток:
 *   Explorer  --FileSelectedEvent-->     Workspace (открывает файл)
 *   Explorer  --CommandSelectedEvent-->  Inspector (показывает описание)
 *   Explorer  --RequestNewFileEvent-->   Screen (открывает диалог)
 *   Explorer  --RequestRenameEvent-->    Screen
 *   Explorer  --RequestDuplicateEvent--> Screen
 *   Explorer  --RequestDeleteEvent-->    Screen
 *   *         --LogEvent-->              Console
 *   Workspace --FileOpenedEvent-->       TopBar (сброс dirty, показ имени)
 */
public final class StudioEvents {

    private StudioEvents() {}

    // ============================================================
    //  Файлы — пользовательские действия
    // ============================================================

    public record FileSelectedEvent(String fileName) implements StudioEvent {}

    /** Workspace реально загрузил файл (после FileSelectedEvent). */
    public record FileOpenedEvent(String fileName) implements StudioEvent {}

    public record SaveRequestedEvent() implements StudioEvent {}

    // ============================================================
    //  Команды
    // ============================================================

    public record CommandSelectedEvent(MaredCommandRegistry.CommandInfo info)
        implements StudioEvent {}

    // ============================================================
    //  Запросы на действия (Explorer → Screen)
    // ============================================================

    public record RequestNewFileEvent() implements StudioEvent {}

    public record RequestRenameFileEvent(String fileName) implements StudioEvent {}

    public record RequestDuplicateFileEvent(String fileName) implements StudioEvent {}

    public record RequestDeleteFileEvent(String fileName) implements StudioEvent {}

    public record RequestReloadPersistentEvent() implements StudioEvent {}

    // ============================================================
    //  Логи
    // ============================================================

    public record LogEvent(String line) implements StudioEvent {}
}