package com.fixmer.mared.gui2.studio.panels.topbar;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.components.MaredPanel;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredBounds;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.render.Fonts;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.action.EditorAction;
import com.fixmer.mared.gui2.studio.action.EditorActionContext;
import com.fixmer.mared.gui2.studio.action.EditorActionRegistry;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * TopBar Studio.
 *
 * 0.3.1:
 *   - Работает над EditorActionRegistry.
 *   - Обработчик LogEvent теперь понимает и structured, и legacy:
 *     dirty сбрасывается по message "saved: ..." или по text "[studio]
 *     saved: ..." — в зависимости от того, что пришло.
 */
public final class StudioTopBar extends MaredPanel implements Disposable {

    private static final int PAD = 4;
    private static final int BTN_PAD_X = 10;
    private static final int BTN_GAP = 4;

    private static final class Btn {
        final String actionId;
        final EditorAction action;
        int x, y, w, h;
        boolean hover;

        Btn(String actionId, EditorAction action) {
            this.actionId = actionId;
            this.action = action;
        }
    }

    private final List<Btn> buttons = new ArrayList<>(8);
    private final SubscriptionGroup subs = new SubscriptionGroup();

    private EditorActionRegistry registry;
    private EditorActionContext context;

    private String currentFile = null;
    private boolean dirty = false;

    public StudioTopBar(StudioEventBus bus) {
        subs.add(bus.subscribe(StudioEvents.FileOpenedEvent.class, e -> {
            currentFile = e.fileName();
            dirty = false;
        }));
        subs.add(bus.subscribe(StudioEvents.LogEvent.class, this::onLogEvent));
    }

    private void onLogEvent(StudioEvents.LogEvent e) {
        if (e == null) return;
        String msg = e.message();
        if (msg == null) return;
        // Structured path приходит как "saved: name", category=studio.
        if ("studio".equals(e.category()) && msg.startsWith("saved:")) {
            dirty = false;
            return;
        }
        // Legacy fallback — если кто-то ещё публикует строку.
        if (msg.startsWith("[studio] saved:")) {
            dirty = false;
        }
    }

    @Override
    public void dispose() { subs.dispose(); }

    public void bind(EditorActionRegistry registry,
                     EditorActionContext context,
                     List<String> actionIds) {
        this.registry = registry;
        this.context = context;
        rebuildButtons(actionIds);
    }

    private void rebuildButtons(List<String> actionIds) {
        buttons.clear();
        if (actionIds == null || registry == null) return;
        for (String id : actionIds) {
            EditorAction a = registry.byId(id);
            if (a == null) continue;
            buttons.add(new Btn(id, a));
        }
    }

    public void setDirty(boolean d) { this.dirty = d; }
    public String currentFile() { return currentFile; }

    @Override
    public void layout(MaredBounds bounds) {
        super.layout(bounds);
        int x = bounds.x() + PAD;
        int y = bounds.y() + 3;
        int h = Math.max(10, bounds.height() - 6);

        for (Btn b : buttons) {
            String label = MaredLang.get(b.action.titleKey());
            int w = Fonts.width(label) + BTN_PAD_X * 2;
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
            boolean enabled = context != null && b.action.isEnabled(context);

            int bg, border, textColor;
            if (!enabled) {
                bg = 0xFF1A1A22; border = 0xFF2A2A38; textColor = 0xFF555566;
            } else if (b.hover) {
                bg = ThemeColors.hover(); border = ThemeColors.accent();
                textColor = ThemeColors.text();
            } else {
                bg = 0xFF252530; border = 0xFF3A3A4A;
                textColor = ThemeColors.text();
            }

            String label = MaredLang.get(b.action.titleKey());

            g.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
            g.renderOutline(b.x, b.y, b.w, b.h, border);
            g.drawString(font, label,
                b.x + (b.w - font.width(label)) / 2,
                b.y + (b.h - 8) / 2, textColor, false);
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
        if (registry == null || context == null) return true;

        for (Btn b : buttons) {
            if (hit(b, mx, my)) {
                registry.execute(b.actionId, context);
                return true;
            }
        }
        return true;
    }

    private boolean hit(Btn b, double mx, double my) {
        return mx >= b.x && mx < b.x + b.w
            && my >= b.y && my < b.y + b.h;
    }
}