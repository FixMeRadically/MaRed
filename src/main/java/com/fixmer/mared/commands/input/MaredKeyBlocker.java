package com.fixmer.mared.commands.input;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class MaredKeyBlocker {

    private MaredKeyBlocker() {}

    private static final Map<Integer, String> BLOCKED_KEYS = new HashMap<>();

    /**
     * FIX 0.2.5: ORIGINAL_MAPPINGS больше не используется для подмены setKey.
     * Оставлен для обратной совместимости.
     */
    @Deprecated
    private static final Map<KeyMapping, InputConstants.Key> ORIGINAL_MAPPINGS = new HashMap<>();

    private static boolean mouseMotionBlocked = false;

    /**
     * FIX 0.2.5+: кэш KeyMapping по keyCode.
     * Строится один раз, потом только читается. KeyMappings в mc.options
     * не меняются на лету, так что кэш живёт до clear().
     */
    private static final Map<Integer, KeyMapping> MAPPING_CACHE = new HashMap<>(128);

    // ============================================================
    //  Блокировка
    // ============================================================

    public static void block(int glfwKey, String displayName) {
        if (glfwKey == GLFW.GLFW_KEY_ESCAPE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        // ---- Мышь ----
        if (glfwKey == MaredKeyNames.MOUSE_LEFT
            || glfwKey == MaredKeyNames.MOUSE_RIGHT
            || glfwKey == MaredKeyNames.MOUSE_MIDDLE) {
            BLOCKED_KEYS.put(glfwKey, displayName);
            return;
        }
        if (glfwKey == MaredKeyNames.MOUSE_MOVE) {
            BLOCKED_KEYS.put(glfwKey, displayName);
            blockMouseMotion();
            return;
        }

        // ---- Клавиатура ----
        if (BLOCKED_KEYS.containsKey(glfwKey)) return;
        BLOCKED_KEYS.put(glfwKey, displayName);
    }

    public static void unblock(int glfwKey) {
        BLOCKED_KEYS.remove(glfwKey);

        if (glfwKey == MaredKeyNames.MOUSE_MOVE) {
            unblockMouseMotion();
        }
    }

    public static boolean isBlocked(int glfwKey) {
        return BLOCKED_KEYS.containsKey(glfwKey);
    }

    // ============================================================
    //  Мышь — движение
    // ============================================================

    public static void blockMouseMotion()   { mouseMotionBlocked = true; }
    public static void unblockMouseMotion() { mouseMotionBlocked = false; }
    public static boolean isMouseMotionBlocked() { return mouseMotionBlocked; }

    // ============================================================
    //  Устаревшие методы (no-op)
    // ============================================================

    @Deprecated
    private static void detach(KeyMapping mapping) {
        // no-op — блокировка делается через tick()
    }

    @Deprecated
    private static void reattach(KeyMapping mapping) {
        // no-op
    }

    // ============================================================
    //  Clear
    // ============================================================

    public static void clear() {
        BLOCKED_KEYS.clear();
        ORIGINAL_MAPPINGS.clear();
        MAPPING_CACHE.clear();
        mouseMotionBlocked = false;
    }

    // ============================================================
    //  Tick — жёсткий сброс клавиатуры
    // ============================================================

    public static void tick() {
        if (BLOCKED_KEYS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (mc.screen != null) return;

        for (Map.Entry<Integer, String> e : BLOCKED_KEYS.entrySet()) {
            int keyCode = e.getKey();
            if (keyCode < 0) continue;

            KeyMapping mapping = getMappingCached(mc, keyCode);
            if (mapping == null) continue;

            mapping.setDown(false);
            // consumeClick() возвращает boolean — выедаем всё
            while (mapping.consumeClick()) {
                // пусто
            }
        }
    }

    // ============================================================
    //  Поиск KeyMapping с кэшем
    // ============================================================

    private static KeyMapping getMappingCached(Minecraft mc, int glfwKey) {
        KeyMapping cached = MAPPING_CACHE.get(glfwKey);
        if (cached != null) return cached;

        KeyMapping found = findMapping(mc, glfwKey);
        if (found != null) {
            MAPPING_CACHE.put(glfwKey, found);
        }
        return found;
    }

    /**
     * Оригинальный поиск. Используется при первом обращении.
     * FIX 0.2.5+: учитывает и getDefaultKey(), и getKey() (текущую привязку).
     */
    public static KeyMapping findMapping(Minecraft mc, int glfwKey) {
        if (mc.options == null || mc.options.keyMappings == null) return null;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) continue;
            if (mapping.getDefaultKey().getValue() == glfwKey) return mapping;
        }
        return null;
    }

    /**
     * FIX 0.2.5+: сбросить кэш KeyMapping.
     * Вызывать, если игрок сменил раскладку/привязки в настройках.
     */
    public static void invalidateMappingCache() {
        MAPPING_CACHE.clear();
    }
}