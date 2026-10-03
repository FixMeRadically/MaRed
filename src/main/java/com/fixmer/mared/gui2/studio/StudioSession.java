package com.fixmer.mared.gui2.studio;

import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.FocusManager;
import com.fixmer.mared.gui2.framework.core.PointerCaptureManager;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.overlay.OverlayManager;
import com.fixmer.mared.gui2.framework.overlay.ToastOverlay;
import com.fixmer.mared.gui2.studio.action.EditorActionContext;
import com.fixmer.mared.gui2.studio.action.EditorActionRegistry;
import com.fixmer.mared.gui2.studio.action.StudioActions;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.services.command.CommandFileService;
import com.fixmer.mared.services.logging.LogSettings;

import net.minecraft.client.gui.screens.Screen;

/**
 * Состояние одного экземпляра Studio.
 *
 * 0.3.1:
 *   - Session больше не владеет MaredScale.bind/unbind. Это делает
 *     Screen в init()/removed(). Причина: Minecraft.setScreen()
 *     всегда вызывает removed() у старого экрана, даже когда это
 *     временный переход Studio → Settings. Если Session управляет
 *     bind'ом, Settings остаётся без контекста и падает на MaredUi.px().
 *   - Session принимает готовый UiContext.
 *   - shutdown() вызывается Screen'ом только при isClosing()==true.
 */
public final class StudioSession implements Disposable {

    private final MaredStudioController controller = new MaredStudioController();
    private final CommandFileService commandService = new CommandFileService();

    private FocusManager focusManager;
    private PointerCaptureManager pointerManager;
    private OverlayManager overlayManager;
    private ToastOverlay toastOverlay;
    private EditorActionRegistry actionRegistry;
    private EditorActionContext actionContext;

    private Screen screen;
    private UiContext uiContext;
    private boolean initialized;
    private boolean closing;

    /**
     * @param screen    родительский Screen
     * @param uiContext уже привязанный к MaredScale контекст
     */
    public void initialize(Screen screen, UiContext uiContext) {
        if (initialized) {
            // Повторный init того же Screen (возврат из Settings) —
            // обновляем только ссылки, UiContext уже пересоздан Screen'ом.
            this.screen = screen;
            this.uiContext = uiContext;
            return;
        }

        this.screen = screen;
        this.uiContext = uiContext;
        this.closing = false;

        focusManager = new FocusManager();
        pointerManager = new PointerCaptureManager();
        overlayManager = new OverlayManager();

        toastOverlay = new ToastOverlay();
        overlayManager.push(toastOverlay);

        controller.initialize(focusManager, pointerManager);

        actionRegistry = new EditorActionRegistry();
        StudioActions.registerAll(actionRegistry);
        actionContext = new EditorActionContext(screen, controller, commandService);

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

    /**
     * Полное разрушение сессии.
     * Вызывается ТОЛЬКО когда Screen реально закрывается
     * (isClosing()==true), а не при временном переключении на
     * другой экран.
     */
    public void shutdown() {
        if (!initialized) return;

        if (focusManager != null) focusManager.clear();
        if (pointerManager != null) pointerManager.releaseAll();
        if (overlayManager != null) overlayManager.clear();
        if (actionRegistry != null) actionRegistry.clear();

        controller.shutdown();

        focusManager = null;
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
        if (actionRegistry == null || actionContext == null) return false;
        return actionRegistry.execute(actionId, actionContext);
    }

    public boolean hasUnsavedChanges() {
        var ws = controller.workspacePanel();
        if (ws == null) return false;
        var wc = ws.workspaceComponent();
        return wc != null
            && wc.currentFileName() != null
            && wc.isDirty();
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