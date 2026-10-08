package com.fixmer.mared.gui2.welcome.render.environment.core;

/**
 * Genesis: РѕРґРЅР° РѕСЂР±РёС‚Р° РІРѕРєСЂСѓРі СЏРґСЂР°.
 *
 * РџРµСЂРёРѕРґС‹ 5/8/13 СЃРµРєСѓРЅРґ РІР·СЏС‚С‹ РёР· СЃРїРµС†РёС„РёРєР°С†РёРё: РѕС‚РЅРѕС€РµРЅРёСЏ 8/5 в‰€ 1.6
 * Рё 13/8 в‰€ 1.625 Р±Р»РёР·РєРё Рє Р·РѕР»РѕС‚РѕРјСѓ СЃРµС‡РµРЅРёСЋ, РїРѕСЌС‚РѕРјСѓ РѕСЂР±РёС‚С‹
 * "СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓСЋС‚СЃСЏ" РѕС‡РµРЅСЊ СЂРµРґРєРѕ - СЃС†РµРЅР° РЅРµ РІС‹РіР»СЏРґРёС‚ С†РёРєР»РёС‡РµСЃРєРѕР№.
 *
 * squashY: 1.0 = РіРѕСЂРёР·РѕРЅС‚Р°Р»СЊРЅРѕРµ РєРѕР»СЊС†Рѕ, 0.2 = РїРѕС‡С‚Рё Р»РёРЅРёСЏ.
 */
public record CoreOrbit(
    float radiusRatio,
    float squashY,
    float periodSec,
    int   alpha
) {

    public static final CoreOrbit[] ORBITS = new CoreOrbit[] {
        new CoreOrbit(0.90f, 1.00f,  5.0f, 70),
        new CoreOrbit(1.15f, 0.50f,  8.0f, 50),
        new CoreOrbit(1.40f, 0.20f, 13.0f, 35),
    };

    /** РЈРіР»РѕРІР°СЏ СЃРєРѕСЂРѕСЃС‚СЊ (СЂР°Рґ/СЃРµРє). */
    public float angularSpeed() {
        return (float)(2.0 * Math.PI) / periodSec;
    }
}