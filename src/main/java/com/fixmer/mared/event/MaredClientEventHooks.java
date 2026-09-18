package com.fixmer.mared.event;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.MaredEventRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
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

@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class MaredClientEventHooks {

    private MaredClientEventHooks() {}

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
            ? player.level().dimension().location().toString() : "unknown");

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
                    var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    data.put("click_block", key.toString());
                }
            } catch (Throwable ignored) {}
        }
        return data;
    }

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

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        int key = event.getKey();

        // ← FIX: Escape не пробрасываем в key_press / key_release
        if (key == GLFW.GLFW_KEY_ESCAPE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (mc.player == null) return;

        String type;
        if (event.getAction() == GLFW.GLFW_PRESS) type = "key_press";
        else if (event.getAction() == GLFW.GLFW_RELEASE) type = "key_release";
        else return;

        if (!MaredEventRegistry.has(type)) return;

        MinecraftServer server = mc.getSingleplayerServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", mc.player.getName().getString());
        data.put("key_code", event.getKey());
        data.put("key_name", nameForKey(event.getKey()));
        MaredEventRegistry.fire(type, server, data);
    }

    /**
     * FIX: расширен список имён клавиш — добавлены модификаторы, F-клавиши,
     * стрелки, цифры Numpad, Escape и т.д.
     */
    private static String nameForKey(int code) {
        // A-Z
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z)
            return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
        // 0-9
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9)
            return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
        // F1-F25
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25)
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        // Numpad 0-9
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9)
            return "Numpad" + (code - GLFW.GLFW_KEY_KP_0);

        switch (code) {
            case GLFW.GLFW_KEY_SPACE:        return "Space";
            case GLFW.GLFW_KEY_ENTER:        return "Enter";
            case GLFW.GLFW_KEY_ESCAPE:       return "Escape";
            case GLFW.GLFW_KEY_TAB:          return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE:    return "Backspace";
            case GLFW.GLFW_KEY_DELETE:       return "Delete";
            case GLFW.GLFW_KEY_INSERT:       return "Insert";
            case GLFW.GLFW_KEY_HOME:         return "Home";
            case GLFW.GLFW_KEY_END:          return "End";
            case GLFW.GLFW_KEY_PAGE_UP:      return "PageUp";
            case GLFW.GLFW_KEY_PAGE_DOWN:    return "PageDown";
            case GLFW.GLFW_KEY_UP:           return "Up";
            case GLFW.GLFW_KEY_DOWN:         return "Down";
            case GLFW.GLFW_KEY_LEFT:         return "Left";
            case GLFW.GLFW_KEY_RIGHT:        return "Right";
            case GLFW.GLFW_KEY_LEFT_SHIFT:   return "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT:  return "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL:return "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT:     return "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT:    return "RAlt";
            case GLFW.GLFW_KEY_LEFT_SUPER:   return "LSuper";
            case GLFW.GLFW_KEY_RIGHT_SUPER:  return "RSuper";
            case GLFW.GLFW_KEY_CAPS_LOCK:    return "CapsLock";
            case GLFW.GLFW_KEY_NUM_LOCK:     return "NumLock";
            case GLFW.GLFW_KEY_SCROLL_LOCK:  return "ScrollLock";
            case GLFW.GLFW_KEY_PRINT_SCREEN: return "PrintScreen";
            case GLFW.GLFW_KEY_PAUSE:        return "Pause";
            case GLFW.GLFW_KEY_MINUS:        return "Minus";
            case GLFW.GLFW_KEY_EQUAL:        return "Equal";
            case GLFW.GLFW_KEY_LEFT_BRACKET: return "LBracket";
            case GLFW.GLFW_KEY_RIGHT_BRACKET:return "RBracket";
            case GLFW.GLFW_KEY_BACKSLASH:    return "Backslash";
            case GLFW.GLFW_KEY_SEMICOLON:    return "Semicolon";
            case GLFW.GLFW_KEY_APOSTROPHE:   return "Apostrophe";
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return "Grave";
            case GLFW.GLFW_KEY_COMMA:        return "Comma";
            case GLFW.GLFW_KEY_PERIOD:       return "Period";
            case GLFW.GLFW_KEY_SLASH:        return "Slash";
            default:                         return "Key#" + code;
        }
    }

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

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!MaredEventRegistry.has("leave")) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        Map<String, Object> data = new HashMap<>();
        if (event.getPlayer() != null) data.put("player", event.getPlayer().getName().getString());
        else data.put("player", "unknown");
        MaredEventRegistry.fire("leave", server, data);
    }
}