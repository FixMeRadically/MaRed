package com.fixmer.mared;

import com.fixmer.genesis.technology.minecraft.MinecraftImportMode;
import com.fixmer.genesis.technology.minecraft.MinecraftLoader;
import com.fixmer.genesis.technology.minecraft.MinecraftProjectDetector;
import com.fixmer.genesis.technology.minecraft.MinecraftProjectImporter;
import com.fixmer.genesis.technology.minecraft.ProjectType;
import com.fixmer.genesis.technology.scripts.NeoForgeProjectTemplate;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class MinecraftProjectImportIntegrationTest {
    @TempDir Path root;

    @Test void detectsNeoForgeStarter() throws Exception {
        Path original = root.resolve("mod");
        Files.createDirectories(original);
        for (Map.Entry<String, String> e : NeoForgeProjectTemplate.files("demomod").entrySet()) {
            if (e.getKey().equals("genesis-project.properties")) continue;
            Path out = original.resolve(e.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, e.getValue());
        }
        var info = MinecraftProjectDetector.analyze(original);
        assertEquals(ProjectType.MINECRAFT_MOD, info.projectType());
        assertEquals(MinecraftLoader.NEOFORGE, info.loader());
        assertEquals("demomod", info.modId());
        assertTrue(info.structure().hasGradle());
        assertTrue(info.suggestedRunTasks().contains("build"));
        assertTrue(info.suggestedRunTasks().contains("runClient"));
    }

    @Test void copyAndLinkImportWriteGenesisWorkspace() throws Exception {
        Path original = root.resolve("external");
        Files.createDirectories(original);
        for (Map.Entry<String, String> e : NeoForgeProjectTemplate.files("portal").entrySet()) {
            if (e.getKey().equals("genesis-project.properties")) continue; // external mods are not Genesis-managed yet
            Path out = original.resolve(e.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, e.getValue());
        }
        var store = new SourceProjects(root.resolve("managed"));
        var importer = new MinecraftProjectImporter(store);

        var copy = importer.importProject("portal_copy", original, MinecraftImportMode.COPY, () -> false);
        assertEquals(MinecraftImportMode.COPY, copy.mode());
        assertTrue(Files.isDirectory(copy.genesisDir()));
        assertTrue(Files.isRegularFile(copy.genesisDir().resolve("project.json")));
        assertTrue(Files.isRegularFile(copy.genesisDir().resolve("workspace.json")));
        assertTrue(Files.isRegularFile(copy.genesisDir().resolve("analysis.json")));
        assertTrue(Files.isRegularFile(copy.genesisDir().resolve("dependencies.json")));
        assertTrue(store.hasGradle("portal_copy"));
        assertTrue(store.importedGradle("portal_copy"));

        var link = importer.importProject("portal_link", original, MinecraftImportMode.LINK, () -> false);
        assertEquals(MinecraftImportMode.LINK, link.mode());
        assertTrue(store.linkedMinecraft("portal_link"));
        assertEquals(original.toRealPath(), store.workingDirectory("portal_link").toRealPath());
        assertTrue(Files.isDirectory(original.resolve(".genesis")));
        assertTrue(Files.isRegularFile(original.resolve(".genesis/project.json")));
        assertTrue(store.files("portal_link").stream().anyMatch(p -> p.endsWith("neoforge.mods.toml")));
    }

    @Test void fabricMetadataIsDetected() throws Exception {
        Path original = root.resolve("fabric");
        Files.createDirectories(original.resolve("src/main/resources"));
        Files.writeString(original.resolve("build.gradle"), "plugins { id 'fabric-loom' version '1.7-SNAPSHOT' }\n");
        Files.writeString(original.resolve("src/main/resources/fabric.mod.json"), """
                {
                  "schemaVersion": 1,
                  "id": "sample",
                  "version": "1.2.3",
                  "name": "Sample Fabric",
                  "entrypoints": { "main": ["com.example.SampleMod"] },
                  "depends": { "minecraft": "~1.21", "fabricloader": ">=0.15" }
                }
                """);
        var info = MinecraftProjectDetector.analyze(original);
        assertEquals(MinecraftLoader.FABRIC, info.loader());
        assertEquals("sample", info.modId());
        assertEquals("com.example.SampleMod", info.mainClass());
        assertEquals(ProjectType.MINECRAFT_MOD, info.projectType());
    }
}
