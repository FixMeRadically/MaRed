package com.fixmer.genesis.technology.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects declared mod / Maven dependencies from metadata and Gradle text. */
public final class MinecraftDependencyReader {
    private static final Pattern TOML_DEP = Pattern.compile(
            "modId\\s*=\\s*\"([^\"]+)\"[\\s\\S]*?(?:type|mandatory)\\s*=\\s*\"?([^\"]+)\"?[\\s\\S]*?versionRange\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FABRIC_DEP = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");

    private MinecraftDependencyReader() {}

    public static List<String> read(ProjectStructure structure, MinecraftLoader loader, MinecraftMetadataReader.ModMetadata meta) throws IOException {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(loader);
        Set<String> deps = new LinkedHashSet<>();
        Path root = structure.rootPath();
        switch (loader) {
            case NEOFORGE -> readTomlDeps(root.resolve("src/main/resources/META-INF/neoforge.mods.toml"), deps);
            case FORGE -> readTomlDeps(root.resolve("src/main/resources/META-INF/mods.toml"), deps);
            case FABRIC -> readJsonDeps(root.resolve("src/main/resources/fabric.mod.json"), deps);
            case QUILT -> readJsonDeps(root.resolve("src/main/resources/quilt.mod.json"), deps);
            case UNKNOWN -> {
                for (String path : structure.metadataFiles()) {
                    Path file = root.resolve(path);
                    if (path.endsWith(".toml")) readTomlDeps(file, deps);
                    else if (path.endsWith(".json")) readJsonDeps(file, deps);
                }
            }
        }
        if (structure.hasGradle()) {
            MinecraftGradleAnalysis.GradleSnapshot gradle = MinecraftGradleAnalysis.analyze(root);
            deps.addAll(gradle.dependencies());
        }
        if (meta != null && !meta.modId().isEmpty()) deps.removeIf(d -> d.toLowerCase(Locale.ROOT).contains("modid=" + meta.modId().toLowerCase(Locale.ROOT)));
        List<String> result = new ArrayList<>(deps);
        if (result.size() > 128) return List.copyOf(result.subList(0, 128));
        return List.copyOf(result);
    }

    private static void readTomlDeps(Path file, Set<String> deps) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        String text = MinecraftGradleAnalysis.read(file);
        Matcher m = TOML_DEP.matcher(text);
        while (m.find() && deps.size() < 128) {
            deps.add("mod:" + m.group(1) + ":" + m.group(3) + " (" + m.group(2) + ")");
        }
        // Also accept simple adjacent declarations
        Matcher simple = Pattern.compile("\\[\\[dependencies\\.[^]]+]]\\s*modId\\s*=\\s*\"([^\"]+)\"\\s*versionRange\\s*=\\s*\"([^\"]+)\"").matcher(text);
        while (simple.find() && deps.size() < 128) deps.add("mod:" + simple.group(1) + ":" + simple.group(2));
    }

    private static void readJsonDeps(Path file, Set<String> deps) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        String text = MinecraftGradleAnalysis.read(file);
        Matcher block = Pattern.compile("\"(?:depends|depends_overrides|breaking)\"\\s*:\\s*\\{([^}]*)}").matcher(text);
        while (block.find()) {
            Matcher item = FABRIC_DEP.matcher(block.group(1));
            while (item.find() && deps.size() < 128) deps.add("mod:" + item.group(1) + ":" + item.group(2));
        }
    }
}
