package com.fixmer.genesis.technology.minecraft;

/** How an external Minecraft project enters Genesis storage. */
public enum MinecraftImportMode {
    /** Full bounded copy under the Genesis projects root. */
    COPY,
    /** Keep sources at the original path; Genesis stores metadata + link only. */
    LINK
}
