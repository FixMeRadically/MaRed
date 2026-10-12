package com.fixmer.genesis.technology.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Detects NeoForge / Forge / Fabric / Quilt from Gradle scripts and mod metadata. */
public final class MinecraftLoaderDetector {
    private MinecraftLoaderDetector() {}

    public static MinecraftLoader detect(ProjectStructure structure) throws IOException {
        Objects.requireNonNull(structure);
        Path root = structure.rootPath();
        String gradle = readAll(root, "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties");
        String lower = gradle.toLowerCase(Locale.ROOT);

        boolean neoMeta = exists(root, "src/main/resources/META-INF/neoforge.mods.toml");
        boolean forgeMeta = exists(root, "src/main/resources/META-INF/mods.toml");
        boolean fabricMeta = exists(root, "src/main/resources/fabric.mod.json");
        boolean quiltMeta = exists(root, "src/main/resources/quilt.mod.json");

        boolean neoGradle = lower.contains("net.neoforged.gradle") || lower.contains("neoforge")
                || lower.contains("userdev") && lower.contains("neoforged");
        boolean forgeGradle = lower.contains("net.minecraftforge.gradle") || lower.contains("minecraftforge")
                || (lower.contains("fg.deobf") && !neoGradle);
        boolean fabricGradle = lower.contains("fabric-loom") || lower.contains("net.fabricmc") || lower.contains("fabricmc");
        boolean quiltGradle = lower.contains("org.quiltmc") || lower.contains("quilt-loom") || lower.contains("quiltmc");

        if (neoMeta || neoGradle) return MinecraftLoader.NEOFORGE;
        if (quiltMeta || quiltGradle) return MinecraftLoader.QUILT;
        if (fabricMeta || fabricGradle) return MinecraftLoader.FABRIC;
        if (forgeMeta || forgeGradle) return MinecraftLoader.FORGE;
        return MinecraftLoader.UNKNOWN;
    }

    private static boolean exists(Path root, String relative) {
        return Files.isRegularFile(root.resolve(relative), LinkOption.NOFOLLOW_LINKS);
    }

    private static String readAll(Path root, String... names) throws IOException {
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            Path file = root.resolve(name);
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) out.append(MinecraftGradleAnalysis.read(file)).append('\n');
        }
        return out.toString();
    }
}
