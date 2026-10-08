package com.fixmer.mared.gui2.navigation;

/**
 * Идентификаторы пространств MaRed.
 *
 * v1: фиксированный список. В будущем может стать строковым id,
 * чтобы плагины могли добавлять свои пространства без правок enum.
 * Пока оставляем enum — это даёт compile-time проверки в SpaceGraph.
 */
public enum SpaceId {
    GENESIS,
    CONTENT,
    WORLD,
    LOGIC,
    RESOURCES,
    TOOLS,
    SCENARIOS,
    STUDIO;

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static SpaceId fromId(String s) {
        if (s == null) return GENESIS;
        try { return valueOf(s.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return GENESIS; }
    }
}