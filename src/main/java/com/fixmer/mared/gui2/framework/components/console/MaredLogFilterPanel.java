package com.fixmer.mared.gui2.framework.components.console;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.MaredUi;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.services.logging.LogEntry;
import com.fixmer.mared.services.logging.LogSettings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Панель фильтров лога (автономный компонент).
 *
 * 0.3.0 (Phase B3b3): перенос legacy gui.common.MaredLogFilterPanel в gui2.
 * 0.3.0 (Phase F3): LogEntry / LogSettings теперь из services.logging.
 */
public final class MaredLogFilterPanel {

    private int scroll = 0;
    private int contentHeight = 0;

    private static final class ChipHit {
        final int x, y, w, h;
        final Runnable onClick;
        ChipHit(int x, int y, int w, int h, Runnable onClick) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.onClick = onClick;
        }
    }

    private final List<ChipHit> hits = new ArrayList<>(32);

    public MaredLogFilterPanel() {}

    public static int countByLevel(LogSettings.Level lvl) {
        List<LogEntry> snap = MaredLogPanel.snapshotForStats();
        int n = 0;
        for (LogEntry e : snap) {
            if (LogSettings.parseLevel(e.text) == lvl) n++;
        }
        return n;
    }

    public static int countByCategory(String cat) {
        List<LogEntry> snap = MaredLogPanel.snapshotForStats();
        int n = 0;
        for (LogEntry e : snap) {
            if (LogSettings.parseCategory(e.text).equals(cat)) n++;
        }
        return n;
    }

    public static int totalEntries() { return MaredLogPanel.snapshotForStats().size(); }
    public static int visibleEntries() { return MaredLogPanel.countVisible(); }

    // ============================================================
    //  Render
    // ============================================================

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       int mouseX, int mouseY) {
        hits.clear();

        MaredTheme t = MaredUi.theme();

        MaredUi.rect(g, x, y, x + w, y + h, t.bgSunken);

        int innerPad = MaredUi.px(8);
        int innerX = x + innerPad;
        int innerW = w - innerPad * 2 - MaredUi.pxScrollbarW();

        int cy = y + 6 - scroll;
        MaredUi.text(g, font, MaredLang.get("mared.log.filter.title"),
            innerX, cy, t.accent);
        cy += 14;

        String total = MaredLang.format("mared.log.filter.total",
            totalEntries(), visibleEntries());
        MaredUi.text(g, font, total, innerX, cy, t.textDim);
        cy += 16;

        cy = drawPresets(g, font, innerX, cy, innerW, mouseX, mouseY);
        cy += 8;

        MaredUi.text(g, font, MaredLang.get("mared.log.filter.levels"),
            innerX, cy, t.warn);
        cy += 14;

        int chipH = 16;
        for (LogSettings.Level lvl : LogSettings.Level.values()) {
            boolean enabled = LogSettings.isLevelEnabled(lvl);
            int count = countByLevel(lvl);
            String label = lvl.name() + "  " + count;
            int textW = font.width(label);
            int chipW = textW + 18;

            boolean hov = MaredUi.hovered(mouseX, mouseY, innerX, cy, chipW, chipH);
            drawChip(g, font, innerX, cy, chipW, chipH, label, enabled,
                levelColor(lvl), hov);

            final LogSettings.Level l = lvl;
            hits.add(new ChipHit(innerX, cy, chipW, chipH,
                () -> LogSettings.toggleLevel(l)));
            cy += chipH + 4;
        }
        cy += 10;

        MaredUi.text(g, font, MaredLang.get("mared.log.filter.categories"),
            innerX, cy, t.warn);
        cy += 14;

        int colX = innerX;
        int colY = cy;
        int colW = innerW;
        int usedW = 0;
        int chipGap = 4;

        for (String cat : LogSettings.CATEGORIES) {
            boolean enabled = LogSettings.isCategoryEnabled(cat);
            int count = countByCategory(cat);
            String label = "[" + cat + "]  " + count;
            int textW = font.width(label);
            int chipW = textW + 18;

            if (usedW + chipW > colW) {
                colY += chipH + chipGap;
                usedW = 0;
            }

            int cx = colX + usedW;
            boolean hov = MaredUi.hovered(mouseX, mouseY, cx, colY, chipW, chipH);
            drawChip(g, font, cx, colY, chipW, chipH, label, enabled,
                t.accent, hov);

            final String c = cat;
            hits.add(new ChipHit(cx, colY, chipW, chipH,
                () -> LogSettings.toggleCategory(c)));
            usedW += chipW + chipGap;
        }

        cy = colY + chipH + 10;
        contentHeight = cy - (y - scroll) + 10;

        int maxScroll = Math.max(0, contentHeight - h);
        if (maxScroll > 0) {
            int trackX = x + w - MaredUi.pxScrollbarW() - 2;
            MaredUi.rect(g, trackX, y, trackX + MaredUi.pxScrollbarW(), y + h,
                t.scrollTrack);
            int thumbH = Math.max(10, h * h / contentHeight);
            int thumbY = y + (h - thumbH) * scroll / maxScroll;
            MaredUi.rect(g, trackX, thumbY,
                trackX + MaredUi.pxScrollbarW(), thumbY + thumbH,
                t.scrollThumb);
        }
    }

    private int drawPresets(GuiGraphics g, Font font, int x, int y, int w,
                            int mouseX, int mouseY) {
        int btnH = 14;
        int gap = 4;
        int cy = y;

        int usedW = 0;
        usedW = drawButton(g, font, x + usedW, cy,
            MaredLang.get("mared.log.filter.preset.all"), mouseX, mouseY,
            () -> {
                for (LogSettings.Level l : LogSettings.Level.values()) {
                    if (!LogSettings.isLevelEnabled(l))
                        LogSettings.toggleLevel(l);
                }
                for (String c : LogSettings.CATEGORIES) {
                    if (!LogSettings.isCategoryEnabled(c))
                        LogSettings.toggleCategory(c);
                }
            });
        usedW += gap;
        usedW = drawButton(g, font, x + usedW, cy,
            MaredLang.get("mared.log.filter.preset.none"), mouseX, mouseY,
            () -> {
                for (LogSettings.Level l : LogSettings.Level.values()) {
                    if (LogSettings.isLevelEnabled(l))
                        LogSettings.toggleLevel(l);
                }
                for (String c : LogSettings.CATEGORIES) {
                    if (LogSettings.isCategoryEnabled(c))
                        LogSettings.toggleCategory(c);
                }
            });
        usedW += gap;
        usedW = drawButton(g, font, x + usedW, cy,
            MaredLang.get("mared.log.filter.preset.invert"), mouseX, mouseY,
            () -> {
                for (LogSettings.Level l : LogSettings.Level.values())
                    LogSettings.toggleLevel(l);
                for (String c : LogSettings.CATEGORIES)
                    LogSettings.toggleCategory(c);
            });
        cy += btnH + gap;

        usedW = 0;
        usedW = drawButton(g, font, x + usedW, cy,
            MaredLang.get("mared.log.filter.preset.errors"), mouseX, mouseY,
            () -> {
                for (LogSettings.Level l : LogSettings.Level.values()) {
                    boolean want = (l == LogSettings.Level.ERROR);
                    if (LogSettings.isLevelEnabled(l) != want)
                        LogSettings.toggleLevel(l);
                }
            });
        usedW += gap;
        usedW = drawButton(g, font, x + usedW, cy,
            MaredLang.get("mared.log.filter.preset.info_plus"), mouseX, mouseY,
            () -> {
                for (LogSettings.Level l : LogSettings.Level.values()) {
                    boolean want = (l == LogSettings.Level.INFO
                        || l == LogSettings.Level.WARN
                        || l == LogSettings.Level.ERROR);
                    if (LogSettings.isLevelEnabled(l) != want)
                        LogSettings.toggleLevel(l);
                }
            });
        usedW += gap;
        usedW = drawButton(g, font, x + usedW, cy,
            MaredLang.get("mared.log.filter.preset.all_plus"), mouseX, mouseY,
            () -> {
                for (LogSettings.Level l : LogSettings.Level.values()) {
                    if (!LogSettings.isLevelEnabled(l))
                        LogSettings.toggleLevel(l);
                }
            });

        return cy + btnH;
    }

    private int drawButton(GuiGraphics g, Font font, int x, int y, String label,
                           int mouseX, int mouseY, Runnable onClick) {
        MaredTheme t = MaredUi.theme();
        int textW = font.width(label);
        int w = textW + 14;
        int h = 14;
        boolean hov = MaredUi.hovered(mouseX, mouseY, x, y, w, h);

        int bg = hov ? t.bgHover : t.bgPanelRaised;
        MaredUi.button3D(g, font, x, y, w, h, label, bg, t.accent, t.text, hov);

        hits.add(new ChipHit(x, y, w, h, onClick));
        return w;
    }

    private void drawChip(GuiGraphics g, Font font, int x, int y, int w, int h,
                          String label, boolean enabled, int accent, boolean hov) {
        MaredTheme t = MaredUi.theme();
        int bg;
        int border;
        int text;

        if (enabled) {
            bg = MaredUi.darken(accent, 0.65f);
            border = accent;
            text = t.text;
        } else {
            bg = hov ? t.bgHover : t.bgPanel;
            border = t.border;
            text = t.textFaint;
        }

        MaredUi.rect(g, x, y, x + w, y + h, bg);
        MaredUi.outline(g, x, y, w, h, border);

        int iconX = x + 6;
        int iconY = y + h / 2 - 3;
        if (enabled) {
            MaredUi.rect(g, iconX, iconY + 2, iconX + 2, iconY + 4, accent);
            MaredUi.rect(g, iconX + 2, iconY + 3, iconX + 4, iconY + 5, accent);
            MaredUi.rect(g, iconX + 4, iconY - 1, iconX + 6, iconY + 3, accent);
        }

        MaredUi.text(g, font, label, x + 18, y + (h - 8) / 2, text);
    }

    private static int levelColor(LogSettings.Level lvl) {
        return switch (lvl) {
            case TRACE -> 0xFF888888;
            case DEBUG -> 0xFF88DDFF;
            case INFO  -> 0xFF55FF88;
            case WARN  -> 0xFFFFAA00;
            case ERROR -> 0xFFFF5555;
        };
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (ChipHit h : hits) {
            if (MaredUi.hovered(mx, my, h.x, h.y, h.w, h.h)) {
                h.onClick.run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double dy,
                                 int x, int y, int w, int h) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int maxScroll = Math.max(0, contentHeight - h);
        if (dy < 0) scroll = Math.min(maxScroll, scroll + 15);
        else if (dy > 0) scroll = Math.max(0, scroll - 15);
        return true;
    }
}