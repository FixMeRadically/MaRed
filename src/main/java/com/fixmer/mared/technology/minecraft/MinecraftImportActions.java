package com.fixmer.mared.technology.minecraft;

import com.fixmer.genesis.technology.minecraft.MinecraftImportMode;
import com.fixmer.genesis.technology.minecraft.MinecraftProjectImporter;
import com.fixmer.genesis.technology.minecraft.MinecraftProjectInfo;
import com.fixmer.genesis.technology.minecraft.MinecraftRunTasks;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * MaRed host adapter for the Genesis Minecraft extension.
 * Keeps import/analysis out of the text editor implementation.
 */
public final class MinecraftImportActions {
    private final MinecraftProjectImporter importer;

    public MinecraftImportActions(SourceProjects storage) {
        this.importer = new MinecraftProjectImporter(Objects.requireNonNull(storage));
    }

    public MinecraftProjectInfo analyze(Path external) throws IOException {
        return importer.analyze(external);
    }

    public MinecraftProjectImporter.Result importCopy(String id, Path external, BooleanSupplier cancelled) throws IOException {
        return importer.importProject(id, external, MinecraftImportMode.COPY, cancelled);
    }

    public MinecraftProjectImporter.Result importLink(String id, Path external, BooleanSupplier cancelled) throws IOException {
        return importer.importProject(id, external, MinecraftImportMode.LINK, cancelled);
    }

    public static String analysisReport(MinecraftProjectInfo info) {
        return MinecraftProjectImporter.reportText(info);
    }

    public static List<String> runTasks(MinecraftProjectInfo info) {
        return MinecraftRunTasks.suggest(info.structure(), info.loader(), info.gradle());
    }
}
