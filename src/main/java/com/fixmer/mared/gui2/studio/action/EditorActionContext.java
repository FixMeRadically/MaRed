package com.fixmer.mared.gui2.studio.action;

import com.fixmer.mared.gui2.studio.MaredStudioController;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.panels.explorer.ExplorerComponent;
import com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent;
import com.fixmer.mared.services.command.CommandFileService;

import net.minecraft.client.gui.screens.Screen;

/**
 * Контекст выполнения action'а.
 *
 * 0.3.1:
 *   - log(String) использует LogEvent.legacy — переходный путь для
 *     call-sites, которые ещё публикуют готовые строки.
 *   - log(Level, category, message) — новый structured-путь.
 */
public final class EditorActionContext {

    private final Screen screen;
    private final MaredStudioController controller;
    private final CommandFileService commandService;

    public EditorActionContext(Screen screen,
                               MaredStudioController controller,
                               CommandFileService commandService) {
        this.screen = screen;
        this.controller = controller;
        this.commandService = commandService;
    }

    public Screen screen() { return screen; }
    public MaredStudioController controller() { return controller; }
    public CommandFileService commands() { return commandService; }

    public WorkspaceComponent workspace() {
        var ws = controller.workspacePanel();
        return ws == null ? null : ws.workspaceComponent();
    }

    public ExplorerComponent explorer() {
        var ex = controller.explorerPanel();
        return ex == null ? null : ex.explorerComponent();
    }

    public void log(String line) {
        controller.bus().publish(StudioEvents.LogEvent.legacy(line));
    }

    public void log(com.fixmer.mared.services.logging.LogSettings.Level level,
                    String category, String message) {
        controller.bus().publish(new StudioEvents.LogEvent(level, category, message));
    }

    public void publish(CommandFileService.Result r) {
        if (r != null && r.logLine() != null) log(r.logLine());
    }

    public void refreshExplorer() {
        ExplorerComponent ex = explorer();
        if (ex != null) ex.reload();
    }
}