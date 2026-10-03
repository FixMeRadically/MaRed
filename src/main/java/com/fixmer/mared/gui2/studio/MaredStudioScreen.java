package com.fixmer.mared.gui2.studio;

import com.fixmer.mared.gui2.docking.layout.DockLayoutCalculator;
import com.fixmer.mared.gui2.docking.render.DockRenderer;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay;
import com.fixmer.mared.gui2.framework.render.MaredAnimState;
import com.fixmer.mared.gui2.framework.render.MaredScale;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.studio.action.StudioActions;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 0.3.1:
 *   - Screen сам создаёт UiContext и делает MaredScale.bind в init,
 *     unbind в removed. Session принимает готовый UiContext.
 *   - removed() вызывает session.shutdown() только при isClosing();
 *     при переключении Studio → Settings сессия сохраняется и
 *     переиспользуется при возврате.
 */
public final class MaredStudioScreen extends Screen implements Disposable {

    private final StudioSession session = new StudioSession();
    private SubscriptionGroup screenSubs = new SubscriptionGroup();

    public MaredStudioScreen() {
        super(Component.literal("MaRed Studio"));
    }

    @Override
    public void dispose() { screenSubs.dispose(); }

    // ============================================================
    //  Lifecycle
    // ============================================================

    @Override
    protected void init() {
        screenSubs.dispose();
        screenSubs = new SubscriptionGroup();

        Minecraft mc = Minecraft.getInstance();
        int physW = mc.getWindow().getWidth();
        int physH = mc.getWindow().getHeight();
        int mcGuiScale = (int) mc.getWindow().getGuiScale();

        UiContext uiCtx = new UiContext(this.width, this.height,
            physW, physH, mcGuiScale, MaredThemeRegistry.active());
        MaredScale.bind(uiCtx);

        session.initialize(this, uiCtx);
        subscribeBusEvents();

        DockLayoutCalculator.calculate(
            session.controller().dockManager().layout(),
            this.width, this.height);
    }

    @Override
    public void removed() {
        screenSubs.dispose();

        // 0.3.1: shutdown — только при фактическом закрытии студии.
        // При переходе Studio → Settings сессия выживает и будет
        // переиспользована при возврате.
        if (session.isClosing()) {
            session.shutdown();
        }

        MaredScale.unbind();
    }

    // ============================================================
    //  Bus subscriptions
    // ============================================================

    private void subscribeBusEvents() {
        var bus = session.controller().bus();

        screenSubs.add(bus.subscribe(StudioEvents.RequestNewFileEvent.class,
            e -> session.executeAction(StudioActions.FILE_NEW)));
        screenSubs.add(bus.subscribe(StudioEvents.RequestReloadPersistentEvent.class,
            e -> session.executeAction(StudioActions.FILE_RELOAD_PERSISTENT)));

        screenSubs.add(bus.subscribe(StudioEvents.RequestRenameFileEvent.class,
            e -> onRenameFile(e.fileName())));
        screenSubs.add(bus.subscribe(StudioEvents.RequestDuplicateFileEvent.class,
            e -> onDuplicateFile(e.fileName())));
        screenSubs.add(bus.subscribe(StudioEvents.RequestDeleteFileEvent.class,
            e -> onDeleteFile(e.fileName())));

        screenSubs.add(bus.subscribe(StudioEvents.RequestContextMenuEvent.class,
            e -> openContextMenu(e.x(), e.y(), e.entries())));
    }

    // ============================================================
    //  Per-file actions
    // ============================================================

    private void onRenameFile(String oldName) {
        ScreenNavigator.openRenameDialog(this, oldName, (newName, persistent) -> {
            var r = session.commands().rename(oldName, newName);
            session.publish(r);
            if (r.ok()) {
                session.controller().bus().publish(
                    new StudioEvents.FileRenamedEvent(oldName, newName));
                session.refreshExplorer();
            }
        });
    }

    private void onDuplicateFile(String fileName) {
        var r = session.commands().duplicate(fileName);
        session.publish(r);
        if (r.ok()) session.refreshExplorer();
    }

