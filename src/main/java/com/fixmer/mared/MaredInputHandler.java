package com.fixmer.mared;

import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredKeyNames;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "mared", value = Dist.CLIENT)
public final class MaredInputHandler {

    private MaredInputHandler() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        int key = event.getKey();
        int action = event.getAction();

        if (action != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        int mods = currentMods();

        for (String keyStr : MaredBindRegistry.keys()) {
            MaredKeyNames.ParsedKey pk = MaredKeyNames.parse(keyStr);
            if (pk == null) continue;
            if (pk.keyCode != key) continue;
            if ((pk.modifiers & mods) != pk.modifiers) continue;

            MinecraftServer server = mc.getSingleplayerServer();
            MaredBindRegistry.fire(keyStr, server);
            // блокировка стандартного поведения — отложена
        }
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
    }
}