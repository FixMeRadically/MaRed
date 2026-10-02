package com.fixmer.mared.gui2.framework.components.overlay;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Контекстное меню (ПКМ).
 *
 * 0.3.0: перенесён из gui.common.MaredContextMenu. Gui2 больше не
 * зависит от legacy gui.common по этому классу.
 *
 * Тема берётся из MaredThemeRegistry.active() — единый источник.
 * Глобальный синглтон: одно меню на всё приложение.
 */
public final class MaredContextMenu {

    private MaredContextMenu() {}

    // ============================================================
    //  Item
    // ============================================================

    public static final class Item {
        public final String label;
        public final Runnable action;
        public final boolean enabled;
        public final boolean separator;

        private Item(String label, Runnable action, boolean enabled, boolean separator) {
            this.label = label;
            this.action = action;
            this.enabled = enabled;
            this.separator = separator;
        }

        public static Item of(String label, Runnable action) {
            return new Item(label, action, true, false);
        }

        public static Item disabled(String label) {
            return new Item(label, null, false, false);
        }

        public static Item separator() {
            return new Item("", null, false, true);
        }

        public static Item hotkey(String label, String key, Runnable action) {
            return new Item(label + "\t" + key, action, true, false);
        }
    }

    // ============================================================
    //  State
    // ============================================================

    private static final List<Item> items = new ArrayList<>(8);
    private static int menuX = 0;
    private static int menuY = 0;
    private static int menuW = 0;
    private static int menuH = 0;
    private static boolean open = false;

    private static Runnable onClose = null;

    private static final int ITEM_H = 14;
    private static final int SEP_H  = 5;
    private static final int PAD_X  = 8;
    private static final int PAD_Y  = 4;

    // ============================================================
    //  Open / Close
    // ============================================================

    public static void open(int x, int y, List<Item> newItems) {
        open(x, y, newItems, null);
    }

    public static void open(int x, int y, List<Item> newItems, Runnable closeCallback) {
        if (newItems == null || newItems.isEmpty()) return;
        items.clear();
        items.addAll(newItems);
        menuX = x;
        menuY = y;
        onClose = closeCallback;
        open = true;
    }

    public static void close() {
        if (!open) return;
        open = false;
        items.clear();
        Runnable cb = onClose;
        onClose = null;
        if (cb != null) cb.run();
    }

    public static boolean isOpen() { return open; }
    public static int getX() { return menuX; }
    public static int getY() { return menuY; }

    // ============================================================
    //  Layout
    // ============================================================

    private static int computeWidth(Font font) {
        int w = 0;
        for (Item it : items) {
            if (it.separator) continue;
            String label = it.label;
            int tab = label.indexOf('\t');
            int textW;
            if (tab >= 0) {
                String left = label.substring(0, tab);
                String right = label.substring(tab + 1);
                textW = font.width(left) + 24 + font.width(right);
            } else {
                textW = font.width(label);
            }
            if (textW > w) w = textW;
        }
        return w + PAD_X * 2;
    }

    private static int computeHeight() {
        int h = PAD_Y * 2;
        for (Item it : items) h += it.separator ? SEP_H : ITEM_H;
        return h;
    }

    // ============================================================
    //  Render
    // ============================================================

    public static void render(GuiGraphics g, Font font, int screenW, int screenH,
                              int mouseX, int mouseY) {
        if (!open || font == null) return;

        MaredTheme t = MaredThemeRegistry.active();
        if (t == null) return;

        menuW = computeWidth(font);
        menuH = computeHeight();

        int bx = menuX;
        int by = menuY;
        if (bx + menuW > screenW - 4) bx = screenW - menuW - 4;
        if (by + menuH > screenH - 4) by = screenH - menuH - 4;
        if (bx < 4) bx = 4;
        if (by < 4) by = 4;
        menuX = bx;
        menuY = by;

        Render.rect(g, bx + 3, by + 3, bx + menuW + 3, by + menuH + 3, 0x80000000);
        Render.rect(g, bx, by, bx + menuW, by + menuH, t.bgPanelRaised);
        Render.outline(g, bx, by, menuW, menuH, t.accent);

        int curY = by + PAD_Y;
        for (Item it : items) {
            if (it.separator) {
                Render.rect(g, bx + PAD_X, curY + SEP_H / 2,
                    bx + menuW - PAD_X, curY + SEP_H / 2 + 1, t.divider);
                curY += SEP_H;
                continue;
            }

            boolean hov = it.enabled
                && mouseX >= bx && mouseX < bx + menuW
                && mouseY >= curY && mouseY < curY + ITEM_H;

            if (hov) {
                Render.rect(g, bx + 2, curY, bx + menuW - 2, curY + ITEM_H, t.bgHover);
            }

            int color = it.enabled ? t.text : t.textFaint;
            String label = it.label;
            int tab = label.indexOf('\t');
            if (tab >= 0) {
                String left = label.substring(0, tab);
                String right = label.substring(tab + 1);
                TextUtils.text(g, font, left, bx + PAD_X, curY + 3, color);
                int rw = font.width(right);
                TextUtils.text(g, font, right, bx + menuW - PAD_X - rw, curY + 3, t.textDim);
            } else {
                TextUtils.text(g, font, label, bx + PAD_X, curY + 3, color);
            }

            curY += ITEM_H;
        }
    }

    // ============================================================
    //  Mouse / Key
    // ============================================================

    /** @return true если клик поглощён меню. */
    public static boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;

        if (mx < menuX || mx >= menuX + menuW
            || my < menuY || my >= menuY + menuH) {
            close();
            return true;
        }

        if (button != 0) return true;

        int curY = menuY + PAD_Y;
        for (Item it : items) {
            if (it.separator) { curY += SEP_H; continue; }
            if (my >= curY && my < curY + ITEM_H) {
                if (it.enabled && it.action != null) {
                    Runnable action = it.action;
                    close();
                    action.run();
                }
                return true;
            }
            curY += ITEM_H;
        }
        return true;
    }

    public static boolean keyPressed(int keyCode) {
        if (!open) return false;
        if (keyCode == 256) { // Escape
            close();
            return true;
        }
        return false;
    }

    // ============================================================
    //  Утилиты — координаты мыши из GLFW
    // ============================================================

    public static int[] mousePos() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        double[] mx = new double[1];
        double[] my = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, mx, my);
        double scale = mc.getWindow().getGuiScale();
        return new int[]{(int) (mx[0] / scale), (int) (my[0] / scale)};
    }
}