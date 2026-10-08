package com.fixmer.mared.gui2.welcome.render.environment.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Genesis: СЃРµРјР°РЅС‚РёС‡РµСЃРєРёРµ СЃРІСЏР·Рё РјРµР¶РґСѓ KEYWORD Рё VALUE.
 *
 * entity  -> zombie, player, spider
 * texture -> grass, stone, oak
 * world   -> air, water, stone
 * ...
 *
 * Data System РёСЃРїРѕР»СЊР·СѓРµС‚ СЌС‚Рё СЃРІСЏР·Рё РґР»СЏ:
 *   - РїСЂРёРѕСЂРёС‚РµС‚РЅС‹С… Data Streams (Р»РёРЅРёСЏ + Р±РµРіСѓС‰РёР№ РјР°СЂРєРµСЂ);
 *   - РѕС‰СѓС‰РµРЅРёСЏ "СЃРёСЃС‚РµРјР° РїРѕРЅРёРјР°РµС‚ РєРѕРґ", Р° РЅРµ СЂРёСЃСѓРµС‚ СЃР»СѓС‡Р°Р№РЅС‹Рµ Р»РёРЅРёРё.
 */
public final class CoreDataRelations {

    private CoreDataRelations() {}

    private static final Map<String, Set<String>> MAP = new HashMap<>(24);

    static {
        add("entity",   "zombie", "player", "spider");
        add("model",    "sword", "village");
        add("texture",  "grass", "stone", "oak");
        add("behavior", "true", "false");
        add("event",    "tick", "spawn");      // tick/spawn РєР°Рє Р·РЅР°С‡РµРЅРёСЏ РЅРµ РІ С‚Р°Р±Р»РёС†Рµ, РЅРѕ РїРѕРґСЃС‚СЂР°С…РѕРІРєР°
        add("world",    "air", "water", "stone");
        add("resource", "diamond", "stone", "oak");
        add("logic",    "true", "false");
        add("spawn",    "zombie", "player", "spider");
        add("tick",     "true", "false");
        add("block",    "stone", "oak", "grass", "air", "water");
        add("data",     "0.3", "0.8");
        add("json",     "true", "false");
        add("npc",      "player", "village");
        add("quest",    "village");
    }

    private static void add(String keyword, String... values) {
        Set<String> set = new HashSet<>(values.length * 2);
        for (String v : values) set.add(v);
        MAP.put(keyword, set);
    }

    public static boolean related(String keyword, String value) {
        if (keyword == null || value == null) return false;
        Set<String> set = MAP.get(keyword);
        return set != null && set.contains(value);
    }
}