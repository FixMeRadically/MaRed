package com.fixmer.mared.gui2.welcome.render.environment.core;

import com.fixmer.mared.gui2.modules.ModuleId;

public final class CoreMemory {

    private CoreMemory() {}

    private static volatile ModuleId lastModule = null;

    public static void remember(ModuleId module) {
        if (module != null) lastModule = module;
    }

    public static ModuleId lastModule() {
        return lastModule;
    }

    public static boolean hasMemory() {
        return lastModule != null;
    }

    public static void clear() {
        lastModule = null;
    }
}