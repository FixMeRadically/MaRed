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
    STUDIO,
    MODS, DATAPACKS, MAPS, SERVERS, SHADERS, MODPACKS, RESOURCEPACKS;

    public static SpaceId forDirection145(com.fixmer.genesis.technology.creation.CreationCatalog145.Direction direction){return valueOf(direction.name());}
    public com.fixmer.genesis.technology.creation.CreationCatalog145.Direction direction145(){
        return switch(this){
            case GENESIS, LOGIC, STUDIO -> null;
            case CONTENT, MODS -> com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.MODS;
            case WORLD, SCENARIOS, MAPS -> com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.MAPS;
            case TOOLS, SERVERS -> com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.SERVERS;
            case RESOURCES, RESOURCEPACKS -> com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.RESOURCEPACKS;
            case DATAPACKS -> com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.DATAPACKS;
            case SHADERS -> com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.SHADERS;
            case MODPACKS -> com.fixmer.genesis.technology.creation.CreationCatalog145.Direction.MODPACKS;
        };
    }
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static SpaceId fromId(String s) {
        if (s == null) return GENESIS;
        try { return valueOf(s.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return GENESIS; }
    }
}