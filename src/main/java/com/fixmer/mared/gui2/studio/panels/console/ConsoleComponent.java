package com.fixmer.mared.gui2.studio.panels.console;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.components.console.MaredLogPanel;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.core.NarratableComponent;
import com.fixmer.mared.gui2.framework.render.legacy.LegacyFontBridge;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;
import com.fixmer.mared.services.logging.LogSettings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Консоль MaRed Studio.
 *
 * 0.3.1: подписка на LogEvent принимает structured-событие.
 * 0.3.2 (accessibility):
 *   - Реализует NarratableComponent — FocusTraversal озвучивает
 *     число записей и видимых по фильтру.
 */
public final class ConsoleComponent extends MaredComponent
        implements Disposable, NarratableComponent {

    private final MaredLogPanel logPanel;
    private final SubscriptionGroup subs = new SubscriptionGroup();

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    public ConsoleComponent(StudioEventBus bus) {
        this.logPanel = new MaredLogPanel();
        logPanel.addStructured(LogSettings.Level.INFO, "studio",
            "console ready");
        subs.add(bus.subscribe(StudioEvents.LogEvent.class,
            e -> logPanel.add(e)));
    }

    @Override
    public void dispose() { subs.dispose(); }

    public MaredLogPanel logPanel() { return logPanel; }

    public void addLine(String line) { logPanel.add(line); }

    public void addStructured(LogSettings.Level level, String category,
                              String message) {
        logPanel.addStructured(level, category, message);
    }

    @Override
    public boolean focusable() { return true; }

    @Override
    protected void safeRender(MaredRenderContext context) {
        logPanel.tickAutoScroll();
        GuiGraphics g = context.graphics();
        Font font = context.font();
        logPanel.render(g, font,
            bounds.x(), bounds.y(), bounds.width(), bounds.height(),
            lastMouseX, lastMouseY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return;
        lastMouseX = (int) mouseX;
        lastMouseY = (int) mouseY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        requestFocus();
        capturePointer(button);
        Font font = LegacyFontBridge.font();
        return logPanel.mouseClicked(
            mx, my, button,
            bounds.x(), bounds.y(), bounds.width(), bounds.height(),
            font
        );
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dragX, double dragY) {
        Font font = LegacyFontBridge.font();
        return logPanel.mouseDragged(
            mx, my, button, dragX, dragY,
            bounds.x(), bounds.y(), bounds.width(), bounds.height(),
            font
        );
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = logPanel.mouseReleased(mx, my, button);
        if (hasPointerCapture(button)) releasePointer();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double scrollX, double scrollY) {
        if (!bounds.contains(mx, my)) return false;
        return logPanel.mouseScrolled(
            mx, my, scrollY,
            bounds.x(), bounds.y(), bounds.width(), bounds.height()
        );
    }

    // ============================================================
    //  Narration (0.3.2 accessibility)
    // ============================================================

    @Override
    public Component narrationText() {
        int visible = logPanel.getVisibleEntries().size();
        int total = logPanel.snapshot().size();
        return Component.literal(MaredLang.format(
            "mared.narration.console.summary", total, visible));
    }
}