package com.fixmer.genesis.technology.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Suggests Gradle run/build tasks for Minecraft loaders without executing Gradle. */
public final class MinecraftRunTasks {
    private MinecraftRunTasks() {}

    public static List<String> suggest(ProjectStructure structure, MinecraftLoader loader, MinecraftGradleAnalysis.GradleSnapshot gradle) {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(loader);
        Objects.requireNonNull(gradle);
        Set<String> tasks = new LinkedHashSet<>();
        tasks.add("build");
        for (String declared : gradle.declaredTasks()) {
            String lower = declared.toLowerCase(Locale.ROOT);
            if (lower.contains("runclient") || lower.equals("client") || declared.equals("runClient")) tasks.add(normalize(declared, "runClient"));
            if (lower.contains("runserver") || lower.equals("server") || declared.equals("runServer")) tasks.add(normalize(declared, "runServer"));
            if (lower.contains("gametest") || lower.contains("rungametest")) tasks.add(normalize(declared, "runGameTestServer"));
            if (lower.contains("rundata") || lower.equals("data")) tasks.add(normalize(declared, "runData"));
            if (lower.contains("debug")) tasks.add(declared);
        }
        switch (loader) {
            case NEOFORGE, FORGE -> {
                tasks.add("runClient");
                tasks.add("runServer");
                tasks.add("runGameTestServer");
                tasks.add("runData");
            }
            case FABRIC, QUILT -> {
                tasks.add("runClient");
                tasks.add("runServer");
            }
            case UNKNOWN -> { }
        }
        if (!structure.hasGradle()) return List.of();
        return List.copyOf(new ArrayList<>(tasks));
    }

    private static String normalize(String declared, String canonical) {
        if (declared.equalsIgnoreCase(canonical) || declared.equalsIgnoreCase(canonical.substring(3))) return canonical;
        return declared;
    }
}
