package com.fixmer.mared.gui2.studio;

import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.FocusManager;
import com.fixmer.mared.gui2.framework.core.FocusTraversal;
import com.fixmer.mared.gui2.framework.core.PointerCaptureManager;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.overlay.OverlayManager;
import com.fixmer.mared.gui2.framework.overlay.ToastOverlay;
import com.fixmer.mared.gui2.studio.action.EditorActionArgs;
import com.fixmer.mared.gui2.studio.action.EditorActionContext;
import com.fixmer.mared.gui2.studio.action.EditorActionRegistry;
import com.fixmer.mared.gui2.studio.action.StudioActions;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.plugin.PluginRegistry;
import com.fixmer.mared.services.command.CommandFileService;
import com.fixmer.mared.services.logging.LogSettings;

import net.minecraft.client.gui.screens.Screen;

public final class StudioSession implements Disposable {

    private final MaredStudioController controller = new MaredStudioController();
    private final CommandFileService commandService = new CommandFileService();

    private FocusManager focusManager;
    private FocusTraversal focusTraversal;
    private PointerCaptureManager pointerManager;
    private OverlayManager overlayManager;
    private ToastOverlay toastOverlay;
    private EditorActionRegistry actionRegistry;
    private EditorActionContext actionContext;

    private Screen screen;
    private UiContext uiContext;
    private com.fixmer.mared.technology.editor.EditorWorkbench workbench;
    public com.fixmer.mared.technology.editor.EditorWorkbench workbench(){
        if(!initialized)throw new IllegalStateException("Studio not initialized");
        if(workbench==null)workbench=new com.fixmer.mared.technology.editor.EditorWorkbench(this);
        return workbench;
    }

    private boolean initialized;
    private boolean closing;

    public void initialize(Screen screen, UiContext uiContext) {
        if (initialized) {
            this.screen = screen;
            this.uiContext = uiContext;
            return;
        }

        this.screen = screen;
        this.uiContext = uiContext;
        this.closing = false;

        focusManager = new FocusManager();
        focusTraversal = new FocusTraversal();
        focusTraversal.setFocusManager(focusManager);
        pointerManager = new PointerCaptureManager();
        overlayManager = new OverlayManager();

        toastOverlay = new ToastOverlay();
        overlayManager.push(toastOverlay);

        controller.initialize(focusManager, pointerManager, focusTraversal);

        actionRegistry = new EditorActionRegistry();
        StudioActions.registerAll(actionRegistry);

        // 0.3.2: применить плагинные actions к свежему реестру.
        PluginRegistry.applyPendingActions(actionRegistry);

        controller.setActionRegistry(actionRegistry);

        actionContext = new EditorActionContext(screen, controller,
            commandService, overlayManager);

        var workspace = actionContext.workspace();
        if (workspace != null) workspace.setSaveAsHandler(document ->
            ScreenNavigator.openSaveAsDialog(overlayManager, document.storageId,
                name -> workspace.saveAs(document, name, this::refreshExplorer),
                () -> document.closeAfterSaveAs = false));

        var topBar = controller.topBarPanel();
        if (topBar != null) {
            topBar.topBar().bind(actionRegistry, actionContext,
                StudioActions.TOP_BAR_ORDER);
        } else {
            com.fixmer.mared.Mared.LOGGER.warn(
                "[studio] topbar missing — TopBar actions not bound");
        }

        initialized = true;
        controller.bus().publish(new StudioEvents.LogEvent(
            LogSettings.Level.INFO, "studio", "session started", null));
    }

    public void shutdown() {
        if (!initialized) return;

        if (focusManager != null) focusManager.clear();
        if (focusTraversal != null) focusTraversal.clear();
        if (pointerManager != null) pointerManager.releaseAll();
        if (overlayManager != null) overlayManager.clear();
        if (actionRegistry != null) actionRegistry.clear();

        if(workbench!=null){workbench.dispose();workbench=null;}
        if (actionContext != null) actionContext.runs().close();
        controller.shutdown();

        focusManager = null;
        focusTraversal = null;
        pointerManager = null;
        overlayManager = null;
        toastOverlay = null;
        actionRegistry = null;
        actionContext = null;
        uiContext = null;

        initialized = false;
    }

    @Override
    public void dispose() { shutdown(); }

    public MaredStudioController controller() { return controller; }
    public CommandFileService commands() { return commandService; }
    public FocusManager focusManager() { return focusManager; }
    public FocusTraversal focusTraversal() { return focusTraversal; }
    public PointerCaptureManager pointerManager() { return pointerManager; }
    public OverlayManager overlayManager() { return overlayManager; }
    public ToastOverlay toast() { return toastOverlay; }
    public EditorActionRegistry actions() { return actionRegistry; }
    public EditorActionContext actionContext() { return actionContext; }
    public Screen screen() { return screen; }
    public UiContext uiContext() { return uiContext; }

    public boolean isInitialized() { return initialized; }
    public boolean isClosing() { return closing; }

    public void markClosing() { this.closing = true; }

    public boolean executeAction(String actionId) {
        return executeAction(actionId, EditorActionArgs.EMPTY);
    }

    public boolean executeAction(String actionId, EditorActionArgs args) {
        if (actionRegistry == null || actionContext == null) return false;
        return actionRegistry.execute(actionId, actionContext,
            args == null ? EditorActionArgs.EMPTY : args);
    }

    public boolean hasUnsavedChanges() {
        var ws = controller.workspacePanel();
        if (ws == null) return false;
        var wc = ws.workspaceComponent();
        return wc != null && wc.hasAnyDirty();
    }

    public void refreshExplorer() {
        var ex = controller.explorerPanel();
        if (ex != null) ex.explorerComponent().reload();
    }

    public void log(String line) {
        controller.bus().publish(StudioEvents.LogEvent.legacy(line));
    }

    public void log(LogSettings.Level level, String category, String message) {
        controller.bus().publish(new StudioEvents.LogEvent(level, category, message));
    }

    public void publish(CommandFileService.Result r) {
        if (r == null || r.logLine() == null) return;
        log(r.logLine());
    }
}