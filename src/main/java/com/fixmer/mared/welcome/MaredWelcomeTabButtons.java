package com.fixmer.mared.welcome;

import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.render.MaredTabStyles;
import com.fixmer.mared.gui2.framework.render.MaredUi;
import com.fixmer.mared.modules.ModuleAvailability;
import com.fixmer.mared.modules.ModuleDescriptor;
import com.fixmer.mared.modules.ModuleRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Stage B7): MaredTabStyles / MaredUi — из gui2.framework.render.
 *
 * 0.3.1: карточки формируются из ModuleRegistry.
 *   - Никаких хардкодных "scripts/commands/npc/events/quests".
 *   - Никаких кэшированных переведённых строк — перевод резолвится
 *     через MaredLang.get() во время render.
 *   - Модуль со статусом SOON/DISABLED помечается и не открывается.
 *   - Если модулей больше, чем влезает в 2 ряда — layout переносит
 *     их построчно; max — весь экран.
 */
public final class MaredWelcomeTabButtons {

    private static final class TabButton {
        final ModuleDescriptor module;
        int x, y, w, h;
        TabButton(ModuleDescriptor module) { this.module = module; }
    }

    private final List<TabButton> buttons;

    public MaredWelcomeTabButtons() {
        List<ModuleDescriptor> modules = ModuleRegistry.all();
        this.buttons = new java.util.ArrayList<>(modules.size());
        for (ModuleDescriptor m : modules) {
            buttons.add(new TabButton(m));
        }
    }

    // ============================================================
    //  Layout
    // ============================================================

    /**
     * Раскладка карточек по центру.
     *
     * Стратегия: до 3 в ряд. Ряд 1 — первые 3, ряд 2 — следующие 2,
     * остаток — третий ряд и т.д. Каждый ряд выравнивается по центру.
     */
    public void layout(int centerX, int centerY) {
        int n = buttons.size();
        if (n == 0) return;

        int perRow = Math.min(3, n);
        int btnW = MaredUi.px(120);
        int btnH = MaredUi.px(90);
        int gapX = MaredUi.px(16);
        int gapY = MaredUi.px(16);

        int rows = (n + perRow - 1) / perRow;
        int totalH = rows * btnH + (rows - 1) * gapY;
        int startY = centerY - totalH / 2;

        for (int r = 0; r < rows; r++) {
            int from = r * perRow;
            int to = Math.min(n, from + perRow);
            int inRow = to - from;

            int rowW = inRow * btnW + (inRow - 1) * gapX;
            int rowX = centerX - rowW / 2;
            int rowY = startY + r * (btnH + gapY);

            for (int i = 0; i < inRow; i++) {
                TabButton b = buttons.get(from + i);
                b.x = rowX + i * (btnW + gapX);
                b.y = rowY;
                b.w = btnW;
                b.h = btnH;
            }
        }
    }

    // ============================================================
    //  Render
    // ============================================================

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        for (TabButton b : buttons) {
            boolean hover = b.module.availability() == ModuleAvailability.AVAILABLE
                && MaredUi.hovered(mouseX, mouseY, b.x, b.y, b.w, b.h);
            drawCard(g, font, b, hover);
        }
    }

    private void drawCard(GuiGraphics g, Font font, TabButton b, boolean hover) {
        ModuleDescriptor m = b.module;

        int accentTop = MaredTabStyles.topColor(m.id());
        int accentBot = MaredTabStyles.bottomColor(m.id());

        // 0.3.1: карточка недоступного модуля приглушена.
        boolean dim = m.availability() != ModuleAvailability.AVAILABLE;
        if (dim) {
            accentTop = MaredUi.darken(accentTop, 0.55f);
            accentBot = MaredUi.darken(accentBot, 0.55f);
        }

        MaredUi.shadowAll(g, b.x, b.y, b.w, b.h, hover ? 4 : 2, 0x80000000);

        int bg = hover
            ? MaredUi.lighten(MaredUi.theme().bgPanelRaised, 0.05f)
            : MaredUi.theme().bgPanelRaised;
        MaredUi.roundedRect(g, b.x, b.y, b.w, b.h,
            MaredUi.radiusLarge(), bg);

        MaredUi.roundedRect(g, b.x, b.y, b.w, MaredUi.px(4),
            MaredUi.radiusLarge(), accentTop);
        MaredUi.rect(g, b.x, b.y + MaredUi.px(2),
            b.x + b.w, b.y + MaredUi.px(4), accentTop);

        MaredUi.roundedOutline(g, b.x, b.y, b.w, b.h,
            MaredUi.radiusLarge(),
            hover ? accentTop : MaredUi.theme().border);

        if (MaredSettings.isPatternsEnabled()) {
            MaredTabStyles.drawBackgroundPattern(g, b.x + 1,
                b.y + MaredUi.px(5),
                b.w - 2, b.h - MaredUi.px(6), m.id(), 20);
        }

        int letterY = b.y + MaredUi.px(14);
        MaredUi.centered(g, font, m.icon(), b.x + b.w / 2, letterY, accentTop);

        int nameY = b.y + MaredUi.px(46);
        String name = MaredLang.get(m.displayNameKey());
        MaredUi.centered(g, font, name, b.x + b.w / 2, nameY,
            dim ? MaredUi.theme().textDim : MaredUi.theme().text);

        int descY = b.y + MaredUi.px(60);
        String desc = MaredLang.get(m.descriptionKey());
        for (String line : desc.split("\n")) {
            MaredUi.centered(g, font, line, b.x + b.w / 2, descY,
                MaredUi.theme().textDim);
            descY += 10;
        }

        // 0.3.1: badge для недоступных модулей.
        if (m.availability() == ModuleAvailability.SOON) {
            String badge = MaredLang.get("mared.module.badge.soon");
            int bw = font.width(badge) + MaredUi.px(8);
            int bx = b.x + b.w - bw - MaredUi.px(4);
            int by = b.y + MaredUi.px(6);
            MaredUi.rect(g, bx, by, bx + bw, by + MaredUi.px(10),
                MaredUi.darken(accentTop, 0.5f));
            MaredUi.centered(g, font, badge, bx + bw / 2, by + 1,
                0xFFFFFFFF);
        }
    }

    // ============================================================
    //  Mouse
    // ============================================================

    /**
     * @return id модуля, если клик попал в доступную карточку;
     *         null — если клик мимо или карточка недоступна.
     */
    public String mouseClicked(double mx, double my) {
        for (TabButton b : buttons) {
            if (!MaredUi.hovered(mx, my, b.x, b.y, b.w, b.h)) continue;
            if (b.module.availability() != ModuleAvailability.AVAILABLE) {
                return null;
            }
            return b.module.id();
        }
        return null;
    }

    public boolean contains(double mx, double my) {
        for (TabButton b : buttons) {
            if (MaredUi.hovered(mx, my, b.x, b.y, b.w, b.h)) return true;
        }
        return false;
    }
}