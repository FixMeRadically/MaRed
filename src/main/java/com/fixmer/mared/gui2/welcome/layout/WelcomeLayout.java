package com.fixmer.mared.gui2.welcome.layout;

import com.fixmer.mared.gui2.welcome.hero.WelcomeModuleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Р•РґРёРЅСЃС‚РІРµРЅРЅС‹Р№ РёСЃС‚РѕС‡РЅРёРє РіРµРѕРјРµС‚СЂРёРё Hero-РєРЅРѕРїРѕРє.
 *
 * Genesis: mouse, click Рё render Р±РѕР»СЊС€Рµ РЅРµ РґСѓР±Р»РёСЂСѓСЋС‚ startX / buttonY /
 * width / height. Р’СЃРµ РѕРЅРё РїРѕР»СѓС‡Р°СЋС‚ РѕРґРёРЅ Рё С‚РѕС‚ Р¶Рµ СЃРїРёСЃРѕРє ButtonLayout.
 *
 * РќРµ С…СЂР°РЅРёС‚ СЃСЃС‹Р»РєСѓ РЅР° selector Рё РЅРёС‡РµРіРѕ РЅРµ Р·РЅР°РµС‚ РїСЂРѕ Controller.
 * rebuild() РІС‹Р·С‹РІР°РµС‚СЃСЏ РёР· WelcomeHeroController.resize(), РєРѕРіРґР° Screen
 * СЃРѕРѕР±С‰Р°РµС‚ Р°РєС‚СѓР°Р»СЊРЅС‹Р№ СЂР°Р·РјРµСЂ.
 */
public final class WelcomeLayout {

    public static final int BUTTON_WIDTH  = 120;
    public static final int BUTTON_HEIGHT = 40;
    public static final int BUTTON_STEP   = 130;

    private static final int ROW_HALF_WIDTH = 390;
    private static final int ROW_OFFSET_Y   = 100;

    private final List<ButtonLayout> buttons = new ArrayList<>(8);

    private int lastWidth  = -1;
    private int lastHeight = -1;
    private int lastCount  = -1;

    public void rebuild(
            int width,
            int height,
            List<WelcomeModuleButton> modules
    ) {
        int n = modules == null ? 0 : modules.size();

        // РќРёС‡РµРіРѕ РЅРµ РёР·РјРµРЅРёР»РѕСЃСЊ - РЅРµ РїРµСЂРµСЃРѕР±РёСЂР°РµРј.
        if (width == lastWidth && height == lastHeight && n == lastCount) {
            return;
        }
        lastWidth  = width;
        lastHeight = height;
        lastCount  = n;

        buttons.clear();
        if (n == 0) return;

        int startX = width / 2 - ROW_HALF_WIDTH;
        int y      = height / 2 + ROW_OFFSET_Y;

        for (int i = 0; i < n; i++) {
            int x = startX + i * BUTTON_STEP;
            buttons.add(new ButtonLayout(
                    modules.get(i),
                    new WelcomeBounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            ));
        }
    }

    public List<ButtonLayout> buttons() {
        return Collections.unmodifiableList(buttons);
    }

    public int size() {
        return buttons.size();
    }

    public ButtonLayout get(int index) {
        if (index < 0 || index >= buttons.size()) return null;
        return buttons.get(index);
    }
}