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
 *
 * FIX 0.2.6: новый порядок слева направо:
 *   Close | Delete | Settings | Reload | Import | New | Save | Run
 *
 * Механика: список defs — СТРОГО справа налево (первая кнопка прижата
 * к правому краю). Поэтому Run идёт первым в списке, Close — последним.
 */
public final class MaredEditorToolbar {

    private final List<AbstractWidget> buttons = new ArrayList<>();

    public MaredEditorToolbar() {}

    public List<AbstractWidget> buttons() { return buttons; }
    public void clear() { buttons.clear(); }

    private record ButtonDef(
        String labelKey,
        int baseW,
        int colorRgb,
        boolean onlyCommands,
        Runnable action
    ) {}

    public void build(ScreenContext ctx, Font font) {
        clear();

        int pad = MaredEditorLayout.PAD();
        int y = pad + 1;
        int right = ctx.screenW() - pad;
        boolean commandsTab = "commands".equals(ctx.openTab());

        // Порядок в списке — СПРАВА НАЛЕВО.
        // Итоговый порядок СЛЕВА НАПРАВО:
        //   Close | Delete | Settings | Reload | Import | New | Save | Run
        List<ButtonDef> defs = new ArrayList<>();
        defs.add(new ButtonDef("mared.ui.run",               50, 0x55FF55, false, ctx::onRun));
        defs.add(new ButtonDef("mared.ui.save",              70, 0x55AAFF, false, ctx::onSave));
        defs.add(new ButtonDef("mared.ui.new",               60, 0xFF55FF, false, ctx::onNew));
        defs.add(new ButtonDef("mared.ui.import",            70, 0xFFAA00, false, ctx::onImport));
        defs.add(new ButtonDef("mared.ui.reload_persistent", 80, 0x55FF88, true,  ctx::onReloadPersistent));
        defs.add(new ButtonDef("mared.ui.settings",          80, 0xAAAAFF, true,  ctx::onSettings));
        defs.add(new ButtonDef("mared.ui.delete",            70, 0xFF4444, false, ctx::onDelete));
        defs.add(new ButtonDef("mared.ui.close",             60, 0xFF5555, false, ctx::onClose));

        for (ButtonDef def : defs) {
            if (def.onlyCommands() && !commandsTab) continue;

            String label = MaredLang.get(def.labelKey());
            int textW = font.width(label);
            int w = Math.max(MaredEditorLayout.px(def.baseW()), textW + MaredEditorLayout.px(16));

            int color = 0xFF000000 | def.colorRgb();
            right -= w;
            buttons.add(new MaredCompactButton(
                right, y, w, MaredEditorLayout.px(18),
                Component.literal(label), color, def.action()));
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