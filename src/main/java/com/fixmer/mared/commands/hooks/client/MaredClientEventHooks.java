package com.fixmer.mared.commands.hooks.client;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.engine.MaredScriptRunner;
import com.fixmer.mared.commands.engine.MaredStateTracker;
import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.input.MaredActionRegistry;
import com.fixmer.mared.commands.input.MaredBindRegistry;
import com.fixmer.mared.commands.input.MaredKeyBlocker;
import com.fixmer.mared.commands.input.MaredKeyNames;
import com.fixmer.mared.commands.storage.MaredGlobalStorage;

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

    // ============================================================
    //  Состояние для отслеживания изменений
    // ============================================================

    private static int lastHotbarSlot = -1;
    private static boolean lastSneaking = false;
    private static boolean lastSprinting = false;
    private static boolean lastOnGround = true;
    private static boolean lastIsAlive = true;
    private static String lastDim = null;
    private static int lastHeldCount = -1;

    // ============================================================
    //  Мышь
    // ============================================================

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        try {
            MaredActionRegistry.observeMouse(event.getButton(),event.getAction());
            Minecraft mc = Minecraft.getInstance();
            if(mc.screen==null&&mc.player!=null){
                int code=switch(event.getButton()){case GLFW.GLFW_MOUSE_BUTTON_LEFT->MaredKeyNames.MOUSE_LEFT;case GLFW.GLFW_MOUSE_BUTTON_RIGHT->MaredKeyNames.MOUSE_RIGHT;case GLFW.GLFW_MOUSE_BUTTON_MIDDLE->MaredKeyNames.MOUSE_MIDDLE;default->Integer.MIN_VALUE;};
                com.fixmer.mared.commands.input.MaredInputBridge.input(code,event.getAction(),mc.getSingleplayerServer());
                if(MaredKeyBlocker.isBlocked(code))event.setCanceled(true);
            }
            if (event.getAction() != GLFW.GLFW_PRESS) return;

            if (mc.screen != null || mc.player == null) return;

            String type = switch (event.getButton()) {
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT  -> "right_click";
                case GLFW.GLFW_MOUSE_BUTTON_LEFT   -> "left_click";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "middle_click";
                default -> null;
            };
            if (type == null || !MaredEventRegistry.has(type)) return;

            MaredEventRegistry.fire(type, mc.getSingleplayerServer(),
                buildClickData(mc));
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onMouseButton error", t);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;

            String type = event.getScrollDeltaY() > 0 ? "scroll_up" : "scroll_down";
            if (!MaredEventRegistry.has(type)) return;

            MaredEventRegistry.fire(type, mc.getSingleplayerServer(),
                playerData(mc.player));
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onScroll error", t);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        try {
            MaredActionRegistry.observeKey(event.getKey(),event.getScanCode(),event.getAction());
            int key = event.getKey();
            if (key == GLFW.GLFW_KEY_ESCAPE) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;

            com.fixmer.mared.commands.input.MaredInputBridge.input(key,event.getAction(),mc.getSingleplayerServer());
            MaredKeyBlocker.tick();
            String type;
            if (event.getAction() == GLFW.GLFW_PRESS) type = "key_press";
            else if (event.getAction() == GLFW.GLFW_RELEASE) type = "key_release";
            else return;

            if (!MaredEventRegistry.has(type)) return;

            Map<String, Object> data = playerData(mc.player);
            data.put("key_code", event.getKey());
            data.put("key_name", MaredKeyNames.nameForKeyCode(event.getKey()));
            MaredEventRegistry.fire(type, mc.getSingleplayerServer(), data);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onKey error", t);
        }
    }

    // ============================================================
    //  Блоки / предметы
    // ============================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof LocalPlayer lp)) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;

            var pos = event.getPos();
            var level = event.getLevel();
            MinecraftServer server = mc.getSingleplayerServer();

            if (MaredEventRegistry.has("block_interact")) {
                Map<String, Object> data = playerData(lp);
                data.put("block_x", pos.getX());
                data.put("block_y", pos.getY());
                data.put("block_z", pos.getZ());
                fillBlockInfo(data, level, pos);
                fillDimension(data, level);
                MaredEventRegistry.fire("block_interact", server, data);
            }

            if (MaredEventRegistry.has("block_place")) {
                var held = lp.getItemInHand(event.getHand());
                if (held.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                    var placePos = pos.relative(event.getFace());
                    Map<String, Object> data = playerData(lp);
                    data.put("block_x", placePos.getX());
                    data.put("block_y", placePos.getY());
                    data.put("block_z", placePos.getZ());
                    try {
                        data.put("block_id", BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString());
                        data.put("block_name", bi.getBlock().getName().getString());
                    } catch (Throwable t) {
                        data.put("block_id", "unknown");
                        data.put("block_name", "unknown");
                    }
                    fillDimension(data, level);
                    MaredEventRegistry.fire("block_place", server, data);
                }
            }
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onRightClickBlock error", t);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        try {
            if (!MaredEventRegistry.has("use_item")) return;
            if (!(event.getEntity() instanceof LocalPlayer lp)) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;

            var stack = event.getItemStack();
            Map<String, Object> data = playerData(lp);
            try {
                data.put("item_id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            } catch (Throwable t) {
                data.put("item_id", "unknown");
            }
            data.put("item_name", stack.getHoverName().getString());
            data.put("item_count", stack.getCount());
            fillDimension(data, lp.level());

            MaredEventRegistry.fire("use_item", mc.getSingleplayerServer(), data);
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
            if (!(event.getEntity() instanceof LocalPlayer lp)) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;

            var target = event.getTarget();
            MaredClientStatePoller.trackAttackTarget(target);

            if (!MaredEventRegistry.has("attack")) return;

            Map<String, Object> data = playerData(lp);
            try {
                data.put("target_id",
                    target.getType().builtInRegistryHolder().key().location().toString());
            } catch (Throwable t) {
                data.put("target_id", "unknown");
            }
            data.put("target_name", target.getName().getString());
            data.put("target_x", (int) target.getX());
            data.put("target_y", (int) target.getY());
            data.put("target_z", (int) target.getZ());

            MaredEventRegistry.fire("attack", mc.getSingleplayerServer(), data);
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
            Map<String, Object> data = new HashMap<>(2);
            data.put("message", event.getMessage().getString());
            MaredEventRegistry.fire("chat", mc.getSingleplayerServer(), data);
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onChat error", t);
        }
    }

    // ============================================================
    //  Tick — основной
    // ============================================================

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            com.fixmer.mared.technology.catalog.MinecraftCommandCatalog.poll(mc.getConnection());
            com.fixmer.mared.commands.runner.MaredFileRunner.tickClient();
            LocalPlayer player = mc.player;
            if (player == null) {MaredActionRegistry.applyTick();com.fixmer.mared.commands.input.MaredInputBridge.tick(null,false);return;}

            MinecraftServer server = mc.getSingleplayerServer();
            Map<String, Object> playerData = playerData(player);

            fireTickClient(server, player);
            checkHotbarSwitch(server, player);
            checkSneak(server, player);
            checkSprint(server, player);
            checkJump(server, player);
            checkDeathRespawn(server, player);
            checkDimensionChange(server, player);
            checkItemPickup(server, player);

            // Multiplexer polling-событий
            MaredClientStatePoller.poll();

            // В мультиплеере executor'ы тикают клиентом
            com.fixmer.mared.commands.input.MaredInputBridge.tick(server,mc.screen==null);
            if (server == null) MaredScriptRunner.tick(null);
            MaredActionRegistry.applyTick();
            MaredKeyBlocker.tick();

            // State diff
            MaredStateTracker.Diff diff = MaredStateTracker.poll();
            if (diff.hasChanges) {
                dispatchDiff(server, playerData, diff.data);
            }
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onTick error", t);
        }
    }

    // ============================================================
    //  Login / Logout
    // ============================================================

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            com.fixmer.mared.technology.catalog.MinecraftCommandCatalog.poll(mc.getConnection());
            LocalPlayer player = mc.player;
            if (player == null) return;

            MinecraftServer server = mc.getSingleplayerServer();
            Map<String, Object> data = playerData(player);
            data.put("uuid", player.getUUID().toString());
            data.put("dimension", player.level().dimension().location().getPath());
            data.put("world", player.level().dimension().location().toString());

            if (MaredEventRegistry.has("join")) {
                MaredEventRegistry.fire("join", server, data);
            }

            if (MaredEventRegistry.has("first_join")) {
                String uuid = player.getUUID().toString();
                String key = "global.__first_join_" + uuid;
                if (!MaredGlobalStorage.has(key)) {
                    MaredGlobalStorage.set(key, Boolean.TRUE);
                    MaredEventRegistry.fire("first_join", server, new HashMap<>(data));
                }
            }
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onLogin error", t);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.fixmer.mared.technology.catalog.MinecraftCommandCatalog.reset();
        try {
            Minecraft mc = Minecraft.getInstance();

            if (MaredEventRegistry.has("leave")) {
                Map<String, Object> data = new HashMap<>(2);
                data.put("player", event.getPlayer() != null
                    ? event.getPlayer().getName().getString() : "unknown");
                MaredEventRegistry.fire("leave", mc.getSingleplayerServer(), data);
            }

            MaredBindRegistry.clearAll();
            MaredKeyBlocker.clear();
            MaredActionRegistry.stopAll();
            MaredStateTracker.reset();
            MaredClientStatePoller.reset();

            lastHotbarSlot = -1;
            lastSneaking = false;
            lastSprinting = false;
            lastOnGround = true;
            lastIsAlive = true;
            lastDim = null;
            lastHeldCount = -1;
        } catch (Throwable t) {
            Mared.LOGGER.error("[Mared] onLogout error", t);
        }
    }

    // ============================================================
    //  Общие хелперы для data
    // ============================================================

    private static Map<String, Object> playerData(LocalPlayer p) {
        Map<String, Object> data = new HashMap<>(8);
        String name = p.getName().getString();
        data.put("player", name);
        data.put("self", name);
        if (p.level() != null) {
            data.put("world", p.level().dimension().location().toString());
        }
        return data;
    }

    private static void fillDimension(Map<String, Object> data, net.minecraft.world.level.Level level) {
        if (level == null) return;
        var loc = level.dimension().location();
        data.put("world", loc.toString());
        data.put("dimension", loc.getPath());
    }

    private static void fillBlockInfo(Map<String, Object> data,
                                      net.minecraft.world.level.Level level,
                                      net.minecraft.core.BlockPos pos) {
        try {
            var state = level.getBlockState(pos);
            data.put("block_id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            data.put("block_name", state.getBlock().getName().getString());
        } catch (Throwable t) {
            data.put("block_id", "unknown");
            data.put("block_name", "unknown");
        }
    }

    private static Map<String, Object> buildClickData(Minecraft mc) {
        Map<String, Object> data = playerData(mc.player);

        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr) {
            var pos = bhr.getBlockPos();
            data.put("block_x", pos.getX());
            data.put("block_y", pos.getY());
            data.put("block_z", pos.getZ());
            data.put("block_face", bhr.getDirection().getName());

            data.put("click_x", pos.getX());
            data.put("click_y", pos.getY());
            data.put("click_z", pos.getZ());
            data.put("click_face", bhr.getDirection().getName());

            fillBlockInfo(data, mc.player.level(), pos);
            data.put("click_block", data.get("block_id"));
            data.put("click_block_name", data.get("block_name"));
        }
        return data;
    }

    // ============================================================
    //  Tick-детекторы
    // ============================================================

    private static void fireTickClient(MinecraftServer server, LocalPlayer player) {
        if (!MaredEventRegistry.has("tick_client")) return;
        Map<String, Object> data = new HashMap<>(2);
        data.put("player", player.getName().getString());
        MaredEventRegistry.fire("tick_client", server, data);
    }

    private static void checkHotbarSwitch(MinecraftServer server, LocalPlayer player) {
        int slot = player.getInventory().selected;
        if (lastHotbarSlot == -1) { lastHotbarSlot = slot; return; }
        if (slot == lastHotbarSlot) return;
        if (MaredEventRegistry.has("hotbar_switch")) {
            Map<String, Object> data = new HashMap<>(4);
            data.put("player", player.getName().getString());
            data.put("from_slot", lastHotbarSlot);
            data.put("to_slot", slot);
            MaredEventRegistry.fire("hotbar_switch", server, data);
        }
        lastHotbarSlot = slot;
    }

    private static void checkSneak(MinecraftServer server, LocalPlayer player) {
        boolean sneaking = player.isShiftKeyDown();
        if (sneaking == lastSneaking) return;
        String type = sneaking ? "sneak_start" : "sneak_end";
        if (MaredEventRegistry.has(type)) {
            Map<String, Object> data = new HashMap<>(2);
            data.put("player", player.getName().getString());
            MaredEventRegistry.fire(type, server, data);
        }
        lastSneaking = sneaking;
    }

    private static void checkSprint(MinecraftServer server, LocalPlayer player) {
        boolean sprinting = player.isSprinting();
        if (sprinting == lastSprinting) return;
        String type = sprinting ? "sprint_start" : "sprint_end";
        if (MaredEventRegistry.has(type)) {
            Map<String, Object> data = new HashMap<>(2);
            data.put("player", player.getName().getString());
            MaredEventRegistry.fire(type, server, data);
        }
        lastSprinting = sprinting;
    }

    private static void checkJump(MinecraftServer server, LocalPlayer player) {
        boolean onGround = player.onGround();
        if (lastOnGround && !onGround && MaredEventRegistry.has("jump")) {
            Map<String, Object> data = new HashMap<>(5);
            data.put("player", player.getName().getString());
            data.put("x", (int) player.getX());
            data.put("y", (int) player.getY());
            data.put("z", (int) player.getZ());
            MaredEventRegistry.fire("jump", server, data);
        }
        lastOnGround = onGround;
    }

    private static void checkDeathRespawn(MinecraftServer server, LocalPlayer player) {
        boolean isAlive = player.isAlive();
        if (lastIsAlive == isAlive) return;

        if (!isAlive && MaredEventRegistry.has("player_death")) {
            Map<String, Object> data = new HashMap<>(8);
            data.put("player", player.getName().getString());
            data.put("self", player.getName().getString());
            data.put("x", (int) player.getX());
            data.put("y", (int) player.getY());
            data.put("z", (int) player.getZ());
            data.put("death_cause", "unknown");
            data.put("killer", "");
            MaredEventRegistry.fire("player_death", server, data);
        } else if (isAlive && MaredEventRegistry.has("respawn")) {
            Map<String, Object> data = new HashMap<>(5);
            data.put("player", player.getName().getString());
            data.put("self", player.getName().getString());
            data.put("x", (int) player.getX());
            data.put("y", (int) player.getY());
            data.put("z", (int) player.getZ());
            MaredEventRegistry.fire("respawn", server, data);
        }
        lastIsAlive = isAlive;
    }

    private static void checkDimensionChange(MinecraftServer server, LocalPlayer player) {
        String cur = player.level().dimension().location().toString();
        if (lastDim == null) { lastDim = cur; return; }
        if (cur.equals(lastDim)) return;

        if (MaredEventRegistry.has("dimension_change")) {
            Map<String, Object> data = new HashMap<>(8);
            data.put("player", player.getName().getString());
            data.put("self", player.getName().getString());
            data.put("from_dimension", extractPath(lastDim));
            data.put("from_world", lastDim);
            data.put("to_dimension", extractPath(cur));
            data.put("to_world", cur);
            data.put("dimension", extractPath(cur));
            data.put("world", cur);
            MaredEventRegistry.fire("dimension_change", server, data);
        }
        lastDim = cur;
    }

    private static void checkItemPickup(MinecraftServer server, LocalPlayer player) {
        int count = player.getMainHandItem().getCount();
        if (lastHeldCount == -1) { lastHeldCount = count; return; }
        if (count <= lastHeldCount) { lastHeldCount = count; return; }

        if (MaredEventRegistry.has("item_pickup")) {
            var stack = player.getMainHandItem();
            Map<String, Object> data = new HashMap<>(6);
            data.put("player", player.getName().getString());
            data.put("self", player.getName().getString());
            try {
                data.put("item_id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            } catch (Throwable t) {
                data.put("item_id", "unknown");
            }
            data.put("item_name", stack.getHoverName().getString());
            data.put("item_count", count - lastHeldCount);
            MaredEventRegistry.fire("item_pickup", server, data);
        }
        lastHeldCount = count;
    }

    private static void dispatchDiff(MinecraftServer server, Map<String, Object> base,
                                     Map<String, Object> diff) {
        Map<String, Object> data = null;
        if (diff.containsKey("dx"))          data = merged(base, diff, "player_move");
        if (diff.containsKey("new_hp"))      data = merged(base, diff, "health_change");
        if (diff.containsKey("new_food"))    data = merged(base, diff, "hunger_change");
        if (diff.containsKey("new_xp"))      data = merged(base, diff, "xp_change");
        if (diff.containsKey("dropped"))     data = merged(base, diff, "item_drop");
        if (diff.containsKey("new_gamemode"))data = merged(base, diff, "gamemode_change");
        if (data != null) { /* fired inside merged */ }
    }

    private static Map<String, Object> merged(Map<String, Object> base,
                                              Map<String, Object> diff,
                                              String type) {
        if (!MaredEventRegistry.has(type)) return null;
        Map<String, Object> full = new HashMap<>(base);
        full.putAll(diff);
        MaredEventRegistry.fire(type, Minecraft.getInstance().getSingleplayerServer(), full);
        return full;
    }

    private static String extractPath(String full) {
        if (full == null) return "unknown";
        int idx = full.indexOf(':');
        return idx >= 0 ? full.substring(idx + 1) : full;
    }
}