package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;

public class MaredScriptRunner {

    private static final List<MaredScriptExecutor> ACTIVE = new ArrayList<>();

    public static void start(MaredScriptExecutor executor) {
        synchronized (ACTIVE) {
            ACTIVE.add(executor);
        }
    }

    public static void stopAll() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
    }

    public static void tick(MinecraftServer server) {
        // Снимаем копию, чтобы не было ConcurrentModificationException.
        List<MaredScriptExecutor> snapshot;
        synchronized (ACTIVE) {
            snapshot = new ArrayList<>(ACTIVE);
        }

        List<MaredScriptExecutor> toRemove = new ArrayList<>();

        for (MaredScriptExecutor executor : snapshot) {
            try {
                executor.tick();
            } catch (Exception e) {
                executor.getContext().log("[error] " + e.getMessage());
                toRemove.add(executor);
                continue;
            }
            if (executor.isFinished()) {
                toRemove.add(executor);
            }
        }

        if (!toRemove.isEmpty()) {
            synchronized (ACTIVE) {
                ACTIVE.removeAll(toRemove);
            }
        }
    }

    public static int getActiveCount() {
        synchronized (ACTIVE) {
            return ACTIVE.size();
        }
    }
}