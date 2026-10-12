package com.fixmer.genesis.technology.minecraft;

import java.util.List;
import java.util.Objects;

/** Complete Phase-1 analysis of an external Minecraft / Java project. */
public record MinecraftProjectInfo(
        ProjectStructure structure,
        ProjectType projectType,
        MinecraftLoader loader,
        String modId,
        String displayName,
        String version,
        String description,
        List<String> authors,
        String mainClass,
        String minecraftVersion,
        String javaVersion,
        String loaderVersion,
        List<String> dependencies,
        MinecraftGradleAnalysis.GradleSnapshot gradle,
        List<String> suggestedRunTasks,
        List<MinecraftImportProblem> problems,
        MinecraftProjectMetadata metadata
) {
    public MinecraftProjectInfo {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(projectType);
        Objects.requireNonNull(loader);
        modId = Objects.requireNonNullElse(modId, "").trim();
        displayName = Objects.requireNonNullElse(displayName, "").trim();
        version = Objects.requireNonNullElse(version, "").trim();
        description = Objects.requireNonNullElse(description, "").trim();
        authors = authors == null ? List.of() : List.copyOf(authors);
        mainClass = Objects.requireNonNullElse(mainClass, "").trim();
        minecraftVersion = Objects.requireNonNullElse(minecraftVersion, "").trim();
        javaVersion = Objects.requireNonNullElse(javaVersion, "").trim();
        loaderVersion = Objects.requireNonNullElse(loaderVersion, "").trim();
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        gradle = gradle == null ? MinecraftGradleAnalysis.GradleSnapshot.empty() : gradle;
        suggestedRunTasks = suggestedRunTasks == null ? List.of() : List.copyOf(suggestedRunTasks);
        problems = problems == null ? List.of() : List.copyOf(problems);
        metadata = metadata == null
                ? new MinecraftProjectMetadata(loader.name().toLowerCase(), minecraftVersion, javaVersion, modId, mainClass, dependencies)
                : metadata;
    }

    public boolean isMinecraft() {
        return projectType == ProjectType.MINECRAFT_MOD || projectType == ProjectType.MINECRAFT_LIBRARY;
    }

    public String summary() {
        String name = displayName.isEmpty() ? (modId.isEmpty() ? structure.rootPath().getFileName().toString() : modId) : displayName;
        return name + " · " + projectType + " · " + loader + (version.isEmpty() ? "" : " · " + version);
    }
}
