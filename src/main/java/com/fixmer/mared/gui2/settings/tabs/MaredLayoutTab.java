package com.fixmer.mared.gui2.settings.tabs;

import java.util.function.IntConsumer;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredLayoutPreset;
import com.fixmer.mared.gui2.framework.render.MaredUi;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;
import com.fixmer.mared.services.settings.LayoutConstraints;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B5): перенос legacy gui.settings.tabs.MaredLayoutTab.
 * 0.3.0 (Phase E2a): работает через ctx.layout().
 * 0.3.0 (Phase F3a): Reset сбрасывает только layout.
 * 0.3.0 (Phase F3b): presetBtnW из presets.length, button guard.
 * 0.3.0 (fix): убран двойной пересчёт scroll при hit-test — координаты
 * берутся из render() без дополнительного сдвига.
 */
public final class MaredLayoutTab implements MaredSettingsTab {

    private static final int ROW_H = 18;
    private static final int CB_SZ = 14;

    private int scroll = 0;
    private int contentHeight = 0;

    private int presetY0;
    private int sidebarToggleY;
    private int rightPanelToggleY;
    private int consoleToggleY;
    private int statusBarToggleY;
    private int toolbarToggleY;
    private int splitToggleY;
    private int sidebarSliderY;
    private int rightSliderY;
    private int consoleSliderY;
    private int resetBtnY;

