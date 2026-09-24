package com.fixmer.mared.commands.engine;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.fixmer.mared.commands.events.MaredEventRegistry;

public final class MaredStateTracker {

    private MaredStateTracker() {}

    public static class Diff {
        public boolean hasChanges = false;
        public final Map<String, Object> data = new HashMap<>(8);

        void put(String k, Object v) {
            data.put(k, v);
            hasChanges = true;
        }

        void reset() {
            hasChanges = false;
            data.clear();
        }
    }

    private static final ThreadLocal<Diff> DIFF = ThreadLocal.withInitial(Diff::new);

    // ============================================================
    //  Cooldown per event type
    // ============================================================

    /** Минимальный интервал между событиями одного типа (в ms). */
    private static final long PLAYER_MOVE_COOLDOWN_MS = 250;  // 4 раза/сек
    private static final long STATE_COOLDOWN_MS = 100;        // 10 раз/сек

    private static long lastPlayerMoveMs = 0;
    private static long lastStateMs = 0;

    // ============================================================
    //  Пороги
    // ============================================================

    private static final double MOVE_THRESHOLD = 0.1;

    // ============================================================
    //  Listener version cache
    // ============================================================

    private static final String[] STATE_EVENTS = {
        "player_move", "health_change", "hunger_change",
        "xp_change", "item_drop", "gamemode_change"
    };

    private static volatile int listenerVersion = -1;
    private static volatile boolean anyListeners = false;

    public static boolean hasAnyStateListeners() {
        int ver = MaredEventRegistry.getVersion();
        if (ver != listenerVersion) {
            boolean any = false;
            for (String e : STATE_EVENTS) {
                if (MaredEventRegistry.has(e)) { any = true; break; }
            }
            anyListeners = any;
            listenerVersion = ver;
        }
        return anyListeners;
    }

    // ============================================================
    //  Предыдущее состояние
    // ============================================================

    private static double lastX = 0, lastY = 0, lastZ = 0;
    private static boolean posInit = false;
    private static int lastHp = -1;
    private static int lastFood = -1;
    private static int lastXpLevel = -1;
    private static int lastXpTotal = -1;
    private static int lastGamemode = -1;
    private static int lastHeldCount = -1;
    private static int lastSelectedSlot = -1;

    // ============================================================
    //  Poll
    // ============================================================

    public static Diff poll() {
        Diff diff = DIFF.get();
        diff.reset();

        if (!hasAnyStateListeners()) return diff;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return diff;

        long now = System.currentTimeMillis();

        // ---- Позиция ----
        double x = p.getX(), y = p.getY(), z = p.getZ();
        if (posInit) {
            double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
            if (dx > MOVE_THRESHOLD || dx < -MOVE_THRESHOLD
                || dy > MOVE_THRESHOLD || dy < -MOVE_THRESHOLD
                || dz > MOVE_THRESHOLD || dz < -MOVE_THRESHOLD) {

                // Cooldown для player_move
                if (now - lastPlayerMoveMs >= PLAYER_MOVE_COOLDOWN_MS) {
                    lastPlayerMoveMs = now;

                    diff.put("from_x", (int) lastX);
                    diff.put("from_y", (int) lastY);
                    diff.put("from_z", (int) lastZ);
                    diff.put("x", (int) x);
                    diff.put("y", (int) y);
                    diff.put("z", (int) z);
                    diff.put("dx", (int) dx);
                    diff.put("dy", (int) dy);
                    diff.put("dz", (int) dz);
                }
            }
        }
        lastX = x; lastY = y; lastZ = z; posInit = true;

        // ---- HP ----
        int hp = (int) p.getHealth();
        if (lastHp >= 0 && hp != lastHp) {
            diff.put("old_hp", lastHp);
            diff.put("new_hp", hp);
            diff.put("delta", hp - lastHp);
        }
        lastHp = hp;

        // ---- Food ----
        int food = p.getFoodData().getFoodLevel();
        if (lastFood >= 0 && food != lastFood) {
            diff.put("old_food", lastFood);
            diff.put("new_food", food);
            diff.put("food_delta", food - lastFood);
        }
        lastFood = food;

        // ---- XP ----
        int xpLvl = p.experienceLevel;
        int xpTot = p.totalExperience;
        if (lastXpLevel >= 0 && xpLvl != lastXpLevel) {
            diff.put("old_xp_level", lastXpLevel);
            diff.put("new_xp_level", xpLvl);
        }
        if (lastXpTotal >= 0 && xpTot != lastXpTotal) {
            diff.put("old_xp", lastXpTotal);
            diff.put("new_xp", xpTot);
            diff.put("xp_delta", xpTot - lastXpTotal);
        }
        lastXpLevel = xpLvl;
        lastXpTotal = xpTot;

        // ---- Gamemode ----
        int gm = -1;
        try {
            if (mc.gameMode != null) {
                gm = mc.gameMode.getPlayerMode().getId();
            }
        } catch (Throwable ignored) {}
        if (lastGamemode >= 0 && gm != lastGamemode && gm >= 0) {
            diff.put("old_gamemode", gamemodeName(lastGamemode));
            diff.put("new_gamemode", gamemodeName(gm));
        }
        lastGamemode = gm;

        // ---- Held count ----
        int heldCount = p.getMainHandItem().getCount();
        if (lastHeldCount >= 0 && heldCount != lastHeldCount && heldCount < lastHeldCount) {
            diff.put("old_count", lastHeldCount);
            diff.put("new_count", heldCount);
            diff.put("dropped", lastHeldCount - heldCount);
        }
        lastHeldCount = heldCount;

        // ---- Selected slot ----
        int slot = p.getInventory().selected;
        if (lastSelectedSlot >= 0 && slot != lastSelectedSlot) {
            diff.put("from_slot", lastSelectedSlot);
            diff.put("to_slot", slot);
        }
        lastSelectedSlot = slot;

        return diff;
    }

    public static void reset() {
        posInit = false;
        lastHp = -1;
        lastFood = -1;
        lastXpLevel = -1;
        lastXpTotal = -1;
        lastGamemode = -1;
        lastHeldCount = -1;
        lastSelectedSlot = -1;
        lastPlayerMoveMs = 0;
        lastStateMs = 0;
    }

    private static String gamemodeName(int id) {
        switch (id) {
            case 0: return "survival";
            case 1: return "creative";
            case 2: return "adventure";
            case 3: return "spectator";
            default: return "unknown";
        }
    }
}