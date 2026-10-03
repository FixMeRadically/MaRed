package com.fixmer.mared.gui2.framework.components;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.core.NarratableComponent;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Текстовый компонент.
 *
 * 0.3.2 (audit #11): Component / translation key / alignment / wrap /
 * ellipsis / semantic color.
 *
 * 0.3.2 (audit #77): реализует NarratableComponent. Хотя MaredLabel
 * не focusable по умолчанию, narration-текст может быть запрошен
 * вышестоящим контейнером (например, чтобы озвучить подпись группы).
 */
public class MaredLabel extends MaredComponent implements NarratableComponent {

    public enum Align { LEFT, CENTER, RIGHT }
    public enum Wrap { NONE, WORD }

    private String text;
    private String translationKey;
    private Align align = Align.LEFT;
    private Wrap wrap = Wrap.WORD;
    private Integer explicitColor;

    public MaredLabel(String text) {
        this.text = text != null ? text : "";
    }

    public MaredLabel(Component component) {
        this.text = component != null ? component.getString() : "";
    }

    public static MaredLabel ofKey(String key) {
        MaredLabel l = new MaredLabel("");
        l.translationKey = key;
        return l;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
        this.translationKey = null;
    }

    public void setText(Component component) {
        this.text = component != null ? component.getString() : "";
        this.translationKey = null;
    }

    public void setTranslationKey(String key) {
        this.translationKey = key;
        this.text = "";
    }

    public void setAlign(Align a) { this.align = a != null ? a : Align.LEFT; }
    public void setWrap(Wrap w)   { this.wrap = w != null ? w : Wrap.WORD; }
    public void setColor(int color) { this.explicitColor = color; }
    public void clearColor()        { this.explicitColor = null; }

    public String text() { return text; }

    @Override
    protected void safeRender(MaredRenderContext context) {
        GuiGraphics g = context.graphics();
        Font font = context.font();

        String resolved = resolveText();
        if (resolved == null || resolved.isEmpty()) return;

        int color = explicitColor != null
            ? explicitColor
            : ThemeColors.text();

        int x = bounds.x();
        int y = bounds.y();
        int w = bounds.width();

        if (wrap == Wrap.WORD) {
            TextUtils.wrapped(g, font, resolved, x, y, w, color);
            return;
        }

        int maxW = Math.max(0, w);
        String display = TextUtils.ellipsize(font, resolved, maxW);
        int textW = font.width(display);

        int drawX;
        switch (align) {
            case CENTER -> drawX = x + (maxW - textW) / 2;
            case RIGHT  -> drawX = x + (maxW - textW);
            default     -> drawX = x;
        }

        int drawY = y + Math.max(0, (bounds.height() - 8) / 2);
        g.drawString(font, display, drawX, drawY, color, true);
    }

    private String resolveText() {
        if (translationKey != null && !translationKey.isEmpty()) {
            return MaredLang.get(translationKey);
        }
        return text;
    }

    // ============================================================
    //  Narration
    // ============================================================

    @Override
    public Component narrationText() {
        String resolved = resolveText();
        if (resolved == null || resolved.isEmpty()) return null;
        return Component.literal(resolved);
    }
}