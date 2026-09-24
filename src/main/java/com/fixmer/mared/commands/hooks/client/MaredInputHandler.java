package com.fixmer.mared.commands.hooks.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.lwjgl.glfw.GLFW;
import com.fixmer.mared.commands.input.MaredActionRegistry;
import com.fixmer.mared.commands.input.MaredBindRegistry;
import com.fixmer.mared.commands.input.MaredKeyBlocker;
import com.fixmer.mared.commands.input.MaredKeyNames;

@EventBusSubscriber(modid = "mared", value = Dist.CLIENT)
public final class MaredInputHandler {

    private MaredInputHandler() {}

    private static final int MOD_MASK =
        GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT
      | GLFW.GLFW_MOD_ALT     | GLFW.GLFW_MOD_SUPER;

    // ============================================================
    //  Key press / release
    // ============================================================

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        int key = event.getKey();
        int action = event.getAction();
        if (key == GLFW.GLFW_KEY_ESCAPE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (action == GLFW.GLFW_PRESS) handleKeyPress(key, mc);
        else if (action == GLFW.GLFW_RELEASE) handleKeyRelease(key, mc);
    }

    private static void handleKeyPress(int key, Minecraft mc) {
        int mods = currentMods() & MOD_MASK;

        // FIX 0.2.5+: берём только бинды для этого keyCode — без парсинга всех.
        List<String> keys = MaredBindRegistry.keysForCode(key);
        if (keys.isEmpty()) return;

        MinecraftServer server = mc.getSingleplayerServer();

        int n = keys.size();
        for (int i = 0; i < n; i++) {
            String keyStr = keys.get(i);

            MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
            if (pk == null) continue;
            if ((pk.modifiers & MOD_MASK) != mods) continue;

            MaredBindRegistry.fire(keyStr, server);
            MaredBindRegistry.startHold(keyStr, server);

            if (MaredBindRegistry.hasBlocking(keyStr)) {
                MaredKeyBlocker.block(key, keyStr);
            }
        }
    }

    private static void handleKeyRelease(int key, Minecraft mc) {
        int mods = currentMods() & MOD_MASK;

        List<String> keys = MaredBindRegistry.keysForCode(key);
        if (keys.isEmpty()) return;

        MinecraftServer server = mc.getSingleplayerServer();

        int n = keys.size();
        for (int i = 0; i < n; i++) {
            String keyStr = keys.get(i);

            MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
            if (pk == null) continue;
            if ((pk.modifiers & MOD_MASK) != mods) continue;

            MaredBindRegistry.stopHold(keyStr);
            MaredBindRegistry.fireRelease(keyStr, server);
        }
    }

    // ============================================================
    //  Mouse
    // ============================================================

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        int button = event.getButton();
        int action = event.getAction();
        if (action != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        int maredCode;
        switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT:   maredCode = MaredKeyNames.MOUSE_LEFT;   break;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT:  maredCode = MaredKeyNames.MOUSE_RIGHT;  break;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE: maredCode = MaredKeyNames.MOUSE_MIDDLE; break;
            default: return;
        }

        List<String> keys = MaredBindRegistry.keysForCode(maredCode);
        if (!keys.isEmpty()) {
            MinecraftServer server = mc.getSingleplayerServer();
            int n = keys.size();
            for (int i = 0; i < n; i++) {
                String keyStr = keys.get(i);
                MaredBindRegistry.fire(keyStr, server);

                if (MaredBindRegistry.hasBlocking(keyStr)) {
                    MaredKeyBlocker.block(maredCode, keyStr);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (MaredKeyBlocker.isBlocked(maredCode)) {
            event.setCanceled(true);
        }
    }

    // ============================================================
    //  Tick
    // ============================================================

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        // FIX 0.2.5+: применяем действия, заданные скриптом, до ванильного ввода
        MaredActionRegistry.applyTick();

        MaredKeyBlocker.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        MaredBindRegistry.tickHolds(server);
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        MaredKeyBlocker.tick();
    }

    // ============================================================
    //  Модификаторы
    // ============================================================

    private static int currentMods() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        int mods = 0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) mods |= GLFW.GLFW_MOD_CONTROL;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) mods |= GLFW.GLFW_MOD_SHIFT;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) mods |= GLFW.GLFW_MOD_ALT;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS) mods |= GLFW.GLFW_MOD_SUPER;
        return mods;
    }

    // ============================================================
    //  Server stopped
    // ============================================================

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MaredBindRegistry.clearAll();
        MaredKeyBlocker.clear();
        MaredActionRegistry.stopAll();
        MaredKeyNames.clearCache();
    }
}