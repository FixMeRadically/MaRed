package com.fixmer.mared.script;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class MaredKeyBlocker {

    private MaredKeyBlocker() {}

    private static final Map<Integer, String> BLOCKED_KEYS = new HashMap<>();
    private static final Map<KeyMapping, InputConstants.Key> ORIGINAL_MAPPINGS = new HashMap<>();

    private static boolean mouseMotionBlocked = false;

    // ============================================================
    //  Блокировка
    // ============================================================

    public static void block(int glfwKey, String displayName) {
        if (glfwKey == GLFW.GLFW_KEY_ESCAPE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        // ---- Мышь ----
        if (glfwKey == MaredKeyNames.MOUSE_LEFT) {
            BLOCKED_KEYS.put(glfwKey, displayName);
            detach(mc.options.keyAttack);
            return;
        }
        if (glfwKey == MaredKeyNames.MOUSE_RIGHT) {
            BLOCKED_KEYS.put(glfwKey, displayName);
            detach(mc.options.keyUse);
            return;
        }
        if (glfwKey == MaredKeyNames.MOUSE_MIDDLE) {
            BLOCKED_KEYS.put(glfwKey, displayName);
            detach(mc.options.keyPickItem);
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

        KeyMapping mapping = findMapping(mc, glfwKey);
        if (mapping != null) {
            detach(mapping);
        }
    }

    public static void unblock(int glfwKey) {
        BLOCKED_KEYS.remove(glfwKey);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        if (glfwKey == MaredKeyNames.MOUSE_LEFT)   { reattach(mc.options.keyAttack); return; }
        if (glfwKey == MaredKeyNames.MOUSE_RIGHT)  { reattach(mc.options.keyUse); return; }
        if (glfwKey == MaredKeyNames.MOUSE_MIDDLE) { reattach(mc.options.keyPickItem); return; }
        if (glfwKey == MaredKeyNames.MOUSE_MOVE)   { unblockMouseMotion(); return; }

        KeyMapping mapping = findMapping(mc, glfwKey);
        if (mapping != null) reattach(mapping);
    }

    public static boolean isBlocked(int glfwKey) {
        return BLOCKED_KEYS.containsKey(glfwKey);
    }

    // ============================================================
    //  Мышь — движение
    // ============================================================

    public static void blockMouseMotion() {
        mouseMotionBlocked = true;
    }

    public static void unblockMouseMotion() {
        mouseMotionBlocked = false;
    }

    public static boolean isMouseMotionBlocked() { return mouseMotionBlocked; }

    // ============================================================
    //  Отвязка / возврат
    // ============================================================

    private static void detach(KeyMapping mapping) {
        if (mapping == null) return;
        if (ORIGINAL_MAPPINGS.containsKey(mapping)) return;
        ORIGINAL_MAPPINGS.put(mapping, mapping.getKey());
        mapping.setKey(InputConstants.UNKNOWN);
        mapping.setDown(false);
    }

    private static void reattach(KeyMapping mapping) {
        if (mapping == null) return;
        InputConstants.Key orig = ORIGINAL_MAPPINGS.remove(mapping);
        if (orig == null) return;
        mapping.setKey(orig);
    }

    // ============================================================
    //  Clear
    // ============================================================

    public static void clear() {
        for (Map.Entry<KeyMapping, InputConstants.Key> e : ORIGINAL_MAPPINGS.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                e.getKey().setKey(e.getValue());
            }
        }
        ORIGINAL_MAPPINGS.clear();
        BLOCKED_KEYS.clear();
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

            KeyMapping mapping = findMapping(mc, keyCode);
            if (mapping == null) continue;

            mapping.setDown(false);
            while (mapping.consumeClick()) {
                // выедаем
            }
        }
    }

    // ============================================================
    //  Поиск
    // ============================================================

    public static KeyMapping findMapping(Minecraft mc, int glfwKey) {
        if (mc.options.keyMappings == null) return null;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) continue;
            if (mapping.getDefaultKey().getValue() == glfwKey) return mapping;
        }
        return null;
    }
}