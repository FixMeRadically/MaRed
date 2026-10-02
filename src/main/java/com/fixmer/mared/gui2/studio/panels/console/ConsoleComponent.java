package com.fixmer.mared.gui2.studio.panels.console;

import com.fixmer.mared.gui2.framework.components.console.MaredLogPanel;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.render.legacy.LegacyFontBridge;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Консоль MaRed Studio.
 *
 * 0.3.0 (Stage B3b3): MaredLogPanel переехал из gui.common в
 * gui2.framework.components.console. ConsoleComponent теперь работает
 * с gui2-версией — legacy больше не задействован.
 *
 * Шрифт для рендера — через context.font().
 * Шрифт для legacy-совместимых mouseClicked/Dragged — через
 * LegacyFontBridge (единственное легальное место, знающее про Minecraft).
 */
public final class ConsoleComponent extends MaredComponent implements Disposable {

    private final MaredLogPanel logPanel;
    private final SubscriptionGroup subs = new SubscriptionGroup();

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    public ConsoleComponent(StudioEventBus bus) {
        this.logPanel = new MaredLogPanel();
        logPanel.add("[studio] console ready");
        subs.add(bus.subscribe(StudioEvents.LogEvent.class,
            e -> logPanel.add(e.line())));
    }

    @Override
    public void dispose() { subs.dispose(); }

    public MaredLogPanel logPanel() { return logPanel; }
    public void addLine(String line) { logPanel.add(line); }

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
        return logPanel.mouseReleased(mx, my, button);
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
}