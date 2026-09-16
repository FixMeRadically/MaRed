package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.script.MaredLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Верхняя панель с кнопками.
 * Ширина каждой кнопки = max(minBaseWidth, font.width(label) + 16).
 */
public final class MaredEditorToolbar {

    private final List<AbstractWidget> buttons = new ArrayList<>();

    public MaredEditorToolbar() {}

    public List<AbstractWidget> buttons() { return buttons; }
    public void clear() { buttons.clear(); }

    public void build(ScreenContext ctx, Font font) {
        clear();

        int pad = MaredEditorLayout.PAD();
        int y = pad + 1;
        int right = ctx.screenW() - pad;
        boolean commandsTab = "commands".equals(ctx.openTab());

        Object[][] defs = {
            {MaredLang.get("mared.ui.close"),             "60", "FF5555", Boolean.FALSE},
            {MaredLang.get("mared.ui.run"),               "50", "55FF55", Boolean.FALSE},
            {MaredLang.get("mared.ui.save"),              "70", "55AAFF", Boolean.FALSE},
            {MaredLang.get("mared.ui.delete"),            "70", "FF4444", Boolean.FALSE},
            {MaredLang.get("mared.ui.import"),            "70", "FFAA00", Boolean.FALSE},
            {MaredLang.get("mared.ui.settings"),          "80", "AAAAFF", Boolean.TRUE},
            {MaredLang.get("mared.ui.reload_persistent"), "80", "55FF88", Boolean.TRUE},
            {MaredLang.get("mared.ui.new"),               "60", "FF55FF", Boolean.FALSE}
        };
        Runnable[] actions = {
            ctx::onClose, ctx::onRun, ctx::onSave, ctx::onDelete,
            ctx::onImport, ctx::onSettings, ctx::onReloadPersistent, ctx::onNew
        };

        for (int i = defs.length - 1; i >= 0; i--) {
            boolean onlyCommands = (Boolean) defs[i][3];
            if (onlyCommands && !commandsTab) continue;

            String label = (String) defs[i][0];
            int baseW = Integer.parseInt((String) defs[i][1]);
            int textW = font.width(label);
            int w = Math.max(MaredEditorLayout.px(baseW), textW + MaredEditorLayout.px(16));

            int color = 0xFF000000 | Integer.parseInt((String) defs[i][2], 16);
            right -= w;
            Runnable action = actions[i];
            buttons.add(new MaredCompactButton(
                right, y, w, MaredEditorLayout.px(18),
                Component.literal(label), color, action));
            right -= MaredEditorLayout.px(4);
        }
    }

    public interface ScreenContext {
        int screenW();
        String openTab();
        void onClose();
        void onRun();
        void onSave();
        void onDelete();
        void onImport();
        void onSettings();
        void onReloadPersistent();
        void onNew();
    }
}