package com.fixmer.mared.gui2.studio.panels.topbar;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.gui2.framework.components.MaredPanel;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.render.Fonts;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class StudioTopBar extends MaredPanel implements Disposable {

    private static final int PAD = 4;
    private static final int BTN_PAD_X = 10;
    private static final int BTN_GAP = 4;

    private static final class Btn {
        final String label;
        final Runnable action;
        int x, y, w, h;
        boolean hover;
        Btn(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private final List<Btn> buttons = new ArrayList<>(6);
    private final SubscriptionGroup subs = new SubscriptionGroup();

    private String currentFile = null;
    private boolean dirty = false;

    private Runnable onNew, onSave, onRun, onSettings, onClose;

    public StudioTopBar(StudioEventBus bus) {
        buttons.add(new Btn("New",      () -> { if (onNew      != null) onNew.run(); }));
        buttons.add(new Btn("Save",     () -> { if (onSave     != null) onSave.run(); }));
        buttons.add(new Btn("Run",      () -> { if (onRun      != null) onRun.run(); }));
        buttons.add(new Btn("Reload",   () -> bus.publish(new StudioEvents.RequestReloadPersistentEvent())));
        buttons.add(new Btn("Settings", () -> { if (onSettings != null) onSettings.run(); }));
        buttons.add(new Btn("Close",    () -> { if (onClose    != null) onClose.run(); }));

        subs.add(bus.subscribe(StudioEvents.FileOpenedEvent.class, e -> {
            currentFile = e.fileName();
            dirty = false;
        }));
        subs.add(bus.subscribe(StudioEvents.LogEvent.class, e -> {
            if (e.line() != null && e.line().startsWith("[studio] saved:")) {
                dirty = false;
            }
        }));
    }

    @Override
    public void dispose() { subs.dispose(); }

    public void setActions(Runnable onNew, Runnable onSave, Runnable onRun,
                           Runnable onSettings, Runnable onClose) {
        this.onNew = onNew;
        this.onSave = onSave;
        this.onRun = onRun;
        this.onSettings = onSettings;
        this.onClose = onClose;
    }

    public void setDirty(boolean d) { this.dirty = d; }
    public String currentFile() { return currentFile; }

    @Override
    public void layout(com.fixmer.mared.gui2.framework.core.MaredBounds bounds) {
        super.layout(bounds);
        int x = bounds.x() + PAD;
        int y = bounds.y() + 3;
        int h = Math.max(10, bounds.height() - 6);
        for (Btn b : buttons) {
            int w = Fonts.width(b.label) + BTN_PAD_X * 2;
            b.x = x; b.y = y; b.w = w; b.h = h;
            x += w + BTN_GAP;
        }
    }

    @Override
    protected void safeRender(MaredRenderContext ctx) {
        super.safeRender(ctx);
        GuiGraphics g = ctx.graphics();
        Font font = ctx.font();

        for (Btn b : buttons) {
            int bg, border;
            if (b.hover) { bg = ThemeColors.hover(); border = ThemeColors.accent(); }
            else { bg = 0xFF252530; border = 0xFF3A3A4A; }
            g.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
            g.renderOutline(b.x, b.y, b.w, b.h, border);
            g.drawString(font, b.label,
                b.x + (b.w - font.width(b.label)) / 2,
                b.y + (b.h - 8) / 2,
                ThemeColors.text(), false);
        }

        String title = currentFile == null
            ? "MaRed Studio"
            : "MaRed Studio — " + currentFile + (dirty ? " ●" : "");

        int tw = font.width(title);
        g.drawString(font, title,
            bounds.right() - PAD - tw,
            bounds.y() + (bounds.height() - 8) / 2,
            currentFile == null ? ThemeColors.muted() : ThemeColors.text(),
            false);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        for (Btn b : buttons) b.hover = hit(b, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (button != 0) return true;
        for (Btn b : buttons) {
            if (hit(b, mx, my)) { b.action.run(); return true; }
        }
        return true;
    }

    private boolean hit(Btn b, double mx, double my) {
        return mx >= b.x && mx < b.x + b.w
            && my >= b.y && my < b.y + b.h;
    }
}