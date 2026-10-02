package com.fixmer.mared.gui2.framework.render;

import java.util.List;

import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Фасад над gui2 render-утилитами.
 *
 * 0.3.0 (Phase B): разгружен. Вложенные типы (ScrollArea, PixelScroll,
 * DragState, DragKind, Content, ItemRenderer) вынесены в top-level.
 * Логика панелей/виджетов вынесена в UiPanels/UiControls.
 *
 * Здесь остались только:
 *  - константы палитры и иконок;
 *  - тонкие делегаты над Render/TextUtils/MaredColor;
 *  - делегаты над UiPanels/UiControls;
 *  - theme()/px()/pad* как bridge.
 *
 * @deprecated after 0.4.0 — новый код должен использовать Render,
 * UiPanels, UiControls, top-level ScrollArea/PixelScroll/Content,
 * UiContext и UiContext.theme() явно. Фасад удаляется.
 */
@Deprecated
public final class MaredUi {

    private MaredUi() {}

    // ============================================================
    //  Палитра
    // ============================================================

    public static final int SCREEN_BG     = 0xFF0A0A10;
    public static final int PANEL_BG      = 0xFF14141C;
    public static final int PANEL_RAISED  = 0xFF1A1A24;
    public static final int SUNKEN_BG     = 0xFF0A0A10;
    public static final int TEXT          = 0xFFFFFFFF;
    public static final int TEXT_DIM      = 0xFFAAAAAA;
    public static final int DANGER        = 0xFFFF4444;
    public static final int SUCCESS       = 0xFF55FF88;
    public static final int WARN          = 0xFFFFAA00;
    public static final int ACCENT_BLUE   = 0xFF55AAFF;
    public static final int ACCENT_PURPLE = 0xFFFF55FF;
    public static final int DIVIDER       = 0xFF333344;
    public static final int SCROLL_TRACK  = 0xFF15151E;

    // ============================================================
    //  Иконки
    // ============================================================

    public static final String ICO_STAR     = "★";
    public static final String ICO_STAR_OFF = "☆";
    public static final String ICO_ARROW_D  = "▼";
    public static final String ICO_ARROW_R  = "▶";

    // ============================================================
    //  Примитивы (делегаты)
    // ============================================================

    public static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        Render.rect(g, x1, y1, x2, y2, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        Render.outline(g, x, y, w, h, color);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        Render.panel(g, x, y, w, h, bg, border);
    }

    public static void panelGradient(GuiGraphics g, int x, int y, int w, int h,
                                     int bg, int topColor, int bottomColor) {
        Render.panelGradient(g, x, y, w, h, bg, topColor, bottomColor);
    }

    public static void outlineGradient(GuiGraphics g, int x, int y, int w, int h,
                                       int topColor, int bottomColor) {
        Render.outlineGradient(g, x, y, w, h, topColor, bottomColor);
    }

