package com.fixmer.mared.event;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.MaredEventRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клиентские хуки событий.
 *
 * ВАЖНО: 'join' здесь НЕТ — он идёт через MaredServerEvents.onPlayerLogin,
 * потому что PlayerLoggedInEvent — серверное событие.
 */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class MaredClientEventHooks {

    private MaredClientEventHooks() {}

    // ============================================================
    //  Мышь — клики
    // ============================================================

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (mc.player == null) return;

        String type;
        switch (event.getButton()) {
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT:  type = "right_click";  break;
            case GLFW.GLFW_MOUSE_BUTTON_LEFT:   type = "left_click";   break;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE: type = "middle_click"; break;
            default: return;
        }

        if (!MaredEventRegistry.has(type)) return;

        MinecraftServer server = mc.getSingleplayerServer();
        Map<String, Object> data = buildClickData(mc);

        MaredEventRegistry.fire(type, server, data);
    }

    private static Map<String, Object> buildClickData(Minecraft mc) {
        Map<String, Object> data = new HashMap<>();

        LocalPlayer player = mc.player;
        if (player == null) return data;

        data.put("player", player.getName().getString());
        data.put("self", player.getName().getString());
        data.put("world", player.level() != null
            ? player.level().dimension().location().toString()
            : "unknown");

        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr) {
            data.put("block_x", bhr.getBlockPos().getX());
            data.put("block_y", bhr.getBlockPos().getY());
            data.put("block_z", bhr.getBlockPos().getZ());
            data.put("block_face", bhr.getDirection().getName());

            data.put("click_x", bhr.getBlockPos().getX());
            data.put("click_y", bhr.getBlockPos().getY());
            data.put("click_z", bhr.getBlockPos().getZ());
            data.put("click_face", bhr.getDirection().getName());

            try {
                if (player.level() != null) {
                    var state = player.level().getBlockState(bhr.getBlockPos());
                    data.put("click_block", state.getBlock().toString());
                }
            } catch (Throwable ignored) {}
        }

        return data;
    }

    // ============================================================
    //  Мышь — прокрутка
    // ============================================================

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (mc.player == null) return;

        String type = event.getScrollDeltaY() > 0 ? "scroll_up" : "scroll_down";
        if (!MaredEventRegistry.has(type)) return;

        MinecraftServer server = mc.getSingleplayerServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", mc.player.getName().getString());

        MaredEventRegistry.fire(type, server, data);
    }

    // ============================================================
    //  Клавиатура
    // ============================================================

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (mc.player == null) return;

        String type;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            type = "key_press";
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            type = "key_release";
        } else {
            return;
        }

        if (!MaredEventRegistry.has(type)) return;

        MinecraftServer server = mc.getSingleplayerServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", mc.player.getName().getString());
        data.put("key_code", event.getKey());
        data.put("key_name", nameForKey(event.getKey()));

        MaredEventRegistry.fire(type, server, data);
    }

    private static String nameForKey(int code) {
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
        }
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
        }
        switch (code) {
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_ESCAPE: return "Escape";
            default: return "Key#" + code;
        }
    }

    // ============================================================
    //  Чат — фильтр через suppression
    // ============================================================

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (!MaredEventRegistry.has("chat")) return;
        if (MaredEventRegistry.isChatSuppressed()) return;

        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();

        Map<String, Object> data = new HashMap<>();
        Component msg = event.getMessage();
        data.put("message", msg.getString());

        MaredEventRegistry.fire("chat", server, data);
    }

    // ============================================================
    //  Client tick
    // ============================================================

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!MaredEventRegistry.has("tick_client")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MinecraftServer server = mc.getSingleplayerServer();

        Map<String, Object> data = new HashMap<>();
        data.put("player", mc.player.getName().getString());

        MaredEventRegistry.fire("tick_client", server, data);
    }

    // ============================================================
    //  Leave — через клиентское LoggingOut
    // ============================================================

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!MaredEventRegistry.has("leave")) return;

        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();

        Map<String, Object> data = new HashMap<>();
        if (event.getPlayer() != null) {
            data.put("player", event.getPlayer().getName().getString());
        } else {
            data.put("player", "unknown");
        }

        MaredEventRegistry.fire("leave", server, data);
    }
}