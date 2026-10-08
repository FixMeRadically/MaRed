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
    private static final Map<Integer, java.util.IdentityHashMap<Object,String>> CLAIMS = new HashMap<>();
    public static synchronized void claim(int code,Object owner,String name) {
        if(code == GLFW.GLFW_KEY_ESCAPE || code == Integer.MIN_VALUE) return;
        CLAIMS.computeIfAbsent(code,k->new java.util.IdentityHashMap<>()).put(owner,name);
    }
    public static synchronized void release(int code,Object owner) {
        var claims=CLAIMS.get(code);if(claims==null)return;claims.remove(owner);if(claims.isEmpty())CLAIMS.remove(code);
    }

    /**
     * FIX 0.2.5+: кэш KeyMapping по keyCode.
     * Строится один раз, потом только читается. KeyMappings в mc.options
     * не меняются на лету, так что кэш живёт до clear().
     */
    private static final Map<Integer, KeyMapping> MAPPING_CACHE = new HashMap<>(128);

    // ============================================================
    //  Блокировка
    // ============================================================

    public static synchronized void block(int glfwKey, String displayName) {
        if (glfwKey == GLFW.GLFW_KEY_ESCAPE) return;

        // Store intent only; native client ticks apply the block.

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

    public static synchronized void unblock(int glfwKey) {
        BLOCKED_KEYS.remove(glfwKey);

        if (glfwKey == MaredKeyNames.MOUSE_MOVE) {
            unblockMouseMotion();
        }
    }

    public static synchronized boolean isBlocked(int glfwKey) {
        return BLOCKED_KEYS.containsKey(glfwKey) || CLAIMS.containsKey(glfwKey);
    }

    // ============================================================
    //  Мышь — движение
    // ============================================================

    public static synchronized void blockMouseMotion()   { mouseMotionBlocked = true; }
    public static synchronized void unblockMouseMotion() { mouseMotionBlocked = false; }
    public static synchronized boolean isMouseMotionBlocked() { return mouseMotionBlocked || CLAIMS.containsKey(MaredKeyNames.MOUSE_MOVE); }

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

    public static synchronized void clear() {
        BLOCKED_KEYS.clear();
        CLAIMS.clear();
        ORIGINAL_MAPPINGS.clear();
        MAPPING_CACHE.clear();
        mouseMotionBlocked = false;
    }

    // ============================================================
    //  Tick — жёсткий сброс клавиатуры
    // ============================================================

    public static synchronized void tick() {
        if (BLOCKED_KEYS.isEmpty() && CLAIMS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (mc.screen != null) return;

        var codes=new java.util.HashSet<>(BLOCKED_KEYS.keySet());
        codes.addAll(CLAIMS.keySet());
        for (int keyCode : codes) {
            if (keyCode < 0) continue;

            // A physical key can belong to several vanilla/mod mappings, and can be rebound.
            if(mc.options.keyMappings==null)continue;
            for(KeyMapping mapping:mc.options.keyMappings){
                if(mapping==null||!mapping.matches(keyCode,0))continue;
                mapping.setDown(false);
                while(mapping.consumeClick()) { /* drain queued clicks */ }
            }
        }
    }

    // ============================================================
    //  Поиск KeyMapping с кэшем
    // ============================================================

    private static KeyMapping getMappingCached(Minecraft mc, int glfwKey) {
        KeyMapping cached = MAPPING_CACHE.get(glfwKey);
        if (cached != null && cached.matches(glfwKey,0)) return cached;

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
    public static synchronized KeyMapping findMapping(Minecraft mc, int glfwKey) {
        if (mc.options == null || mc.options.keyMappings == null) return null;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) continue;
            if (mapping.matches(glfwKey,0)) return mapping;
        }
        return null;
    }

    /**
     * FIX 0.2.5+: сбросить кэш KeyMapping.
     * Вызывать, если игрок сменил раскладку/привязки в настройках.
     */
    public static synchronized void invalidateMappingCache() {
        MAPPING_CACHE.clear();
    }
}