package com.fixmer.genesis.technology.minecraft;

import java.util.List;
import java.util.Objects;

/**
 * Metadata only. This class intentionally contains no filesystem, Gradle,
 * Minecraft loader or runtime logic. It is an extension point for Minecraft
 * projects without changing SourceProjects.
 */
public record MinecraftProjectMetadata(
        String loader,
        String minecraftVersion,
        String javaVersion,
        String modId,
        String mainClass,
        List<String> dependencies
) {
    public MinecraftProjectMetadata {
        loader = normalize(loader);
        minecraftVersion = normalize(minecraftVersion);
        javaVersion = normalize(javaVersion);
        modId = normalize(modId);
        mainClass = normalize(mainClass);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    public boolean isMinecraftMod() {
        return !loader.isEmpty() || !minecraftVersion.isEmpty() || !modId.isEmpty();
    }
}
