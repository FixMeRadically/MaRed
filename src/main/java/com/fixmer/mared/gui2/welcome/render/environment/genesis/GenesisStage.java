package com.fixmer.mared.gui2.welcome.render.environment.genesis;

/**
 * Genesis: СЃС‚Р°РґРёРё Genesis Engine.
 *
 * 7 СЃС‚Р°РґРёР№ Birth Sequence. РџРѕР»РЅС‹Р№ СЂРµР¶РёРј ~21 СЃРµРєСѓРЅРґР°,
 * fast mode (РїСЂРё РїРѕРІС‚РѕСЂРЅРѕРј РѕС‚РєСЂС‹С‚РёРё) ~4 СЃРµРєСѓРЅРґС‹.
 *
 * LIVING - Р±РµСЃРєРѕРЅРµС‡РЅР°СЏ. Р”Р»РёС‚РµР»СЊРЅРѕСЃС‚СЊ Р·РґРµСЃСЊ - РјР°СЂРєРµСЂ
 * "РЅРµ РёРјРµРµС‚ Р·РЅР°С‡РµРЅРёСЏ", РѕРЅР° РЅРµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІ СЂР°СЃС‡С‘С‚Р°С….
 */
public enum GenesisStage {

    BOOT           (0.8f, 0.2f),
    AWAKENING      (3.2f, 0.7f),
    PARSING        (4.0f, 0.5f),
    SEED           (4.0f, 0.6f),
    CONSTRUCTION   (6.0f, 1.5f),
    STABILIZATION  (3.0f, 0.5f),
    LIVING         (Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

    /** Р”Р»РёС‚РµР»СЊРЅРѕСЃС‚СЊ СЃС‚Р°РґРёРё РІ РїРѕР»РЅРѕРј СЂРµР¶РёРјРµ. */
    public final float fullDurationSec;

    /** Р”Р»РёС‚РµР»СЊРЅРѕСЃС‚СЊ СЃС‚Р°РґРёРё РІ fast mode. */
    public final float fastDurationSec;

    GenesisStage(float full, float fast) {
        this.fullDurationSec = full;
        this.fastDurationSec = fast;
    }

    public float duration(boolean fast) {
        return fast ? fastDurationSec : fullDurationSec;
    }

    public boolean isLast() {
        return this == LIVING;
    }
}