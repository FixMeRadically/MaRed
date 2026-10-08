package com.fixmer.mared.gui2.welcome.render.environment.core;

/**
 * Genesis: РІСЂРµРјРµРЅРЅС‹Рµ С„СѓРЅРєС†РёРё Genesis Core.
 *
 * Р’СЃРµ РјРµС‚РѕРґС‹ - static, РІС…РѕРґ - time.seconds(). РќРёРєР°РєРѕРіРѕ СЃРѕСЃС‚РѕСЏРЅРёСЏ.
 *
 * Р’С‹РЅРµСЃРµРЅРѕ РѕС‚РґРµР»СЊРЅРѕ, С‡С‚РѕР±С‹ CoreObjectLayer РѕСЃС‚Р°Р»СЃСЏ "С‚РѕРЅРєРёРј"
 * СЃР»РѕРµРј СЂРёСЃРѕРІР°РЅРёСЏ, Р° РїР°СЂР°РјРµС‚СЂС‹ Р°РЅРёРјР°С†РёР№ Р¶РёР»Рё РІ РѕРґРЅРѕРј РјРµСЃС‚Рµ.
 * РљСЂСѓС‚РёС‚СЊ СЌС„С„РµРєС‚С‹ Р±СѓРґРµРј Р·РґРµСЃСЊ, РЅРµ РїРµСЂРµРїРёСЃС‹РІР°СЏ Р»РѕРіРёРєСѓ СЂРёСЃРѕРІР°РЅРёСЏ.
 */
public final class CoreAnimation {

    private CoreAnimation() {}

    // ------------------------------------------------------------
    //  Depth breathing
    // ------------------------------------------------------------

    private static final float BREATH_PERIOD = 2.0f;
    private static final float BREATH_AMP    = 0.015f;

    /**
     * РћС‡РµРЅСЊ СЃР»Р°Р±С‹Р№ scale. 1.0 В± 0.015. РњРѕР·Рі Р·Р°РјРµС‡Р°РµС‚, РіР»Р°Р· РЅРµ
     * СЂР°Р·РґСЂР°Р¶Р°РµС‚СЃСЏ. РџРµСЂРёРѕРґ 2 СЃРµРє.
     */
    public static float breathingScale(float timeSec) {
        float phase = timeSec * (float)(2.0 * Math.PI) / BREATH_PERIOD;
        return 1f + (float)Math.sin(phase) * BREATH_AMP;
    }

    // ------------------------------------------------------------
    //  Flash (РєРѕСЂРѕС‚РєРёР№, СЂРµРґРєРёР№)
    // ------------------------------------------------------------

    private static final float FLASH_PERIOD  = 4.0f;
    private static final float FLASH_DURATION = 0.3f;

    /**
     * РРјРїСѓР»СЊСЃ СЂР°Р· РІ 4 СЃРµРєСѓРЅРґС‹. Р’РѕР·РІСЂР°С‰Р°РµС‚ 0 РІРЅРµ РѕРєРЅР° flash Рё
     * РїР»Р°РІРЅСѓСЋ РєРѕР»РѕРєРѕР»РѕРѕР±СЂР°Р·РЅСѓСЋ РєСЂРёРІСѓСЋ РІРЅСѓС‚СЂРё.
     */
    public static float flashPulse(float timeSec) {
        float local = timeSec % FLASH_PERIOD;
        if (local >= FLASH_DURATION) return 0f;
        float t = local / FLASH_DURATION;
        // РџР»Р°РІРЅРѕРµ: 0 -> 1 -> 0
        return (float)Math.sin(t * Math.PI);
    }

    /**
     * РС‚РѕРіРѕРІС‹Р№ СЂР°РґРёСѓСЃ inner energy СЃ СѓС‡С‘С‚РѕРј breath + flash.
     * Р’ РѕР±С‹С‡РЅРѕРµ РІСЂРµРјСЏ: base В± 15%. Р’Рѕ РІСЂРµРјСЏ flash: РґРѕ 1.4 * base.
     */
    public static float innerEnergyRadius(float baseRadius, float timeSec) {
        float breath = 1f
            + 0.15f * (float)Math.sin(
                timeSec * (float)(2.0 * Math.PI) / 1.5f);

        float flash = flashPulse(timeSec);

        return baseRadius * breath * (1f + flash * 0.40f);
    }

    /**
     * РђР»СЊС„Р° inner energy: РІРѕ РІСЂРµРјСЏ flash РґРѕ 255, РѕР±С‹С‡РЅРѕ 180.
     */
    public static int innerEnergyAlpha(float timeSec) {
        float flash = flashPulse(timeSec);
        int base = 180;
        return (int)(base + (255 - base) * flash);
    }

    // ------------------------------------------------------------
    //  Core Shadow
    // ------------------------------------------------------------

    private static final float SHADOW_PERIOD = 1.5f;
    private static final int   SHADOW_BASE_ALPHA = 0x40;
    private static final int   SHADOW_AMP        = 0x10;

    public static int coreShadowAlpha(float timeSec) {
        float phase = timeSec * (float)(2.0 * Math.PI) / SHADOW_PERIOD;
        return SHADOW_BASE_ALPHA
             + (int)((float)Math.sin(phase) * SHADOW_AMP);
    }

    // ------------------------------------------------------------
    //  Energy veins
    // ------------------------------------------------------------

    /**
     * РўРѕРЅРєРёРµ Р»СѓС‡Рё РѕС‚ С†РµРЅС‚СЂР° Рє РєСЂР°СЋ. РћР±С‰Р°СЏ Р°Р»СЊС„Р° РїСѓР»СЊСЃРёСЂСѓРµС‚.
     */
    public static int energyVeinAlpha(float timeSec) {
        float phase = timeSec * (float)(2.0 * Math.PI) / 3.2f;
        return 28 + (int)((float)Math.sin(phase) * 8f);
    }
}