package com.fixmer.mared.event;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredKeyBlocker;
import com.fixmer.mared.script.MaredStateTracker;

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
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class MaredClientEventHooks {

    private MaredClientEventHooks() {}

    // === Состояние для отслеживания изменений ===
    private static int lastHotbarSlot = -1;
    private static boolean lastSneaking = false;
    private static boolean lastSprinting = false;
    private static boolean lastOnGround = true;

    // ============================================================
    //  Клик мышью
    // ============================================================

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        try {
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
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onMouseButton error", t);
        }
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
                    data.put("click_block_name", state.getBlock().getName().getString());
                }
            } catch (Throwable ignored) {}
        }
        return data;
    }

    // ============================================================
    //  Scroll
    // ============================================================

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;
            if (mc.player == null) return;

            String type = event.getScrollDeltaY() > 0 ? "scroll_up" : "scroll_down";
            if (!MaredEventRegistry.has(type)) return;

            MinecraftServer server = mc.getSingleplayerServer();
            Map<String, Object> data = new HashMap<>();
            data.put("player", mc.player.getName().getString());
            MaredEventRegistry.fire(type, server, data);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onScroll error", t);
        }
    }

    // ============================================================
    //  Key
    // ============================================================

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        try {
            int key = event.getKey();
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
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onKey error", t);
        }
    }

    // ============================================================
    //  Block interact
    // ============================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!MaredEventRegistry.has("block_interact")) return;
            if (!(event.getEntity() instanceof LocalPlayer lp)) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;

            var pos = event.getPos();
            var level = event.getLevel();
            MinecraftServer server = mc.getSingleplayerServer();

            Map<String, Object> data = new HashMap<>();
            data.put("player", lp.getName().getString());
            data.put("self", lp.getName().getString());

            data.put("block_x", pos.getX());
            data.put("block_y", pos.getY());
            data.put("block_z", pos.getZ());

            try {
                var state = level.getBlockState(pos);
                var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                data.put("block_id", key.toString());
                data.put("block_name", state.getBlock().getName().getString());
            } catch (Throwable t) {
                data.put("block_id", "unknown");
                data.put("block_name", "unknown");
            }

            data.put("world", level.dimension().location().toString());
            data.put("dimension", level.dimension().location().getPath());

            MaredEventRegistry.fire("block_interact", server, data);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onRightClickBlock error", t);
        }
    }

    // ============================================================
    //  Use item
    // ============================================================

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        try {
            if (!MaredEventRegistry.has("use_item")) return;
            if (!(event.getEntity() instanceof LocalPlayer lp)) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;

            var stack = event.getItemStack();
            MinecraftServer server = mc.getSingleplayerServer();

            Map<String, Object> data = new HashMap<>();
            data.put("player", lp.getName().getString());
            data.put("self", lp.getName().getString());

            try {
                var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                data.put("item_id", key.toString());
            } catch (Throwable t) {
                data.put("item_id", "unknown");
            }
            data.put("item_name", stack.getHoverName().getString());
            data.put("item_count", stack.getCount());

            data.put("world", lp.level().dimension().location().toString());
            data.put("dimension", lp.level().dimension().location().getPath());

            MaredEventRegistry.fire("use_item", server, data);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onRightClickItem error", t);
        }
    }

    // ============================================================
    //  Attack
    // ============================================================

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        try {
            if (!MaredEventRegistry.has("attack")) return;
            if (!(event.getEntity() instanceof LocalPlayer lp)) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;

            var target = event.getTarget();
            MinecraftServer server = mc.getSingleplayerServer();

            Map<String, Object> data = new HashMap<>();
            data.put("player", lp.getName().getString());
            data.put("self", lp.getName().getString());

            try {
                data.put("target_id", target.getType().builtInRegistryHolder().key().location().toString());
            } catch (Throwable t) {
                data.put("target_id", "unknown");
            }
            data.put("target_name", target.getName().getString());

            data.put("target_x", (int) target.getX());
            data.put("target_y", (int) target.getY());
            data.put("target_z", (int) target.getZ());

            MaredEventRegistry.fire("attack", server, data);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onAttack error", t);
        }
    }

    // ============================================================
    //  Chat
    // ============================================================

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        try {
            if (!MaredEventRegistry.has("chat")) return;
            if (MaredEventRegistry.isChatSuppressed()) return;

            Minecraft mc = Minecraft.getInstance();
            MinecraftServer server = mc.getSingleplayerServer();
            Map<String, Object> data = new HashMap<>();
            Component msg = event.getMessage();
            data.put("message", msg.getString());
            MaredEventRegistry.fire("chat", server, data);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onChat error", t);
        }
    }

    // ============================================================
    //  Tick
    // ============================================================

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            MinecraftServer server = mc.getSingleplayerServer();
            LocalPlayer player = mc.player;

            // tick_client
            if (MaredEventRegistry.has("tick_client")) {
                Map<String, Object> data = new HashMap<>();
                data.put("player", player.getName().getString());
                MaredEventRegistry.fire("tick_client", server, data);
            }

            // hotbar_switch
            int currentSlot = player.getInventory().selected;
            if (lastHotbarSlot == -1) lastHotbarSlot = currentSlot;
            if (currentSlot != lastHotbarSlot) {
                if (MaredEventRegistry.has("hotbar_switch")) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("player", player.getName().getString());
                    data.put("from_slot", lastHotbarSlot);
                    data.put("to_slot", currentSlot);
                    MaredEventRegistry.fire("hotbar_switch", server, data);
                }
                lastHotbarSlot = currentSlot;
            }

            // sneak_start / sneak_end
            boolean sneaking = player.isShiftKeyDown();
            if (sneaking != lastSneaking) {
                String type = sneaking ? "sneak_start" : "sneak_end";
                if (MaredEventRegistry.has(type)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("player", player.getName().getString());
                    MaredEventRegistry.fire(type, server, data);
                }
                lastSneaking = sneaking;
            }

            // sprint_start / sprint_end
            boolean sprinting = player.isSprinting();
            if (sprinting != lastSprinting) {
                String type = sprinting ? "sprint_start" : "sprint_end";
                if (MaredEventRegistry.has(type)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("player", player.getName().getString());
                    MaredEventRegistry.fire(type, server, data);
                }
                lastSprinting = sprinting;
            }

            // jump
            boolean onGround = player.onGround();
            if (lastOnGround && !onGround) {
                if (MaredEventRegistry.has("jump")) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("player", player.getName().getString());
                    data.put("x", (int) player.getX());
                    data.put("y", (int) player.getY());
                    data.put("z", (int) player.getZ());
                    MaredEventRegistry.fire("jump", server, data);
                }
            }
            lastOnGround = onGround;

            // State tracker
            MaredStateTracker.Diff diff = MaredStateTracker.poll();
            if (diff.hasChanges) {
                if (diff.data.containsKey("dx")) {
                    fireIfHas("player_move", server, player, diff.data);
                }
                if (diff.data.containsKey("new_hp")) {
                    fireIfHas("health_change", server, player, diff.data);
                }
                if (diff.data.containsKey("new_food")) {
                    fireIfHas("hunger_change", server, player, diff.data);
                }
                if (diff.data.containsKey("new_xp")) {
                    fireIfHas("xp_change", server, player, diff.data);
                }
                if (diff.data.containsKey("dropped")) {
                    fireIfHas("item_drop", server, player, diff.data);
                }
                if (diff.data.containsKey("new_gamemode")) {
                    fireIfHas("gamemode_change", server, player, diff.data);
                }
            }
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onTick error", t);
        }
    }

    /**
     * Единая точка запуска триггеров состояния.
     */
    private static void fireIfHas(String type, MinecraftServer server, LocalPlayer player,
                                  Map<String, Object> data) {
        try {
            if (!MaredEventRegistry.has(type)) return;
            Map<String, Object> full = new HashMap<>(data);
            full.put("player", player.getName().getString());
            full.put("self", player.getName().getString());
            MaredEventRegistry.fire(type, server, full);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] fireIfHas error for {}", type, t);
        }
    }

    // ============================================================
    //  Logout
    // ============================================================

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftServer server = mc.getSingleplayerServer();

            if (MaredEventRegistry.has("leave")) {
                Map<String, Object> data = new HashMap<>();
                if (event.getPlayer() != null) data.put("player", event.getPlayer().getName().getString());
                else data.put("player", "unknown");
                MaredEventRegistry.fire("leave", server, data);
            }

            MaredBindRegistry.clearAll();
            MaredKeyBlocker.clear();
            MaredActionRegistry.stopAll();
            MaredStateTracker.reset();

            lastHotbarSlot = -1;
            lastSneaking = false;
            lastSprinting = false;
            lastOnGround = true;
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onLogout error", t);
        }
    }

    // ============================================================
    //  Утилиты
    // ============================================================

    private static String nameForKey(int code) {
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z)
            return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9)
            return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25)
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
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
}