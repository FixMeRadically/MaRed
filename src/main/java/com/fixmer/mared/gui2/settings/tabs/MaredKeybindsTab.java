package com.fixmer.mared.gui2.settings.tabs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.input.KeybindItem;
import com.fixmer.mared.commands.input.MaredKeyNames;
import com.fixmer.mared.gui2.framework.render.legacy.MaredUi;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Keybind settings tab.
 *
 * 0.3.2 (audit #100 / #101):
 *   - Поиск по key/label.
 *   - Rebind: клик по кнопке "Rebind" → следующий key event
 *     переименовывает привязку. Esc — отмена.
 *   - Конфликты: если newKey уже занят — ошибка, остаёмся в режиме.
 *   - Reset all — как и раньше, через draft-commit.
 *
 * В отличие от остальных табов, rename применяется НЕМЕДЛЕННО
 * (не откладывается до Save). Причина: rebind — явный user action,
 * а не toggle. Если бы мы откладывали — UX был бы непонятен
 * (пользователь нажал новую клавишу, а bind не сработал до Save).
 */
public final class MaredKeybindsTab implements MaredSettingsTab {

    private static final int ROW_H = 22;
    private static final int HEADER_H = 22;
    private static final int SEARCH_H = 18;
    private static final int REBIND_BTN_W = 90;
    private static final int RESET_BTN_W = 160;
    private static final int BLOCK_TAG_W = 60;

    private int scroll = 0;
    private int contentHeight = 0;

    // 0.3.2 search
    private final StringBuilder searchQuery = new StringBuilder();
    private boolean searchFocused = false;

    // 0.3.2 rebind
    private String rebindKey = null;       // key, который сейчас переназначаем
    private String rebindError = "";       // сообщение о конфликте

    // Layout caches
    private int searchX, searchY, searchW;
    private int rowsTopY;
    private int resetBtnY;
    private final List<RowHit> rowHits = new ArrayList<>(16);

    private static final class RowHit {
        final int index;
        final int y;
        final int rebindBtnX;
        RowHit(int index, int y, int rebindBtnX) {
            this.index = index; this.y = y; this.rebindBtnX = rebindBtnX;
        }
    }

    @Override public String id() { return "keybinds"; }
    @Override public String displayName() {
        return MaredLang.get("mared.settings.tab.keybinds");
    }
    @Override public int accentColor() { return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accent; }

    @Override
    public void onOpen(SettingsContext ctx) {
        scroll = 0;
        searchQuery.setLength(0);
        searchFocused = false;
        rebindKey = null;
        rebindError = "";
    }

    @Override
    public void onClose(SettingsContext ctx) {
        rebindKey = null;
        rebindError = "";
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       SettingsContext ctx, int mouseX, int mouseY) {
        List<KeybindItem> all = ctx.keybinds().searchBinds(searchQuery.toString());

        // --- Header: search ---
        searchX = x;
        searchY = y;
        searchW = Math.max(80, w - 8);
        drawSearch(g, font, mouseX, mouseY);

        int listTop = y + SEARCH_H + 6;
        int listBottom = y + h - RESET_BTN_W - 40;
        int listH = Math.max(20, listBottom - listTop);
        rowsTopY = listTop - scroll;

        contentHeight = all.size() * ROW_H + 20;

        g.enableScissor(x, listTop, x + w, listTop + listH);

        rowHits.clear();
        int rowY = rowsTopY;
        int cx = x;
        int rowW = w - 8;
        for (int i = 0; i < all.size(); i++) {
            KeybindItem it = all.get(i);

            int screenY = rowY;
            if (screenY + ROW_H < listTop || screenY > listTop + listH) {
                rowY += ROW_H;
                continue;
            }
            drawRow(g, font, cx, screenY, rowW, it, ctx, mouseX, mouseY);
            rowY += ROW_H;
        }

        g.disableScissor();

        // --- Reset button ---
        resetBtnY = y + h - 30;
        boolean resetHover = MaredUi.hovered(mouseX, mouseY, x, resetBtnY,
            MaredUi.px(RESET_BTN_W), MaredUi.px(20));
        MaredUi.button3D(g, font, x, resetBtnY,
            MaredUi.px(RESET_BTN_W), MaredUi.px(20),
            MaredLang.get("mared.settings.keybinds.reset_all"),
            resetHover ? 0xFF663333 : 0xFF3A2020,
            com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().danger, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text, resetHover);

        if (ctx.keybinds().isResetRequested()) {
            MaredUi.text(g, font, "● " + MaredLang.get(
                "mared.settings.keybinds.reset_done"),
                x + MaredUi.px(RESET_BTN_W) + 8, resetBtnY + 6,
                com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().warn);
        }

        // --- Error hint под списком ---
        if (!rebindError.isEmpty()) {
            MaredUi.text(g, font, rebindError,
                x, resetBtnY - 18, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().danger);
        } else if (rebindKey != null) {
            MaredUi.text(g, font,
                MaredLang.get("mared.settings.keybinds.awaiting"),
                x, resetBtnY - 18, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().warn);
        }
    }

    private void drawSearch(GuiGraphics g, Font font, int mouseX, int mouseY) {
        MaredUi.rect(g, searchX, searchY, searchX + searchW, searchY + SEARCH_H,
            com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgSunken);
        MaredUi.outline(g, searchX, searchY, searchW, SEARCH_H,
            searchFocused ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accent : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().border);

        String q = searchQuery.toString();
        int textY = searchY + (SEARCH_H - 8) / 2;

        if (q.isEmpty() && !searchFocused) {
            MaredUi.textNoShadow(g, font,
                MaredLang.get("mared.settings.keybinds.search_hint"),
                searchX + 6, textY, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textFaint);
        } else {
            MaredUi.textNoShadow(g, font, q, searchX + 6, textY, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text);

            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = searchX + 6 + font.width(q);
                MaredUi.rect(g, cx, searchY + 3, cx + 1, searchY + SEARCH_H - 3,
                    com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text);
            }
        }
    }

    private void drawRow(GuiGraphics g, Font font, int x, int y, int rowW,
                         KeybindItem it, SettingsContext ctx,
                         int mouseX, int mouseY) {
        MaredUi.rect(g, x, y, x + rowW, y + ROW_H - 2, 0xFF1A1A22);

        int keyW = MaredUi.px(140);
        MaredUi.rect(g, x, y, x + keyW, y + ROW_H - 2, 0xFF23232E);

        boolean isRebinding = rebindKey != null && rebindKey.equals(it.key());
        int keyColor = isRebinding ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().warn : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accent;
        String keyText = isRebinding
            ? MaredLang.get("mared.settings.keybinds.awaiting_short")
            : it.key();
        MaredUi.text(g, font, keyText, x + 6, y + 6, keyColor);

        MaredUi.text(g, font, it.label(), x + keyW + 8, y + 6, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text);

        if (it.blocking()) {
            MaredUi.text(g, font, "[BLOCK]",
                x + rowW - BLOCK_TAG_W - REBIND_BTN_W - 8, y + 6,
                com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().danger);
        }

        // Rebind button
        int btnX = x + rowW - REBIND_BTN_W - 4;
        boolean hover = MaredUi.hovered(mouseX, mouseY, btnX, y + 2,
            REBIND_BTN_W, ROW_H - 6);
        int btnBg = hover ? 0xFF2D2D3D : 0xFF23232E;
        MaredUi.rect(g, btnX, y + 2, btnX + REBIND_BTN_W, y + ROW_H - 4, btnBg);
        MaredUi.outline(g, btnX, y + 2, REBIND_BTN_W, ROW_H - 6,
            hover ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accent : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().border);
        MaredUi.centered(g, font,
            MaredLang.get("mared.settings.keybinds.rebind"),
            btnX + REBIND_BTN_W / 2, y + 6,
            hover ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textDim);

        rowHits.add(new RowHit(-1, y, btnX));
    }

    // ============================================================
    //  Mouse
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button,
                                int x, int y, int w, int h,
                                SettingsContext ctx) {
        if (button != 0) return false;

        // Search field
        if (mx >= searchX && mx < searchX + searchW
            && my >= searchY && my < searchY + SEARCH_H) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // Reset button
        if (MaredUi.hovered(mx, my, x, resetBtnY,
            MaredUi.px(RESET_BTN_W), MaredUi.px(20))) {
            ctx.keybinds().requestReset();
            return true;
        }

        // Rows: rebind button hit-test
        for (RowHit hit : rowHits) {
            if (mx >= hit.rebindBtnX && mx < hit.rebindBtnX + REBIND_BTN_W
                && my >= hit.y + 2 && my < hit.y + ROW_H - 4) {
                List<KeybindItem> list = ctx.keybinds()
                    .searchBinds(searchQuery.toString());
                int idx = rowHits.indexOf(hit);
                if (idx >= 0 && idx < list.size()) {
                    rebindKey = list.get(idx).key();
                    rebindError = "";
                }
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta,
                                 int x, int y, int w, int h,
                                 SettingsContext ctx) {
        int maxScroll = Math.max(0, contentHeight - h);
        if (delta < 0) scroll = Math.min(maxScroll, scroll + 12);
        else if (delta > 0) scroll = Math.max(0, scroll - 12);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        return true;
    }

    // ============================================================
    //  Keyboard
    // ============================================================

    /**
     * Если rebindKey != null — перехватываем всё.
     * Если searchFocused — обрабатываем Backspace/Escape/символы.
     * Иначе — пропускаем в Screen (переключение табов).
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers,
                              SettingsContext ctx) {

        // --- Rebind mode ---
        if (rebindKey != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                rebindKey = null;
                rebindError = "";
                return true;
            }

            // Собрать display name новой клавиши.
            String newKeyName = buildKeyName(keyCode, modifiers);
            if (newKeyName == null) {
                return true; // модификатор сам по себе — игнорируем
            }

            String oldKey = rebindKey;
            rebindKey = null;

            if (oldKey.equals(newKeyName)) {
                rebindError = "";
                return true;
            }
            if (ctx.keybinds().hasKey(newKeyName)) {
                rebindError = MaredLang.format(
                    "mared.settings.keybinds.conflict", newKeyName);
                return true;
            }

            boolean ok = ctx.keybinds().rename(oldKey, newKeyName);
            if (!ok) {
                rebindError = MaredLang.get(
                    "mared.settings.keybinds.rename_failed");
            } else {
                rebindError = "";
            }
            return true;
        }

        // --- Search field ---
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchQuery.setLength(0);
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (searchQuery.length() > 0) {
                    searchQuery.deleteCharAt(searchQuery.length() - 1);
                }
                return true;
            }
            // Остальные клавиши — не перехватываем (пусть Screen
            // обработает tab-switching).
        }

        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers, SettingsContext ctx) {
        if (rebindKey != null) return true; // не пропускаем текст в search
        if (searchFocused && c >= 32) {
            searchQuery.append(c);
            return true;
        }
        return false;
    }

    /**
     * Собирает display name новой клавиши из keyCode+modifiers.
     * Возвращает null для чистых модификаторов.
     */
    private static String buildKeyName(int keyCode, int modifiers) {
        // Чистые модификаторы — не биндятся.
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
            || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
            || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL
            || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
            || keyCode == GLFW.GLFW_KEY_LEFT_ALT
            || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
            || keyCode == GLFW.GLFW_KEY_LEFT_SUPER
            || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER) {
            return null;
        }

        String base = MaredKeyNames.nameForKeyCode(keyCode);
        if (base == null || base.isEmpty()) return null;
        if (base.startsWith("Key#")) return null; // неизвестная клавиша

        StringBuilder sb = new StringBuilder(16);
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT)   != 0) sb.append("Shift+");
        if ((modifiers & GLFW.GLFW_MOD_ALT)     != 0) sb.append("Alt+");
        if ((modifiers & GLFW.GLFW_MOD_SUPER)   != 0) sb.append("Super+");
        sb.append(base);
        return sb.toString();
    }
}