    private void onDeleteFile(String fileName) {
        if (!session.commands().exists(fileName)) {
            session.log("[studio] delete skipped: file does not exist: " + fileName);
            session.refreshExplorer();
            return;
        }
        boolean persistent = session.commands().isPersistent(fileName);
        ScreenNavigator.openDeleteConfirm(this, fileName, persistent, () -> {
            var r = session.commands().delete(fileName);
            session.publish(r);
            if (r.ok()) {
                session.controller().bus().publish(
                    new StudioEvents.FileDeletedEvent(fileName));
                session.refreshExplorer();
            }
        });
    }

    private void openContextMenu(int x, int y,
                                 java.util.List<ContextMenuEntry> entries) {
        if (session.overlayManager() == null) return;
        session.overlayManager().push(new ContextMenuOverlay(x, y, entries));
    }

    // ============================================================
    //  Close guard
    // ============================================================

    @Override
    public void onClose() {
        if (session.hasUnsavedChanges()) {
            ScreenNavigator.openUnsavedConfirm(this, () -> {
                session.markClosing();
                session.overlayManager().clear();
                MaredStudioScreen.super.onClose();
            });
            return;
        }
        session.markClosing();
        if (session.overlayManager() != null) session.overlayManager().clear();
        super.onClose();
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        if (MaredScale.isBound()) MaredAnimState.tick();

        graphics.fill(0, 0, width, height, MaredThemeRegistry.active().bgScreen);
        super.render(graphics, mouseX, mouseY, partialTick);

        MaredRenderContext context = new MaredRenderContext(graphics);
        DockRenderer.beginFrame(context);
        try {
            DockRenderer.render(session.controller().dockManager(),
                context, width, height);
            DockRenderer.renderDividers(session.controller().dockManager(),
                context, width, height, mouseX, mouseY);
        } finally {
            DockRenderer.endFrame();
        }

        if (session.overlayManager() != null) {
            session.overlayManager().render(graphics, this.font,
                width, height, mouseX, mouseY);
        }
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (session.overlayManager() != null
            && session.overlayManager().mouseClicked(mx, my, button)) return true;

        if (super.mouseClicked(mx, my, button)) return true;
        if (DockRenderer.beginDividerDrag(session.controller().dockManager(),
            mx, my, width, height, button)) return true;
        return DockRenderer.dispatchClick(session.controller().dockManager(),
            mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (DockRenderer.isDraggingDivider()) {
            DockRenderer.endDividerDrag(session.controller().dockManager());
            return true;
        }
        var pm = session.pointerManager();
        if (pm != null && pm.isCapturedForButton(button)) {
            MaredComponent owner = pm.owner();
            boolean handled = owner != null && owner.mouseReleased(mx, my, button);
            pm.releaseAll();
            return handled;
        }
        if (super.mouseReleased(mx, my, button)) return true;
        return DockRenderer.dispatchReleased(session.controller().dockManager(),
            mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dragX, double dragY) {
        if (DockRenderer.isDraggingDivider()) {
            DockRenderer.updateDividerDrag(session.controller().dockManager(),
                mx, my, width, height);
            return true;
        }
        var pm = session.pointerManager();
        if (pm != null && pm.isCapturedForButton(button)) {
            MaredComponent owner = pm.owner();
            if (owner != null) {
                return owner.mouseDragged(mx, my, button, dragX, dragY);
            }
        }
        if (super.mouseDragged(mx, my, button, dragX, dragY)) return true;
        return DockRenderer.dispatchDrag(session.controller().dockManager(),
            mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double scrollX, double scrollY) {
        if (session.overlayManager() != null
            && session.overlayManager().shouldBlockGenericInput()) return true;
        if (super.mouseScrolled(mx, my, scrollX, scrollY)) return true;
        return DockRenderer.dispatchScroll(session.controller().dockManager(),
            mx, my, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx, my);
        DockRenderer.dispatchMove(session.controller().dockManager(), mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (session.overlayManager() != null
            && session.overlayManager().keyPressed(keyCode, scanCode, modifiers))
            return true;

        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (DockRenderer.dispatchKey(session.controller().dockManager(),
            keyCode, scanCode, modifiers)) return true;

        if (session.actionContext() != null
            && session.actions() != null
            && session.actions().executeByKey(keyCode, modifiers,
                session.actionContext())) {
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (session.overlayManager() != null
            && session.overlayManager().shouldBlockGenericInput()) return true;
        if (super.charTyped(codePoint, modifiers)) return true;
        return DockRenderer.dispatchChar(session.controller().dockManager(),
            codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}