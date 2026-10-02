package com.fixmer.mared.gui2.studio.panels.inspector;

import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class InspectorComponent extends MaredComponent implements Disposable {

    private static final int HEADER_H = 20;
    private static final int PAD = 6;
    private static final int LINE_H = 10;
    private static final int SCROLL_STEP = 20;

    private final SubscriptionGroup subs = new SubscriptionGroup();

    private MaredCommandRegistry.CommandInfo current;
    private int scrollOffset = 0;
    private int contentHeight = 0;

    public InspectorComponent(StudioEventBus bus) {
        subs.add(bus.subscribe(StudioEvents.CommandSelectedEvent.class,
            e -> select(e.info())));
    }

    @Override
    public void dispose() { subs.dispose(); }

    public MaredCommandRegistry.CommandInfo current() { return current; }

    public void select(MaredCommandRegistry.CommandInfo info) {
        this.current = info;
        this.scrollOffset = 0;
        this.contentHeight = 0;
    }

    @Override
    protected void safeRender(MaredRenderContext ctx) {
        GuiGraphics g = ctx.graphics();
        Font font = ctx.font();

        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF14141C);
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + HEADER_H, 0xFF1A1A24);
        g.drawString(font, "Inspector", bounds.x() + PAD, bounds.y() + 6,
            ThemeColors.accent(), false);
        g.fill(bounds.x(), bounds.y() + HEADER_H - 1,
               bounds.right(), bounds.y() + HEADER_H, 0xFF222233);

        if (current == null) {
            g.drawString(font, "No command selected",
                bounds.x() + PAD, bounds.y() + HEADER_H + 8,
                ThemeColors.muted(), false);
            contentHeight = 0;
            return;
        }

        int bodyY = bounds.y() + HEADER_H + 2;
        int bodyH = bounds.height() - HEADER_H - 4;
        int maxW = Math.max(40, bounds.width() - PAD * 2 - 6);

        contentHeight = computeContentHeight(font, maxW);

        int maxScroll = Math.max(0, contentHeight - bodyH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        g.enableScissor(bounds.x(), bodyY, bounds.right(), bodyY + bodyH);

        int y = bodyY - scrollOffset + 2;

        y = sectionHeader(g, font, "COMMAND", y);
        g.drawString(font, current.name, bounds.x() + PAD, y, ThemeColors.accent(), false);
        y += LINE_H + 2;
        y = kv(g, font, "category", current.category, y);
        y = kv(g, font, "op level", String.valueOf(current.opLevel), y);
        y += 4;

        if (notEmpty(current.description)) {
            y = sectionHeader(g, font, "DESCRIPTION", y);
            y = TextUtils.wrapped(g, font, current.description,
                bounds.x() + PAD, y, maxW, ThemeColors.text());
            y += 6;
        }

        if (notEmpty(current.example)) {
            y = sectionHeader(g, font, "EXAMPLE", y);
            y = TextUtils.wrapped(g, font, current.example,
                bounds.x() + PAD, y, maxW, 0xFF55FF88);
            y += 6;
        }

        if (!current.arguments.isEmpty()) {
            y = sectionHeader(g, font, "ARGUMENTS (" + current.arguments.size() + ")", y);
            for (MaredCommandRegistry.Argument arg : current.arguments) {
                g.drawString(font, "▸ " + arg.value,
                    bounds.x() + PAD + 2, y, ThemeColors.text(), false);
                y += LINE_H + 1;
                if (notEmpty(arg.description)) {
                    y = TextUtils.wrapped(g, font, arg.description,
                        bounds.x() + PAD + 10, y, maxW - 10, ThemeColors.muted());
                }
                y += 4;
            }
            y += 2;
        }

        if (!current.nbtHints.isEmpty()) {
            y = sectionHeader(g, font, "NBT (" + current.nbtHints.size() + ")", y);
            for (MaredCommandRegistry.NbtHint nbt : current.nbtHints) {
                g.drawString(font, nbt.tag,
                    bounds.x() + PAD + 2, y, ThemeColors.accent(), false);
                y += LINE_H + 1;
                if (notEmpty(nbt.what)) {
                    y = TextUtils.wrapped(g, font, nbt.what,
                        bounds.x() + PAD + 10, y, maxW - 10, ThemeColors.text());
                }
                if (notEmpty(nbt.why)) {
                    y = TextUtils.wrapped(g, font, nbt.why,
                        bounds.x() + PAD + 10, y, maxW - 10, ThemeColors.muted());
                }
                if (notEmpty(nbt.example)) {
                    y = TextUtils.wrapped(g, font, nbt.example,
                        bounds.x() + PAD + 10, y, maxW - 10, 0xFF55FF88);
                }
                y += 6;
            }
        }

        g.disableScissor();
        drawScrollbar(g, bodyY, bodyH, maxScroll);
    }

    private int sectionHeader(GuiGraphics g, Font font, String title, int y) {
        g.drawString(font, title, bounds.x() + PAD, y, ThemeColors.muted(), false);
        return y + LINE_H + 2;
    }

    private int kv(GuiGraphics g, Font font, String key, String value, int y) {
        g.drawString(font, key + ": " + value, bounds.x() + PAD, y, ThemeColors.muted(), false);
        return y + LINE_H;
    }

    private void drawScrollbar(GuiGraphics g, int bodyY, int bodyH, int maxScroll) {
        if (maxScroll <= 0) return;
        int trackX = bounds.right() - 4;
        int trackW = 3;
        g.fill(trackX, bodyY, trackX + trackW, bodyY + bodyH, 0xFF15151E);
        int thumbH = Math.max(10, bodyH * bodyH / Math.max(1, contentHeight));
        int thumbY = bodyY + (bodyH - thumbH) * scrollOffset / maxScroll;
        g.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, ThemeColors.accent());
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    private int computeContentHeight(Font font, int maxW) {
        int h = 4;
        h += LINE_H + 2 + LINE_H + 2 + LINE_H + LINE_H + 4;
        if (notEmpty(current.description)) {
            h += LINE_H + 2 + TextUtils.wrappedHeight(font, current.description, maxW) + 6;
        }
        if (notEmpty(current.example)) {
            h += LINE_H + 2 + TextUtils.wrappedHeight(font, current.example, maxW) + 6;
        }
        if (!current.arguments.isEmpty()) {
            h += LINE_H + 2;
            for (MaredCommandRegistry.Argument a : current.arguments) {
                h += LINE_H + 1;
                if (notEmpty(a.description))
                    h += TextUtils.wrappedHeight(font, a.description, maxW - 10);
                h += 4;
            }
            h += 2;
        }
        if (!current.nbtHints.isEmpty()) {
            h += LINE_H + 2;
            for (MaredCommandRegistry.NbtHint n : current.nbtHints) {
                h += LINE_H + 1;
                if (notEmpty(n.what))    h += TextUtils.wrappedHeight(font, n.what, maxW - 10);
                if (notEmpty(n.why))     h += TextUtils.wrappedHeight(font, n.why, maxW - 10);
                if (notEmpty(n.example)) h += TextUtils.wrappedHeight(font, n.example, maxW - 10);
                h += 6;
            }
        }
        return h;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!bounds.contains(mx, my)) return false;
        int bodyH = bounds.height() - HEADER_H - 4;
        int maxScroll = Math.max(0, contentHeight - bodyH);
        if (scrollY < 0) scrollOffset = Math.min(maxScroll, scrollOffset + SCROLL_STEP);
        else if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return bounds.contains(mx, my);
    }
}