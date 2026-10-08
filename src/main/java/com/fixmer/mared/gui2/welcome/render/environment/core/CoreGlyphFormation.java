package com.fixmer.mared.gui2.welcome.render.environment.core;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.5.37.4.2 Cinematic Pass.
 *
 * РћРґРЅРѕ СЃР»РѕРІРѕ Р·Р° С†РёРєР». РќРµ СЃРїРёСЃРѕРє. Р”РµСЂР¶РёС‚СЃСЏ ~4 СЃРµРєСѓРЅРґС‹ РІРЅСѓС‚СЂРё
 * PARSING. РџРѕСЏРІР»РµРЅРёРµ С‡РµСЂРµР· СЃРїСѓС‚РЅРёРєРё: 2 С‚РѕС‡РєРё РЅР° РєР°Р¶РґСѓСЋ Р±СѓРєРІСѓ
 * Р»РµС‚СЏС‚ СЃ РѕСЂР±РёС‚С‹ R*0.55 Рє С†РµР»РµРІРѕР№ РїРѕР·РёС†РёРё Рё СЂР°СЃС‚РІРѕСЂСЏСЋС‚СЃСЏ С‚Р°Рј
 * (Р±СѓРєРІР° "РјР°С‚РµСЂРёР°Р»РёР·СѓРµС‚СЃСЏ" РёР· РґР°РЅРЅС‹С…).
 *
 * Trails СѓР±СЂР°РЅС‹ - РѕРЅРё РІС‹РіР»СЏРґРµР»Рё РєР°Рє "СЃС…РµРјР° РёР· After Effects".
 */
public final class CoreGlyphFormation {

    private static final String[] WORDS = {
        "entity", "model", "texture", "world", "npc"
    };

    /** 2 СЃРїСѓС‚РЅРёРєР° РЅР° Р±СѓРєРІСѓ. */
    private static final int SATELLITES_PER_CHAR = 2;

    /**
     * @param textRgb RGB (Р±РµР· alpha) С…РѕР»РѕРґРЅРѕРіРѕ СЃС‚СЂСѓРєС‚СѓСЂРЅРѕРіРѕ С‚РѕРЅР°
     */
    public void render(GuiGraphics g,
                       Font font,
                       int cx, int cy,
                       float R,
                       float timeSec,
                       CoreState state,
                       int textRgb) {

        if (state != CoreState.PARSING) return;

        float p = CoreStateMachine.progressAt(timeSec);

        // Р¤РѕСЂРјРёСЂРѕРІР°РЅРёРµ: 0.10-0.35, РґРµСЂР¶РёС‚СЃСЏ 0.35-0.75, СЂР°СЃС‚РІРѕСЂСЏРµС‚СЃСЏ 0.75-1.0
        float appear, hold, disappear;
        if (p < 0.10f) {
            appear = 0f; hold = 0f; disappear = 0f;
        } else if (p < 0.35f) {
            appear = CoreGeometry.smoothstep((p - 0.10f) / 0.25f);
            hold = appear; disappear = 0f;
        } else if (p < 0.75f) {
            appear = 1f; hold = 1f; disappear = 0f;
        } else {
            appear = 1f; hold = 1f;
            disappear = CoreGeometry.smoothstep((p - 0.75f) / 0.25f);
        }

        float overallAlpha = (1f - disappear);
        if (overallAlpha <= 0.02f) return;

        long cycle = (long)(timeSec / CoreStateMachine.TOTAL_CYCLE_SEC);
        String word = WORDS[(int)(cycle % WORDS.length)];
        int n = word.length();
        if (n == 0) return;

        int totalW = font.width(word);
        int startX = cx - totalW / 2;
        int baseY  = cy - 4;

        // --- РЎРїСѓС‚РЅРёРєРё-С‚РѕС‡РєРё ---
        // 2 С‚РѕС‡РєРё РЅР° РєР°Р¶РґСѓСЋ Р±СѓРєРІСѓ, СЃС‚Р°СЂС‚СѓСЋС‚ СЃ РѕСЂР±РёС‚С‹ R*0.55, Р»РµС‚СЏС‚ Рє Р±СѓРєРІРµ.
        int satAlpha = (int)(220 * appear * overallAlpha);
        if (satAlpha > 20) {
            for (int i = 0; i < n; i++) {
                int tx = startX + font.width(word.substring(0, i)) + 2;
                int ty = baseY + 4;

                for (int k = 0; k < SATELLITES_PER_CHAR; k++) {
                    float angle = i * 2.399f + k * 1.2f;
                    float scatterR = R * 0.55f;
                    int sx = cx + (int)(Math.cos(angle) * scatterR);
                    int sy = cy + (int)(Math.sin(angle) * scatterR);

                    int x = (int)(sx + (tx - sx) * appear);
                    int y = (int)(sy + (ty - sy) * appear);

                    int a = (int)(satAlpha * (1f - appear * 0.7f));
                    if (a <= 10) continue;

                    int color = (a << 24) | (textRgb & 0x00FFFFFF);
                    g.fill(x - 1, y - 1, x + 1, y + 1, color);
                }
            }
        }

        // --- Р‘СѓРєРІС‹ ---
        int textAlpha = (int)(240 * hold * overallAlpha);
        if (textAlpha > 20) {
            int color = (textAlpha << 24) | (textRgb & 0x00FFFFFF);
            g.drawString(font, word, startX, baseY, color, false);
        }
    }
}