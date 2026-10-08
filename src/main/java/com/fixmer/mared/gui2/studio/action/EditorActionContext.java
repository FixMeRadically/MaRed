package com.fixmer.mared.gui2.studio.action;

import com.fixmer.mared.gui2.framework.overlay.OverlayManager;
import com.fixmer.mared.gui2.studio.MaredStudioController;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.panels.explorer.ExplorerComponent;
import com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent;
import com.fixmer.mared.services.command.CommandFileService;

import net.minecraft.client.gui.screens.Screen;

/**
 * 0.3.2: overlays() — доступ к OverlayManager через action context.
 */
public final class EditorActionContext {

    private final Screen screen;
    private final com.fixmer.mared.technology.editor.EditorRuns runs = new com.fixmer.mared.technology.editor.EditorRuns();
    public com.fixmer.mared.technology.editor.EditorRuns runs() { return runs; }
    private final MaredStudioController controller;
    private final CommandFileService commandService;
    private final OverlayManager overlayManager;

    public EditorActionContext(Screen screen,
                               MaredStudioController controller,
                               CommandFileService commandService,
                               OverlayManager overlayManager) {
        this.screen = screen;
        this.controller = controller;
        this.commandService = commandService;
        this.overlayManager = overlayManager;
    }

    public Screen screen() { return screen; }
    public MaredStudioController controller() { return controller; }
    public CommandFileService commands() { return commandService; }
    public OverlayManager overlays() { return overlayManager; }

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