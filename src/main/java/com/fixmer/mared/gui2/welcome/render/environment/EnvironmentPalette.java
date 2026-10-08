package com.fixmer.mared.gui2.welcome.render.environment;

import com.fixmer.mared.gui2.framework.render.MaredColor;

/**
 * 1.5.37.1A: С†РІРµС‚Р° РІСЃРµР№ СЃСЂРµРґС‹ Welcome.
 *
 * Р Р°РЅСЊС€Рµ Р±С‹Р» BackgroundPalette (3 С†РІРµС‚Р° РіСЂР°РґРёРµРЅС‚Р° + glow).
 * РўРµРїРµСЂСЊ СЌС‚Рѕ EnvironmentPalette - РѕР±С‰Р°СЏ РїР°Р»РёС‚СЂР° РґР»СЏ РІСЃРµС… СЃР»РѕС‘РІ:
 * sky, glow, accent. РџРѕ РјРµСЂРµ РґРѕР±Р°РІР»РµРЅРёСЏ Nebula / StarField /
 * LightBeam СЃСЋРґР° Р±СѓРґСѓС‚ РґРѕР±Р°РІР»СЏС‚СЊСЃСЏ СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓСЋС‰РёРµ РїРѕР»СЏ.
 *
 * fromAccent() - РµРґРёРЅСЃС‚РІРµРЅРЅС‹Р№ РїСѓР±Р»РёС‡РЅС‹Р№ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂ. РџР°Р»РёС‚СЂР°
 * РІСЃРµРіРґР° СЃС‚СЂРѕРёС‚СЃСЏ РёР· accent-С†РІРµС‚Р° С‚РµРєСѓС‰РµРіРѕ РјРѕРґСѓР»СЏ. РљР»РёРє РїРѕ
 * Content (Р·РµР»С‘РЅС‹Р№) - sky Р·РµР»РµРЅРµРµС‚, glow Р·РµР»С‘РЅС‹Р№. РљР»РёРє РїРѕ Logic
 * (С„РёРѕР»РµС‚РѕРІС‹Р№) - sky С„РёРѕР»РµС‚РѕРІРµРµС‚.
 *
 * РљРѕСЌС„С„РёС†РёРµРЅС‚С‹ РїРѕРґРјРµС€РёРІР°РЅРёСЏ РїРѕРґРѕР±СЂР°РЅС‹ С‚Р°Рє, С‡С‚РѕР±С‹ СЂР°Р·РЅРёС†Р° Р±С‹Р»Р°
 * Р·Р°РјРµС‚РЅР°, РЅРѕ РЅРµ РєСЂРёС‡Р°Р»Р°. Р•СЃР»Рё СЃР»Р°Р±Рѕ - РїРѕРґРЅРёРјР°РµРј.
 */
public final class EnvironmentPalette {

    public final int skyTop;
    public final int skyMiddle;
    public final int skyBottom;
    public final int glow;
    public final int accent;

    private EnvironmentPalette(int skyTop, int skyMiddle,
                               int skyBottom, int glow, int accent) {
        this.skyTop    = skyTop;
        this.skyMiddle = skyMiddle;
        this.skyBottom = skyBottom;
        this.glow      = glow;
        this.accent    = accent;
    }

    public static EnvironmentPalette defaultPalette() {
        return fromAccent(0xFF55AAFF);
    }

    public static EnvironmentPalette fromAccent(int accent) {
        int a = 0xFF000000 | (accent & 0x00FFFFFF);

        int skyTop    = MaredColor.lerpColor(0xFF0A0A14, a, 0.08f);
        int skyMiddle = MaredColor.lerpColor(0xFF14142A, a, 0.22f);
        int skyBottom = MaredColor.lerpColor(0xFF030308, a, 0.04f);
        int glow      = a;

        return new EnvironmentPalette(skyTop, skyMiddle, skyBottom, glow, a);
    }
}