    public static void gradientV(GuiGraphics g, int x1, int y1, int x2, int y2,
                                 int topColor, int bottomColor) {
        Render.gradientV(g, x1, y1, x2, y2, topColor, bottomColor);
    }

    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h,
                                   int radius, int color) {
        Render.roundedRect(g, x, y, w, h, radius, color);
    }

    public static void roundedOutline(GuiGraphics g, int x, int y, int w, int h,
                                      int radius, int color) {
        Render.roundedOutline(g, x, y, w, h, radius, color);
    }

    // ============================================================
    //  Цвет (делегаты)
    // ============================================================

    public static int lighten(int color, float amount) { return MaredColor.lighten(color, amount); }
    public static int darken(int color, float amount)  { return MaredColor.darken(color, amount); }
    public static int withAlpha(int color, int alpha)  { return MaredColor.withAlpha(color, alpha); }
    public static int lerpColor(int a, int b, float t) { return MaredColor.lerpColor(a, b, t); }

    // ============================================================
    //  Текст (делегаты)
    // ============================================================

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
        TextUtils.text(g, f, s, x, y, c);
    }

    public static void textNoShadow(GuiGraphics g, Font f, String s, int x, int y, int c) {
        TextUtils.textNoShadow(g, f, s, x, y, c);
    }

    public static void centered(GuiGraphics g, Font f, String s, int cx, int y, int c) {
        TextUtils.centered(g, f, s, cx, y, c);
    }

    public static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return Render.hovered(mx, my, x, y, w, h);
    }

    public static String ellipsize(Font f, String s, int maxW) {
        return TextUtils.ellipsize(f, s, maxW);
    }

    public static List<String> wrapLines(Font f, String text, int maxW) {
        return TextUtils.wrapLines(f, text, maxW);
    }

    public static List<String> wrapLinesCached(Font f, String text, int maxW) {
        return TextUtils.wrapLinesCached(f, text, maxW);
    }

    public static int wrapped(GuiGraphics g, Font f, String text, int x, int y, int maxW, int c) {
        return TextUtils.wrapped(g, f, text, x, y, maxW, c);
    }

    public static int wrappedHeight(Font f, String text, int maxW) {
        return TextUtils.wrappedHeight(f, text, maxW);
    }

    public static void clearWrapCaches() { TextUtils.clearCaches(); }

    // ============================================================
    //  Панели (делегаты над UiPanels)
    // ============================================================

    public static void fillPanel(GuiGraphics g, int x, int y, int w, int h,
                                 String tab, boolean raised) {
        UiPanels.fillPanel(g, x, y, w, h, tab, raised);
    }

    public static void fillPanel(GuiGraphics g, int x, int y, int w, int h, boolean raised) {
        UiPanels.fillPanel(g, x, y, w, h, raised);
    }

    public static void fillPanelRounded(GuiGraphics g, int x, int y, int w, int h,
                                        String tab, boolean raised, int radius) {
        UiPanels.fillPanelRounded(g, x, y, w, h, tab, raised, radius);
    }

    public static void fillOverlay(GuiGraphics g, int x, int y, int w, int h, int radius) {
        UiPanels.fillOverlay(g, x, y, w, h, radius);
    }

    public static void panelLit(GuiGraphics g, int x, int y, int w, int h, int baseColor) {
        UiPanels.panelLit(g, x, y, w, h, baseColor);
    }

    public static void panelLitBordered(GuiGraphics g, int x, int y, int w, int h,
                                        int baseColor, int border) {
        UiPanels.panelLitBordered(g, x, y, w, h, baseColor, border);
    }

    public static void panel3D(GuiGraphics g, int x, int y, int w, int h,
                               int bg, int accentTop, int accentBottom) {
        UiPanels.panel3D(g, x, y, w, h, bg, accentTop, accentBottom);
    }

    public static void dialogBackground(GuiGraphics g, int width, int height) {
        UiPanels.dialogBackground(g, width, height);
    }

    public static void dialogPanel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        UiPanels.dialogPanel(g, x, y, w, h, accent);
    }

    public static void dashedLine(GuiGraphics g, int x1, int y, int x2, int color) {
        UiPanels.dashedLine(g, x1, y, x2, color);
    }

    public static void dashedLineGradient(GuiGraphics g, int x1, int y, int x2,
                                          int leftColor, int rightColor) {
        UiPanels.dashedLineGradient(g, x1, y, x2, leftColor, rightColor);
    }

    public static void shadowAll(GuiGraphics g, int x, int y, int w, int h,
                                 int size, int color) {
        UiPanels.shadowAll(g, x, y, w, h, size, color);
    }

    // ============================================================
    //  Виджеты (делегаты над UiControls)
    // ============================================================

    public static void button3D(GuiGraphics g, Font f, int x, int y, int w, int h,
                                String label, int bg, int accent, int textColor,
                                boolean hovered) {
        MaredWidgets.button3D(g, f, x, y, w, h, label, bg, accent, textColor, hovered);
    }

    public static void button(GuiGraphics g, Font f, int x, int y, int w, int h,
                              String label, int bg, int border,
                              boolean hovered, int textColor) {
        UiControls.button(g, f, x, y, w, h, label, bg, border, hovered, textColor);
    }

    public static void buttonGradient(GuiGraphics g, Font f, int x, int y, int w, int h,
                                      String label, int bg, int topColor, int bottomColor,
                                      int textColor) {
        UiControls.buttonGradient(g, f, x, y, w, h, label, bg, topColor, bottomColor, textColor);
    }

    public static void buttonGhost(GuiGraphics g, Font f, int x, int y, int w, int h,
                                   String label, int accent, boolean hovered) {
        UiControls.buttonGhost(g, f, x, y, w, h, label, accent, hovered);
    }

    public static void drawCheckbox(GuiGraphics g, int x, int y, int sz,
                                    boolean checked, int border, int fill) {
        UiControls.drawCheckbox(g, x, y, sz, checked, border, fill);
    }

    public static void drawCheckMark(GuiGraphics g, int x, int y, int sz,
                                     boolean checked, int border, int fill) {
        UiControls.drawCheckMark(g, x, y, sz, checked, border, fill);
    }

    public static void drawRadio(GuiGraphics g, int x, int y, int sz,
                                 boolean selected, int border, int fill) {
        UiControls.drawRadio(g, x, y, sz, selected, border, fill);
    }

    public static void drawDelButton(GuiGraphics g, int x, int y, int sz, boolean hovered) {
        UiControls.drawDelButton(g, x, y, sz, hovered);
    }

    public static void drawUnlockButton(GuiGraphics g, Font f, int x, int y, int sz,
                                        boolean hovered) {
        UiControls.drawUnlockButton(g, f, x, y, sz, hovered);
    }

    public static void slider(GuiGraphics g, int x, int y, int w, int h,
                              float value01, int trackColor, int thumbColor) {
        UiControls.slider(g, x, y, w, h, value01, trackColor, thumbColor);
    }

    public static void scrollbarTrack(GuiGraphics g, int x, int y, int w, int h) {
        UiControls.scrollbarTrack(g, x, y, w, h);
    }

    public static void scrollbarThumb(GuiGraphics g, int x, int y, int w, int h,
                                      int topColor, int bottomColor) {
        UiControls.scrollbarThumb(g, x, y, w, h, topColor, bottomColor);
    }

    public static void drawScrollbar(GuiGraphics g, int trackX, int trackY,
                                     int trackW, int trackH,
                                     int thumbY, int thumbH,
                                     int accentTop, int accentBottom) {
        UiControls.drawScrollbar(g, trackX, trackY, trackW, trackH,
            thumbY, thumbH, accentTop, accentBottom);
    }

    public static void scissorOn(GuiGraphics g, int x1, int y1, int x2, int y2) {
        UiControls.scissorOn(g, x1, y1, x2, y2);
    }

    public static void scissorOff(GuiGraphics g) {
        UiControls.scissorOff(g);
    }

    // ============================================================
    //  Списки (оставлены как удобные утилиты)
    // ============================================================

    public static void listRow(GuiGraphics g, int x, int y, int w, int h,
                               int bg, boolean selected,
                               int selAccentTop, int selAccentBottom) {
        g.fill(x, y, x + w, y + h, bg);
        if (selected) Render.gradientV(g, x, y, x + 2, y + h, selAccentTop, selAccentBottom);
    }

    public static void listGradient(GuiGraphics g, Font f, ScrollArea area,
                                    int selectedIdx, int scrollbarWidth,
                                    int topColor, int bottomColor,
                                    int selColor, int hoverColor, int normalColor,
                                    ItemRenderer renderer, int mouseX, int mouseY) {
        if (area.itemCount == 0) return;
        area.clamp();

        int itemW = area.contentW(scrollbarWidth);
        int visible = area.visibleItems();
        for (int i = 0; i < visible; i++) {
            int idx = i + area.offset;
            if (idx >= area.itemCount) break;
            int itemY = area.y + i * area.itemHeight;
            boolean selected = idx == selectedIdx;
            boolean hov = mouseX >= area.x && mouseX < area.x + itemW
                       && mouseY >= itemY && mouseY < itemY + area.itemHeight - 2;
            int bg = selected ? selColor : (hov ? hoverColor : normalColor);
            listRow(g, area.x, itemY, itemW, area.itemHeight - 2, bg, selected,
                topColor, bottomColor);
            renderer.render(g, f, idx, area.x, itemY, itemW,
                area.itemHeight - 2, hov, selected);
        }
        area.drawScrollbarGradient(g, topColor, bottomColor, scrollbarWidth);
    }

    // ============================================================
    //  Bridge (тонкие хелперы)
    // ============================================================

    public static MaredTheme theme() { return MaredThemeRegistry.active(); }
    public static int px(int value)  { return MaredScale.px(value); }

    public static int padSmall()  { return 4; }
    public static int padMedium() { return 8; }
    public static int padLarge()  { return 12; }

    public static int pxScrollbarW() { return 6; }
    public static int radiusSmall()  { return 2; }

    public static int radiusLarge() {
        MaredTheme t = theme();
        return t == null ? 6 : t.radiusLarge;
    }
}