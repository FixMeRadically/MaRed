package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.MinecraftServer;

public class MaredScriptRunner {

    private static final List<MaredScriptExecutor> ACTIVE = new ArrayList<>();

    public static void start(MaredScriptExecutor executor) {
        ACTIVE.add(executor);
    }

    public static void stopAll() {
        ACTIVE.clear();
    }

    public static void tick(MinecraftServer server) {
        Iterator<MaredScriptExecutor> it = ACTIVE.iterator();
        while (it.hasNext()) {
            MaredScriptExecutor executor = it.next();
            try {
                executor.tick();
            } catch (Exception e) {
                executor.getContext().log("[error] " + e.getMessage());
                it.remove();
                continue;
            }
            if (executor.isFinished()) {
                it.remove();
            }
        }
    }

    public static int getActiveCount() {
        return ACTIVE.size();
    }
}