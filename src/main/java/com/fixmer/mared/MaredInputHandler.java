package com.fixmer.mared;

import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredKeyBlocker;
import com.fixmer.mared.script.MaredKeyNames;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "mared", value = Dist.CLIENT)
public final class MaredInputHandler {

    private MaredInputHandler() {}

    private static final int MOD_MASK =
        GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT
      | GLFW.GLFW_MOD_ALT     | GLFW.GLFW_MOD_SUPER;

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

        for (String keyStr : MaredBindRegistry.keys()) {
            MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
            if (pk == null) continue;
            if (pk.keyCode != key) continue;
            if ((pk.modifiers & MOD_MASK) != mods) continue;

            MinecraftServer server = mc.getSingleplayerServer();
            MaredBindRegistry.fire(keyStr, server);
            MaredBindRegistry.startHold(keyStr, server);

            if (MaredBindRegistry.hasBlocking(keyStr)) {
                MaredKeyBlocker.block(key, keyStr);
            }
        }
    }

    private static void handleKeyRelease(int key, Minecraft mc) {
        int mods = currentMods() & MOD_MASK;

        for (String keyStr : MaredBindRegistry.keys()) {
            MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
            if (pk == null) continue;
            if (pk.keyCode != key) continue;
            if ((pk.modifiers & MOD_MASK) != mods) continue;

            MinecraftServer server = mc.getSingleplayerServer();
            MaredBindRegistry.stopHold(keyStr);
            MaredBindRegistry.fireRelease(keyStr, server);
        }
    }

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

        for (String keyStr : MaredBindRegistry.keys()) {
            MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
            if (pk == null) continue;
            if (pk.keyCode != maredCode) continue;

            MinecraftServer server = mc.getSingleplayerServer();
            MaredBindRegistry.fire(keyStr, server);

            if (MaredBindRegistry.hasBlocking(keyStr)) {
                MaredKeyBlocker.block(maredCode, keyStr);
                event.setCanceled(true);
                return;
            }
        }

        if (MaredKeyBlocker.isBlocked(maredCode)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
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

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MaredBindRegistry.clearAll();
        MaredKeyBlocker.clear();
    }
}