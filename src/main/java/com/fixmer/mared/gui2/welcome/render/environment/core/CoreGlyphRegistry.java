package com.fixmer.mared.gui2.welcome.render.environment.core;

import java.util.Random;

public final class CoreGlyphRegistry {

    private CoreGlyphRegistry() {}

    public static final String[] SYMBOLS = {
        "{", "}", "[", "]", "<", ">", "=", ":", "/", "*", "#", "+", "-", ";"
    };

    public static final String[] KEYWORDS = {
        "entity", "model", "texture", "behavior", "event",
        "world", "resource", "logic", "spawn", "tick",
        "block", "data", "json", "npc", "quest"
    };

    public static final String[] VALUES = {
        "zombie", "player", "0.3", "true", "stone", "oak",
        "grass", "diamond", "0.8", "false", "air", "water",
        "sword", "village", "spider"
    };

    public static String pickSymbol(Random rng) {
        return SYMBOLS[rng.nextInt(SYMBOLS.length)];
    }

    public static String pickKeyword(Random rng) {
        return KEYWORDS[rng.nextInt(KEYWORDS.length)];
    }

    public static String pickValue(Random rng) {
        return VALUES[rng.nextInt(VALUES.length)];
    }
}