package com.fixmer.mared.gui2.studio;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.docking.layout.DockLayoutCalculator;
import com.fixmer.mared.gui2.docking.render.DockRenderer;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.overlay.ConfirmDialogOverlay;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuEntry;
import com.fixmer.mared.gui2.framework.overlay.ContextMenuOverlay;
import com.fixmer.mared.gui2.framework.render.animation.MaredAnimState;
import com.fixmer.mared.gui2.framework.render.MaredScale;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.studio.action.EditorAction;
import com.fixmer.mared.gui2.studio.action.EditorActionArgs;
import com.fixmer.mared.gui2.studio.action.StudioActions;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;
import com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 0.3.2:
 *   - Tab / Shift+Tab — focus traversal между focusable-компонентами.
 *     Порядок — в FocusTraversal, регистрация — в controller.
 *   - Narration: FocusTraversal.narrate() озвучивает новую цель, если
 *     она реализует NarratableComponent.
 */
public final class MaredStudioScreen extends Screen implements Disposable {

    private final StudioSession session = new StudioSession();
    private SubscriptionGroup screenSubs = new SubscriptionGroup();

    public MaredStudioScreen() {
        super(Component.literal("MaRed Studio"));
    }

    @Override
    public void dispose() { screenSubs.dispose(); }

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
        if (session.isClosing()) session.shutdown();
        MaredScale.unbind();
    }

    private void subscribeBusEvents() {
        var bus = session.controller().bus();

        screenSubs.add(bus.subscribe(StudioEvents.RequestNewFileEvent.class,
            e -> session.executeAction(StudioActions.FILE_NEW)));
        screenSubs.add(bus.subscribe(StudioEvents.RequestReloadPersistentEvent.class,
            e -> session.executeAction(StudioActions.FILE_RELOAD_PERSISTENT)));

        screenSubs.add(bus.subscribe(StudioEvents.RequestContextMenuForFileEvent.class,
            e -> showFileContextMenu(e.x(), e.y(), e.fileName())));
        screenSubs.add(bus.subscribe(StudioEvents.RequestContextMenuEvent.class,
            e -> showRawContextMenu(e.x(), e.y(), e.entries())));

        screenSubs.add(bus.subscribe(StudioEvents.RequestCloseDirtyDocumentEvent.class,
            e -> showCloseDirtyConfirm(e.documentTitle())));
    }

    private void showFileContextMenu(int x, int y, String fileName) {
        if (session.overlayManager() == null) return;
        if (session.actions() == null || session.actionContext() == null) return;

        EditorActionArgs args = EditorActionArgs.of("fileName", fileName);
        List<ContextMenuEntry> items = new ArrayList<>();

        for (String id : StudioActions.FILE_CONTEXT_MENU) {
            if (StudioActions.isSeparator(id)) {
                items.add(ContextMenuEntry.divider());
                continue;
            }
            EditorAction a = session.actions().byId(id);
            if (a == null) continue;
            if (!a.isEnabled(session.actionContext(), args)) continue;
            final String actionId = id;
            items.add(ContextMenuEntry.of(
                MaredLang.get(a.titleKey()),
                () -> session.executeAction(actionId, args)
            ));
        }
        stripRedundantDividers(items);
        if (items.isEmpty()) return;
        session.overlayManager().push(new ContextMenuOverlay(x, y, items).category(com.fixmer.mared.technology.editor.GenesisEditorVisuals.LOGIC));
    }

    private void showRawContextMenu(int x, int y, List<ContextMenuEntry> entries) {
        if (session.overlayManager() == null) return;
        if (entries == null || entries.isEmpty()) return;
        session.overlayManager().push(new ContextMenuOverlay(x, y, entries).category(com.fixmer.mared.technology.editor.GenesisEditorVisuals.LOGIC));
    }

    private static void stripRedundantDividers(List<ContextMenuEntry> items) {
        while (!items.isEmpty() && items.get(0).separator()) items.remove(0);
        while (!items.isEmpty() && items.get(items.size() - 1).separator()) {
            items.remove(items.size() - 1);
        }
        for (int i = items.size() - 1; i > 0; i--) {
            if (items.get(i).separator() && items.get(i - 1).separator()) {
                items.remove(i);
            }
        }
    }

    private void showCloseDirtyConfirm(String title) {
        if (session.overlayManager() == null) return;
        WorkspaceComponent wc = workspace();
        if (wc == null) return;

        session.overlayManager().push(new ConfirmDialogOverlay(
            MaredLang.get("mared.dialog.unsaved_title"),
            MaredLang.format("mared.dialog.unsaved_message_named", title),
            () -> wc.confirmPendingClose(WorkspaceComponent.CloseAction.DISCARD),
            true, com.fixmer.mared.technology.editor.GenesisEditorVisuals.LOGIC, true, "mared.dialog.confirm"
        ));
    }

    @Override
    public void onClose() {
        WorkspaceComponent wc = workspace();
        if (wc != null && wc.hasAnyDirty()) {
            ScreenNavigator.openUnsavedConfirm(session.overlayManager(), () -> {
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

    private WorkspaceComponent workspace() {
        var ws = session.controller().workspacePanel();
        return ws == null ? null : ws.workspaceComponent();
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
            && session.overlayManager().mouseScrolled(mx, my, scrollX, scrollY))
            return true;
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
        // 1. Overlay первым.
        if (session.overlayManager() != null
            && session.overlayManager().keyPressed(keyCode, scanCode, modifiers))
            return true;

        // 2. Vanilla.
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        // 3. Панели — focused-обработчики.
        if (DockRenderer.dispatchKey(session.controller().dockManager(),
            keyCode, scanCode, modifiers)) return true;

        // 4. Tab / Shift+Tab — focus traversal.
        //    Ctrl+Tab уже обрабатывается Workspace'ом (переключение табов).
        if (keyCode == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (session.focusTraversal() != null
                && session.focusTraversal().handleTab(shift ? -1 : +1)) {
                return true;
            }
        }

        // 5. Actions.
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
            && session.overlayManager().charTyped(codePoint, modifiers))
            return true;

        if (session.overlayManager() != null
            && session.overlayManager().shouldBlockGenericInput()) return true;

        if (super.charTyped(codePoint, modifiers)) return true;
        return DockRenderer.dispatchChar(session.controller().dockManager(),
            codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}