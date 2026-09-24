package com.fixmer.mared.commands.hooks.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fixmer.mared.commands.events.MaredEventRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Polling-события для мультиплеера.
 *
 * Отслеживает:
 *   block_break     — через изменение блока под прицелом
 *   entity_kill     — через diff атакованных сущностей
 *   entity_hurt     — через diff HP (отрицательный delta)
 *   item_crafted    — через слот результата CraftingMenu
 */
final class MaredClientStatePoller {

    private MaredClientStatePoller() {}

    private static final int KILL_TIMEOUT_TICKS = 40;

    // ---- block_break ----
    private static BlockPos lastHitPos = null;
    private static boolean lastHitWasAir = true;

    // ---- entity_kill ----
    private static final Map<Integer, EntityInfo> PENDING_KILLS = new HashMap<>();
    private static final Set<Integer> TO_REMOVE = new HashSet<>();

    // ---- entity_hurt ----
    private static int lastHpForHurt = -1;

    // ---- item_crafted ----
    private static boolean lastCraftingOpen = false;
    private static ItemStack lastCraftResult = ItemStack.EMPTY;

    private static final class EntityInfo {
        final String id;
        final String name;
        int ticksAlive;

        EntityInfo(String id, String name) {
            this.id = id;
            this.name = name;
            this.ticksAlive = 0;
        }
    }

    // ============================================================
    //  Main
    // ============================================================

    static void poll() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ClientLevel level = mc.level;
        if (level == null) return;
        MinecraftServer server = mc.getSingleplayerServer();

        pollBlockBreak(mc, player, level, server);
        pollEntityKill(player, level, server);
        pollEntityHurt(player, server);
        pollItemCrafted(mc, player, server);
    }

    // ============================================================
    //  block_break
    // ============================================================

    private static void pollBlockBreak(Minecraft mc, LocalPlayer player,
                                        ClientLevel level, MinecraftServer server) {
        if (!(mc.hitResult instanceof BlockHitResult bhr)) {
            lastHitPos = null;
            return;
        }

        BlockPos pos = bhr.getBlockPos();
        boolean isAir = level.getBlockState(pos).isAir();

        if (lastHitPos != null && lastHitPos.equals(pos) && !lastHitWasAir && isAir) {
            if (MaredEventRegistry.has("block_break")) {
                Map<String, Object> data = new HashMap<>(8);
                data.put("player", player.getName().getString());
                data.put("self", player.getName().getString());
                data.put("block_x", pos.getX());
                data.put("block_y", pos.getY());
                data.put("block_z", pos.getZ());
                data.put("block_id", "unknown");
                data.put("block_name", "unknown");
                data.put("world", level.dimension().location().toString());
                data.put("dimension", level.dimension().location().getPath());
                MaredEventRegistry.fire("block_break", server, data);
            }
        }

        lastHitPos = pos;
        lastHitWasAir = isAir;
    }

    // ============================================================
    //  entity_kill
    // ============================================================

    private static void pollEntityKill(LocalPlayer player, ClientLevel level,
                                        MinecraftServer server) {
        if (PENDING_KILLS.isEmpty()) return;

        TO_REMOVE.clear();
        for (Map.Entry<Integer, EntityInfo> e : PENDING_KILLS.entrySet()) {
            int id = e.getKey();
            EntityInfo info = e.getValue();
            Entity entity = level.getEntity(id);

            if (entity == null || !entity.isAlive() || entity.isRemoved()) {
                TO_REMOVE.add(id);
                if (MaredEventRegistry.has("entity_kill")) {
                    Map<String, Object> data = new HashMap<>(6);
                    data.put("player", player.getName().getString());
                    data.put("self", player.getName().getString());
                    data.put("entity_id", info.id);
                    data.put("entity_name", info.name);
                    MaredEventRegistry.fire("entity_kill", server, data);
                }
            } else {
                info.ticksAlive++;
                if (info.ticksAlive > KILL_TIMEOUT_TICKS) TO_REMOVE.add(id);
            }
        }
        for (int id : TO_REMOVE) PENDING_KILLS.remove(id);
    }

    static void trackAttackTarget(Entity target) {
        if (target == null) return;
        String id;
        try {
            id = target.getType().builtInRegistryHolder().key().location().toString();
        } catch (Throwable t) {
            id = "unknown";
        }
        String name = target.getName().getString();
        PENDING_KILLS.put(target.getId(), new EntityInfo(id, name));
    }

    // ============================================================
    //  entity_hurt
    // ============================================================

    private static void pollEntityHurt(LocalPlayer player, MinecraftServer server) {
        int hp = (int) player.getHealth();
        if (lastHpForHurt == -1) { lastHpForHurt = hp; return; }
        if (hp < lastHpForHurt && MaredEventRegistry.has("entity_hurt")) {
            int delta = lastHpForHurt - hp;
            Map<String, Object> data = new HashMap<>(7);
            data.put("player", player.getName().getString());
            data.put("self", player.getName().getString());
            data.put("damage", delta);
            data.put("hp", hp);
            data.put("max_hp", (int) player.getMaxHealth());
            data.put("damage_type", "unknown");
            data.put("attacker", "");
            MaredEventRegistry.fire("entity_hurt", server, data);
        }
        lastHpForHurt = hp;
    }

    // ============================================================
    //  item_crafted
    // ============================================================

    private static void pollItemCrafted(Minecraft mc, LocalPlayer player,
                                         MinecraftServer server) {
        boolean craftingOpen = player.containerMenu instanceof CraftingMenu;

        if (craftingOpen) {
            CraftingMenu menu = (CraftingMenu) player.containerMenu;
            ItemStack result = menu.getSlot(CraftingMenu.RESULT_SLOT).getItem();

            if (!lastCraftingOpen) {
                lastCraftResult = result.copy();
            } else if (!ItemStack.matches(result, lastCraftResult)) {
                if (!result.isEmpty() && lastCraftResult.isEmpty()
                    && MaredEventRegistry.has("item_crafted")) {
                    Map<String, Object> data = new HashMap<>(6);
                    data.put("player", player.getName().getString());
                    data.put("self", player.getName().getString());
                    try {
                        data.put("item_id",
                            BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
                    } catch (Throwable t) {
                        data.put("item_id", "unknown");
                    }
                    data.put("item_name", result.getHoverName().getString());
                    data.put("item_count", result.getCount());
                    MaredEventRegistry.fire("item_crafted", server, data);
                }
                lastCraftResult = result.copy();
            }
        } else {
            lastCraftingOpen = false;
            lastCraftResult = ItemStack.EMPTY;
        }
        lastCraftingOpen = craftingOpen;
    }

    // ============================================================
    //  Reset
    // ============================================================

    static void reset() {
        lastHitPos = null;
        lastHitWasAir = true;
        PENDING_KILLS.clear();
        TO_REMOVE.clear();
        lastHpForHurt = -1;
        lastCraftingOpen = false;
        lastCraftResult = ItemStack.EMPTY;
    }
}