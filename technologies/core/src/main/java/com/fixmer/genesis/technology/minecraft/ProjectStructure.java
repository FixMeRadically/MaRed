package com.fixmer.genesis.technology.minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable discovery snapshot of an external project tree. */
public record ProjectStructure(
        Path rootPath,
        boolean hasGradle,
        boolean hasMinecraftMetadata,
        List<String> sourceFolders,
        List<String> resourceFolders,
        List<String> modules,
        List<String> buildFiles,
        List<String> metadataFiles,
        boolean hasBuildLibs,
        boolean hasWrapper
) {
    public ProjectStructure {
        Objects.requireNonNull(rootPath);
        sourceFolders = sourceFolders == null ? List.of() : List.copyOf(sourceFolders);
        resourceFolders = resourceFolders == null ? List.of() : List.copyOf(resourceFolders);
        modules = modules == null ? List.of() : List.copyOf(modules);
        buildFiles = buildFiles == null ? List.of() : List.copyOf(buildFiles);
        metadataFiles = metadataFiles == null ? List.of() : List.copyOf(metadataFiles);
    }
}
