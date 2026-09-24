package com.fixmer.mared.gui.editor;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui.common.MaredCompactButton;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public final class MaredEditorToolbar {

    private final List<AbstractWidget> buttons = new ArrayList<>(8);

    public MaredEditorToolbar() {}

    public List<AbstractWidget> buttons() { return buttons; }
    public void clear() { buttons.clear(); }

    /**
     * Определение кнопки. Статический массив — не создаётся при каждом build.
     *
     * Порядок в массиве — СПРАВА НАЛЕВО (первая прижата к правому краю).
     * Итоговый порядок СЛЕВА НАПРАВО:
     *   Close | Delete | Settings | Reload | Import | New | Save | Run
     */
    private record ButtonDef(
        String labelKey,
        int baseW,
        int colorRgb,
        boolean onlyCommands,
        int action   // switch-код
    ) {}

    private static final int A_RUN              = 0;
    private static final int A_SAVE             = 1;
    private static final int A_NEW              = 2;
    private static final int A_IMPORT           = 3;
    private static final int A_RELOAD_PERSISTENT= 4;
    private static final int A_SETTINGS         = 5;
    private static final int A_DELETE           = 6;
    private static final int A_CLOSE            = 7;

    private static final ButtonDef[] DEFS = {
        new ButtonDef("mared.ui.run",               50, 0x55FF55, false, A_RUN),
        new ButtonDef("mared.ui.save",              70, 0x55AAFF, false, A_SAVE),
        new ButtonDef("mared.ui.new",               60, 0xFF55FF, false, A_NEW),
        new ButtonDef("mared.ui.import",            70, 0xFFAA00, false, A_IMPORT),
        new ButtonDef("mared.ui.reload_persistent", 80, 0x55FF88, true,  A_RELOAD_PERSISTENT),
        new ButtonDef("mared.ui.settings",          80, 0xAAAAFF, true,  A_SETTINGS),
        new ButtonDef("mared.ui.delete",            70, 0xFF4444, false, A_DELETE),
        new ButtonDef("mared.ui.close",             60, 0xFF5555, false, A_CLOSE),
    };

    public void build(ScreenContext ctx, Font font) {
        clear();

        int pad = MaredEditorLayout.PAD();
        int y = pad + 1;
        int right = ctx.screenW() - pad;
        int btnH = MaredEditorLayout.px(18);
        boolean commandsTab = "commands".equals(ctx.openTab());

        for (ButtonDef def : DEFS) {
            if (def.onlyCommands() && !commandsTab) continue;

            String label = MaredLang.get(def.labelKey());
            int textW = font.width(label);
            int w = Math.max(MaredEditorLayout.px(def.baseW()), textW + MaredEditorLayout.px(16));

            int color = 0xFF000000 | def.colorRgb();
            right -= w;

            Runnable action = switch (def.action()) {
                case A_RUN               -> ctx::onRun;
                case A_SAVE              -> ctx::onSave;
                case A_NEW               -> ctx::onNew;
                case A_IMPORT            -> ctx::onImport;
                case A_RELOAD_PERSISTENT -> ctx::onReloadPersistent;
                case A_SETTINGS          -> ctx::onSettings;
                case A_DELETE            -> ctx::onDelete;
                default                  -> ctx::onClose;
            };

            buttons.add(new MaredCompactButton(right, y, w, btnH,
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