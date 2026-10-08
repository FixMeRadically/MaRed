package com.fixmer.mared.gui2.welcome.render.environment;

/**
 * 1.5.37.1A: РµРґРёРЅС‹Рµ С‡Р°СЃС‹ СЃСЂРµРґС‹ Welcome.
 *
 * РћРґРЅРѕ РіР»РѕР±Р°Р»СЊРЅРѕРµ РІСЂРµРјСЏ РЅР° РІСЃСЋ Environment. РЎР»РѕРё РќР• С…СЂР°РЅСЏС‚ СЃРІРѕС‘
 * РІСЂРµРјСЏ - РѕРЅРё С‡РёС‚Р°СЋС‚ time.seconds() РІ render Рё СЃС‡РёС‚Р°СЋС‚ РґРІРёР¶РµРЅРёРµ
 * РёР· РЅРµРіРѕ. РўР°Рє СЃР»РѕРё РѕСЃС‚Р°СЋС‚СЃСЏ stateless: РґРІР° РІС‹Р·РѕРІР° render() РїСЂРё
 * РѕРґРЅРѕРј tick() РґР°РґСѓС‚ РѕРґРёРЅР°РєРѕРІСѓСЋ РєР°СЂС‚РёРЅРєСѓ.
 *
 * tick() РІС‹Р·С‹РІР°РµС‚СЃСЏ РёР· WelcomeEnvironment.tick(), РєРѕС‚РѕСЂС‹Р№ СЃР°Рј
 * РґС‘СЂРіР°РµС‚СЃСЏ РёР· WelcomeHeroRenderer.tick(). Р§Р°СЃС‚РѕС‚Р° ~60 Hz
 * (0.016 СЃ/С‚РёРє) - РєР°Рє Сѓ Minecraft client tick.
 */
public final class EnvironmentTime {

    private static final float SECONDS_PER_TICK = 0.016f;

    private float seconds;
    private long  frames;

    public void tick() {
        frames++;
        seconds += SECONDS_PER_TICK;
        if (seconds > 1000f) seconds -= 1000f;
    }

    public float seconds() { return seconds; }
    public long  frames()  { return frames; }

    /** sin(seconds * speed). */
    public float sin(float speed) {
        return (float) Math.sin(seconds * speed);
    }

    /** cos(seconds * speed). */
    public float cos(float speed) {
        return (float) Math.cos(seconds * speed);
    }

    /** sin РІ РґРёР°РїР°Р·РѕРЅРµ 0..1 (РґР»СЏ РїСѓР»СЊСЃР°С†РёР№). */
    public float pulse(float speed) {
        return (float) Math.sin(seconds * speed) * 0.5f + 0.5f;
    }
}