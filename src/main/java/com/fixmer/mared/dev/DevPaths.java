package com.fixmer.mared.dev;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;

/** Canonical layout for every dev-mode config, state file and supervisor artifact. */
public final class DevPaths {
    private DevPaths() { }
    public static Path root() { return FMLPaths.CONFIGDIR.get().resolve("mared/dev").toAbsolutePath().normalize(); }
    public static Path file(String name) { return root().resolve(name); }
    public static Path logs() { return root().resolve("logs"); }
    public static synchronized void migrateLegacy() throws IOException {
        Files.createDirectories(root()); Files.createDirectories(logs());
        Path legacy = root().getParent();
        for (var entry : Map.of("dev.json", "client.json", "supervisor.properties", "supervisor.properties",
                "dev-worlds.properties", "worlds.properties", "dev-player-state.dat", "player-state.dat",
                ".dev-state", "session.state").entrySet()) {
            Path source = legacy.resolve(entry.getKey()), target = root().resolve(entry.getValue());
            // Keep legacy originals and never overwrite an existing new-format config.
            if (Files.isRegularFile(source) && !Files.exists(target)) {
                Path temp = Files.createTempFile(root(), "migration-", ".tmp");
                try {
                    Files.copy(source,temp,StandardCopyOption.REPLACE_EXISTING);
                    try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException e) { Files.move(temp,target); }
                } finally { Files.deleteIfExists(temp); }
            }
        }
    }
    public static Path state(String current, String legacyName) {
        Path next = file(current);
        Path legacy = root().getParent().resolve(legacyName);
        return Files.isRegularFile(next) || !Files.isRegularFile(legacy) ? next : legacy;
    }
}
