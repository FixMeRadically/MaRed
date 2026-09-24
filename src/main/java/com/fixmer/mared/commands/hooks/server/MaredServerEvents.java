package com.fixmer.mared.commands.hooks.server;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.fixmer.mared.commands.engine.MaredScriptRunner;
import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.input.MaredBindRegistry;
import com.fixmer.mared.commands.input.MaredKeyBlocker;
import com.fixmer.mared.commands.storage.MaredGlobalStorage;
import com.fixmer.mared.MaredTicks;

@EventBusSubscriber(modid = Mared.MOD_ID)
public class MaredServerEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MaredTicks.increment();
        MaredScriptRunner.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());
        data.put("dimension", sp.level().dimension().location().getPath());
        data.put("world", sp.level().dimension().location().toString());

        if (MaredEventRegistry.has("join")) {
            Mared.LOGGER.info("[Mared] PlayerLoggedInEvent — firing 'join' for {}",
                sp.getName().getString());
            MaredEventRegistry.fire("join", server, data);
        } else {
            Mared.LOGGER.info("[Mared] PlayerLoggedInEvent — no 'join' handlers registered");
        }

        // on_first_join — только если у игрока ещё нет метки
        if (MaredEventRegistry.has("first_join")) {
            String uuid = sp.getUUID().toString();
            String key = "global.__first_join_" + uuid;
            if (!MaredGlobalStorage.has(key)) {
                MaredGlobalStorage.set(key, Boolean.TRUE);
                Mared.LOGGER.info("[Mared] first_join — firing for {}", sp.getName().getString());
                MaredEventRegistry.fire("first_join", server, data);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!MaredEventRegistry.has("block_break")) return;

        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer sp)) return;

        Level level = sp.level();
        if (level == null) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        MinecraftServer server = sp.getServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        data.put("block_x", pos.getX());
        data.put("block_y", pos.getY());
        data.put("block_z", pos.getZ());

        String blockId;
        try {
            blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        } catch (Throwable t) {
            blockId = "unknown";
        }
        data.put("block_id", blockId);
        data.put("block_name", state.getBlock().getName().getString());

        String dimFull = level.dimension().location().toString();
        data.put("world", dimFull);
        data.put("dimension", level.dimension().location().getPath());

        MaredEventRegistry.fire("block_break", server, data);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!MaredEventRegistry.has("block_place")) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        Level level = sp.level();
        if (level == null) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();

        MinecraftServer server = sp.getServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        data.put("block_x", pos.getX());
        data.put("block_y", pos.getY());
        data.put("block_z", pos.getZ());

        String blockId;
        try {
            blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        } catch (Throwable t) {
            blockId = "unknown";
        }
        data.put("block_id", blockId);
        data.put("block_name", state.getBlock().getName().getString());

        data.put("world", level.dimension().location().toString());
        data.put("dimension", level.dimension().location().getPath());

        MaredEventRegistry.fire("block_place", server, data);
    }

    @SubscribeEvent
    public static void onEntityKill(LivingDeathEvent event) {
        if (!MaredEventRegistry.has("entity_kill")) return;
        if (event.getSource() == null) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;

        var dead = event.getEntity();
        MinecraftServer server = sp.getServer();

        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        data.put("entity_id", dead.getType().builtInRegistryHolder().key().location().toString());
        data.put("entity_name", dead.getName().getString());
        data.put("entity_x", (int) dead.getX());
        data.put("entity_y", (int) dead.getY());
        data.put("entity_z", (int) dead.getZ());

        data.put("world", sp.level().dimension().location().toString());
        data.put("dimension", sp.level().dimension().location().getPath());

        MaredEventRegistry.fire("entity_kill", server, data);
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (!MaredEventRegistry.has("entity_hurt")) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        data.put("damage", event.getAmount());
        data.put("hp", (int) sp.getHealth());
        data.put("max_hp", (int) sp.getMaxHealth());

        if (event.getSource() != null) {
            try {
                data.put("damage_type", event.getSource().getMsgId());
            } catch (Throwable t) {
                data.put("damage_type", "unknown");
            }
            if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
                data.put("attacker", attacker.getName().getString());
            } else if (event.getSource().getEntity() != null) {
                data.put("attacker", event.getSource().getEntity().getName().getString());
            }
        }

        data.put("world", sp.level().dimension().location().toString());
        data.put("dimension", sp.level().dimension().location().getPath());

        MaredEventRegistry.fire("entity_hurt", server, data);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!MaredEventRegistry.has("player_death")) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        data.put("x", (int) sp.getX());
        data.put("y", (int) sp.getY());
        data.put("z", (int) sp.getZ());

        if (event.getSource() != null) {
            try {
                data.put("death_cause", event.getSource().getMsgId());
            } catch (Throwable t) {
                data.put("death_cause", "unknown");
            }
            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                data.put("killer", killer.getName().getString());
            }
        }

        data.put("world", sp.level().dimension().location().toString());
        data.put("dimension", sp.level().dimension().location().getPath());

        MaredEventRegistry.fire("player_death", server, data);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!MaredEventRegistry.has("respawn")) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();
        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        data.put("x", (int) sp.getX());
        data.put("y", (int) sp.getY());
        data.put("z", (int) sp.getZ());

        data.put("world", sp.level().dimension().location().toString());
        data.put("dimension", sp.level().dimension().location().getPath());

        MaredEventRegistry.fire("respawn", server, data);
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!MaredEventRegistry.has("item_pickup")) return;
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;

        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity == null) return;

        ItemStack stack = itemEntity.getItem();
        MinecraftServer server = sp.getServer();

        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        String itemId;
        try {
            itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable t) {
            itemId = "unknown";
        }
        data.put("item_id", itemId);
        data.put("item_name", stack.getHoverName().getString());
        data.put("item_count", stack.getCount());

        data.put("world", sp.level().dimension().location().toString());
        data.put("dimension", sp.level().dimension().location().getPath());

        MaredEventRegistry.fire("item_pickup", server, data);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!MaredEventRegistry.has("item_crafted")) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        ItemStack stack = event.getCrafting();
        MinecraftServer server = sp.getServer();

        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        String itemId;
        try {
            itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable t) {
            itemId = "unknown";
        }
        data.put("item_id", itemId);
        data.put("item_name", stack.getHoverName().getString());
        data.put("item_count", stack.getCount());

        data.put("world", sp.level().dimension().location().toString());
        data.put("dimension", sp.level().dimension().location().getPath());

        MaredEventRegistry.fire("item_crafted", server, data);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!MaredEventRegistry.has("dimension_change")) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();

        ResourceLocation fromRl = event.getFrom().location();
        ResourceLocation toRl = event.getTo().location();

        Map<String, Object> data = new HashMap<>();
        data.put("player", sp.getName().getString());
        data.put("self", sp.getName().getString());
        data.put("uuid", sp.getUUID().toString());

        data.put("from_dimension", fromRl.getPath());
        data.put("from_world", fromRl.toString());
        data.put("to_dimension", toRl.getPath());
        data.put("to_world", toRl.toString());

        data.put("world", toRl.toString());
        data.put("dimension", toRl.getPath());

        MaredEventRegistry.fire("dimension_change", server, data);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MaredScriptRunner.stopAll();
        MaredGlobalStorage.clear();
        MaredEventRegistry.clearAll();
        MaredBindRegistry.clearAll();
        MaredKeyBlocker.clear();
        MaredTicks.reset();
    }
}