    @Override public String id() { return "layout"; }
    @Override public String displayName() {
        return MaredLang.get("mared.settings.tab.layout");
    }
    @Override public int accentColor() { return 0xFF55AAFF; }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       SettingsContext ctx, int mouseX, int mouseY) {
        var lo = ctx.layout();
        contentHeight = computeHeight();

        g.enableScissor(x, y, x + w, y + h);

        int cy = y - scroll;
        int cx = x;
        int maxW = w - MaredUi.px(8);

        MaredUi.text(g, font, MaredLang.get("mared.settings.layout.preset"),
            cx, cy, 0xFFFFAA00);
        cy += 14;
        presetY0 = cy;

        MaredLayoutPreset cur = lo.preset();
        MaredLayoutPreset[] presets = lo.allPresets();

        int presetGap = MaredUi.px(8);
        int presetBtnW = (maxW - presetGap * (presets.length - 1)) / presets.length;
        int presetH = MaredUi.px(46);

        for (int i = 0; i < presets.length; i++) {
            MaredLayoutPreset p = presets[i];
            int bx = cx + i * (presetBtnW + presetGap);
            boolean active = cur == p;
            boolean hov = MaredUi.hovered(mouseX, mouseY, bx, cy,
                presetBtnW, presetH);

            int bg = active ? 0xFF3355AA
                : (hov ? 0xFF23232E : 0xFF14141C);
            int border = active ? 0xFF55AAFF
                : (hov ? 0xFF5555AA : 0xFF333344);

            MaredUi.rect(g, bx, cy, bx + presetBtnW, cy + presetH, bg);
            MaredUi.outline(g, bx, cy, presetBtnW, presetH, border);

            MaredUi.text(g, font, p.displayName, bx + 6, cy + 6,
                active ? 0xFFFFFFFF : 0xFFAAAAAA);

            int descY = cy + 20;
            StringBuilder line = new StringBuilder();
            for (String word : p.description.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (font.width(test) > presetBtnW - 12) {
                    MaredUi.text(g, font, line.toString(), bx + 6, descY,
                        0xFF888888);
                    descY += 10;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            if (line.length() > 0) {
                MaredUi.text(g, font, line.toString(), bx + 6, descY,
                    0xFF888888);
            }
        }
        cy += presetH + MaredUi.px(16);

        MaredUi.text(g, font, MaredLang.get("mared.settings.layout.panels"),
            cx, cy, 0xFFFFAA00);
        cy += 16;

        sidebarToggleY = cy;
        cy = drawCheckbox(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.panels.sidebar"),
            lo.showSidebar());

        rightPanelToggleY = cy;
        cy = drawCheckbox(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.panels.right"),
            lo.showRightPanel());

        consoleToggleY = cy;
        cy = drawCheckbox(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.panels.console"),
            lo.showConsole());

        statusBarToggleY = cy;
        cy = drawCheckbox(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.panels.status"),
            lo.showStatusBar());

        toolbarToggleY = cy;
        cy = drawCheckbox(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.panels.toolbar"),
            lo.showToolbar());

        cy += MaredUi.px(8);

        MaredUi.text(g, font, MaredLang.get("mared.settings.layout.split"),
            cx, cy, 0xFFFFAA00);
        cy += 16;
        splitToggleY = cy;
        cy = drawCheckbox(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.split.enabled"),
            lo.splitEnabled());
        cy += MaredUi.px(8);

        MaredUi.text(g, font, MaredLang.get("mared.settings.layout.sizes"),
            cx, cy, 0xFFFFAA00);
        cy += 16;

        sidebarSliderY = cy;
        cy = drawSlider(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.sizes.sidebar"),
            lo.sidebarWidth(),
            LayoutConstraints.SIDEBAR_MIN, LayoutConstraints.SIDEBAR_MAX);

        rightSliderY = cy;
        cy = drawSlider(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.sizes.right"),
            lo.rightPanelWidth(),
            LayoutConstraints.RIGHT_PANEL_MIN, LayoutConstraints.RIGHT_PANEL_MAX);

        consoleSliderY = cy;
        cy = drawSlider(g, font, cx, cy, maxW,
            MaredLang.get("mared.settings.layout.sizes.console"),
            lo.consoleHeight(),
            LayoutConstraints.CONSOLE_MIN, LayoutConstraints.CONSOLE_MAX);

        cy += MaredUi.px(12);

        resetBtnY = cy;
        boolean resetHover = MaredUi.hovered(mouseX, mouseY, cx, cy,
            MaredUi.px(160), MaredUi.px(20));
        MaredUi.button3D(g, font, cx, cy, MaredUi.px(160), MaredUi.px(20),
            MaredLang.get("mared.settings.layout.reset"),
            resetHover ? 0xFF663333 : 0xFF3A2020,
            0xFFFF5555, 0xFFFFFFFF, resetHover);
        cy += MaredUi.px(24);

        contentHeight = cy - (y - scroll);

        g.disableScissor();
    }

    private int drawCheckbox(GuiGraphics g, Font font, int x, int y, int maxW,
                             String label, boolean checked) {
        MaredUi.drawCheckbox(g, x, y + 2, CB_SZ, checked, 0xFF4A4A4A, 0xFF55AAFF);
        MaredUi.text(g, font, label, x + CB_SZ + 6, y + 5,
            checked ? 0xFFFFFFFF : 0xFFAAAAAA);
        return y + ROW_H;
    }

    private int drawSlider(GuiGraphics g, Font font, int x, int y, int maxW,
                           String label, int value, int min, int max) {
        MaredUi.text(g, font, label + "   " + value, x, y, 0xFFAAAAAA);
        y += 12;

        int sliderW = MaredUi.px(220);
        float t = (value - min) / (float) (max - min);
        t = Math.max(0f, Math.min(1f, t));
        MaredUi.slider(g, x, y + 2, sliderW, 10, t, 0xFF333344, 0xFF55AAFF);
        return y + ROW_H + 6;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button,
                                int x, int y, int w, int h,
                                SettingsContext ctx) {
        if (button != 0) return false;
        if (my < y || my >= y + h) return false;
        if (mx < x || mx >= x + w) return false;

        var lo = ctx.layout();
        int maxW = w - MaredUi.px(8);

        MaredLayoutPreset[] presets = lo.allPresets();
        int presetGap = MaredUi.px(8);
        int presetBtnW = (maxW - presetGap * (presets.length - 1)) / presets.length;
        int presetH = MaredUi.px(46);

        for (int i = 0; i < presets.length; i++) {
            int bx = x + i * (presetBtnW + presetGap);
            if (MaredUi.hovered(mx, my, bx, presetY0, presetBtnW, presetH)) {
                ctx.applyLayoutPreset(presets[i]);
                return true;
            }
        }

        if (clickCheckbox(mx, my, x, sidebarToggleY, maxW)) {
            lo.setShowSidebar(!lo.showSidebar()); return true;
        }
        if (clickCheckbox(mx, my, x, rightPanelToggleY, maxW)) {
            lo.setShowRightPanel(!lo.showRightPanel()); return true;
        }
        if (clickCheckbox(mx, my, x, consoleToggleY, maxW)) {
            lo.setShowConsole(!lo.showConsole()); return true;
        }
        if (clickCheckbox(mx, my, x, statusBarToggleY, maxW)) {
            lo.setShowStatusBar(!lo.showStatusBar()); return true;
        }
        if (clickCheckbox(mx, my, x, toolbarToggleY, maxW)) {
            lo.setShowToolbar(!lo.showToolbar()); return true;
        }
        if (clickCheckbox(mx, my, x, splitToggleY, maxW)) {
            lo.setSplitEnabled(!lo.splitEnabled()); return true;
        }

        if (clickSlider(mx, my, x, sidebarSliderY, 220,
            LayoutConstraints.SIDEBAR_MIN, LayoutConstraints.SIDEBAR_MAX,
            lo::setSidebarWidth)) return true;
        if (clickSlider(mx, my, x, rightSliderY, 220,
            LayoutConstraints.RIGHT_PANEL_MIN, LayoutConstraints.RIGHT_PANEL_MAX,
            lo::setRightPanelWidth)) return true;
        if (clickSlider(mx, my, x, consoleSliderY, 220,
            LayoutConstraints.CONSOLE_MIN, LayoutConstraints.CONSOLE_MAX,
            lo::setConsoleHeight)) return true;

        if (MaredUi.hovered(mx, my, x, resetBtnY,
            MaredUi.px(160), MaredUi.px(20))) {
            ctx.layout().resetToDefaults();
            return true;
        }

        return false;
    }

    private boolean clickCheckbox(double mx, double my, int x, int rowY,
                                  int maxW) {
        if (my < rowY || my >= rowY + ROW_H) return false;
        if (mx < x || mx >= x + maxW) return false;
        return true;
    }

    private boolean clickSlider(double mx, double my, int x, int rowY,
                                int sliderW, int min, int max,
                                IntConsumer setter) {
        int sliderRowY = rowY + 14;
        if (my < sliderRowY || my >= sliderRowY + 14) return false;
        if (mx < x || mx >= x + MaredUi.px(sliderW)) return false;

        float t = (float) ((mx - x) / MaredUi.px(sliderW));
        t = Math.max(0f, Math.min(1f, t));
        int value = min + Math.round(t * (max - min));
        setter.accept(value);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta,
                                 int x, int y, int w, int h,
                                 SettingsContext ctx) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int maxScroll = Math.max(0, contentHeight - h);
        if (delta < 0) scroll = Math.min(maxScroll, scroll + 12);
        else if (delta > 0) scroll = Math.max(0, scroll - 12);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        return true;
    }

    private int computeHeight() {
        return 46 + 16 + 6 * ROW_H + 16 + 3 * (ROW_H + 18) + 24 + 80;
    }
}