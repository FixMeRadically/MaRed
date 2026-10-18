package com.fixmer.mared.dev.world;

import com.fixmer.mared.Mared;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Шаг 5: сохранение и восстановление состояния игрока.
 *
 * Файл: config/mared/dev-player-state.dat (бинарный NBT).
 *
 * Восстановление пропускается, если мир или UUID игрока не совпадают.
 * Позиция применяется через teleportTo(), чтобы клиент получил
 * корректный пакет синхронизации и не завис на "Loading terrain".
 */
public final class DevPlayerState {

    private DevPlayerState() {}

    private static final int    VERSION        = 1;
    private static final String FILE_NAME      = "dev-player-state.dat";
    private static final long   MAX_FILE_BYTES = 1L * 1024 * 1024;

    // ============================================================
    //  Capture
    // ============================================================

    public static boolean capture(ServerPlayer player, MinecraftServer server) {
        try {
            Path file = filePath();
            if (file == null) return false;

            String worldId = worldId(server);
            if (worldId.isEmpty()) return false;

            CompoundTag root = new CompoundTag();
            root.putInt("version", VERSION);
            root.putString("worldId", worldId);
            root.putString("playerUUID", player.getUUID().toString());
            root.putString("dimension",
                player.level().dimension().location().toString());
            root.putLong("savedAt", System.currentTimeMillis());
            root.put("player", player.saveWithoutId(new CompoundTag()));

            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream os = Files.newOutputStream(tmp)) {
                NbtIo.writeCompressed(root, os);
            }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception t) {
            Mared.LOGGER.warn("[Mared] cannot capture player state", t);
            return false;
        }
    }

    // ============================================================
    //  Restore
    // ============================================================

    public static void restore(ServerPlayer player, MinecraftServer server) {
        try {
            Path file = filePath();
            if (file == null) {
                Mared.LOGGER.info("[Mared] dev: state file path unavailable");
                return;
            }
            if (!Files.isRegularFile(file)) {
                Mared.LOGGER.info(
                    "[Mared] dev: no saved state at {}, skipping restore", file);
                return;
            }

            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                Mared.LOGGER.warn(
                    "[Mared] dev: state file too large ({} bytes), skipping", size);
                return;
            }

            CompoundTag root;
            try (InputStream is = Files.newInputStream(file)) {
                root = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            }

            if (root.getInt("version") != VERSION) {
                Mared.LOGGER.warn(
                    "[Mared] dev: unsupported state version, skipping");
                return;
            }

            String savedWorldId = root.getString("worldId");
            String currentWorldId = worldId(server);
            if (!savedWorldId.equals(currentWorldId)) {
                Mared.LOGGER.info(
                    "[Mared] dev: state for another world ('{}' vs '{}'), skipping",
                    savedWorldId, currentWorldId);
                return;
            }

            String savedUuid = root.getString("playerUUID");
            if (!savedUuid.isEmpty() && !savedUuid.equals(player.getUUID().toString())) {
                Mared.LOGGER.info(
                    "[Mared] dev: state for another player ({}), skipping", savedUuid);
                return;
            }

            String savedDimension = root.getString("dimension");
            String currentDimension = player.level().dimension().location().toString();
            boolean sameDimension = savedDimension.equals(currentDimension);

            CompoundTag playerTag = root.getCompound("player").copy();

            playerTag.remove("Pos");
            playerTag.remove("Rotation");
            playerTag.remove("Motion");
            playerTag.remove("UUID");
            playerTag.remove("Dimension");
            playerTag.remove("WorldUUIDLeast");
            playerTag.remove("WorldUUIDMost");

            try {
                player.load(playerTag);
            } catch (Throwable t) {
                Mared.LOGGER.warn("[Mared] dev: partial state restore failed", t);
            }

            if (sameDimension) {
                ListTag pos = root.getCompound("player").getList("Pos", 6);
                ListTag rot = root.getCompound("player").getList("Rotation", 5);
                if (pos.size() == 3 && rot.size() == 2) {
                    double x = pos.getDouble(0);
                    double y = pos.getDouble(1);
                    double z = pos.getDouble(2);
                    float yaw = rot.getFloat(0);
                    float pitch = rot.getFloat(1);
                    player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
                    Mared.LOGGER.info(
                        "[Mared] dev: state restored — pos=({}, {}, {}), rot=({}, {})",
                        x, y, z, yaw, pitch);
                } else {
                    Mared.LOGGER.warn(
                        "[Mared] dev: malformed Pos/Rotation, position not restored");
                }
            } else {
                Mared.LOGGER.info(
                    "[Mared] dev: state restored without position "
                    + "(dimension changed: {} -> {})",
                    savedDimension, currentDimension);
            }
        } catch (Throwable t) {
            Mared.LOGGER.warn("[Mared] cannot restore player state", t);
        }
    }

    // ============================================================
    //  Clear
    // ============================================================

    public static void clear() {
        Path file = filePath();
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private static String worldId(MinecraftServer server) {
        try {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = root.getFileName();
            return name == null ? "" : name.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static Path filePath() {
        try {
            return com.fixmer.mared.dev.DevPaths.state("player-state.dat", FILE_NAME);
        } catch (Throwable t) {
            return null;
        }
    }
}