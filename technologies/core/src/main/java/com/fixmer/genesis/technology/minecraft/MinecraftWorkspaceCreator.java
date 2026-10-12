package com.fixmer.genesis.technology.minecraft;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;

/**
 * Writes Genesis `.genesis/` analysis artifacts beside a project without rewriting Gradle.
 */
public final class MinecraftWorkspaceCreator {
    public static final String DIR = ".genesis";

    private MinecraftWorkspaceCreator() {}

    public static Path write(Path projectRoot, MinecraftProjectInfo info, MinecraftImportMode mode, Path linkedSource) throws IOException {
        Objects.requireNonNull(projectRoot);
        Objects.requireNonNull(info);
        Objects.requireNonNull(mode);
        if (!Files.isDirectory(projectRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(projectRoot))
            throw new IOException("Invalid Genesis workspace root");
        Path genesis = projectRoot.resolve(DIR);
        if (Files.exists(genesis, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(genesis, LinkOption.NOFOLLOW_LINKS))
            throw new IOException(".genesis exists and is not a directory");
        Files.createDirectories(genesis);
        if (Files.isSymbolicLink(genesis) || !genesis.toRealPath().startsWith(projectRoot.toRealPath()))
            throw new IOException(".genesis escapes project root");

        atomicWrite(genesis.resolve("project.json"), projectJson(info, mode, linkedSource));
        atomicWrite(genesis.resolve("workspace.json"), workspaceJson(info, mode, linkedSource, projectRoot));
        atomicWrite(genesis.resolve("analysis.json"), analysisJson(info));
        atomicWrite(genesis.resolve("dependencies.json"), dependenciesJson(info));
        return genesis;
    }

    private static String projectJson(MinecraftProjectInfo info, MinecraftImportMode mode, Path linkedSource) {
        return "{\n"
                + "  \"format\": 1,\n"
                + "  \"kind\": \"minecraft\",\n"
                + "  \"projectType\": \"" + esc(info.projectType().name()) + "\",\n"
                + "  \"loader\": \"" + esc(info.loader().name()) + "\",\n"
                + "  \"modId\": \"" + esc(info.modId()) + "\",\n"
                + "  \"displayName\": \"" + esc(info.displayName()) + "\",\n"
                + "  \"version\": \"" + esc(info.version()) + "\",\n"
                + "  \"mainClass\": \"" + esc(info.mainClass()) + "\",\n"
                + "  \"minecraftVersion\": \"" + esc(info.minecraftVersion()) + "\",\n"
                + "  \"javaVersion\": \"" + esc(info.javaVersion()) + "\",\n"
                + "  \"importMode\": \"" + esc(mode.name()) + "\",\n"
                + "  \"linkedSource\": \"" + esc(linkedSource == null ? "" : linkedSource.toString().replace('\\', '/')) + "\"\n"
                + "}\n";
    }

    private static String workspaceJson(MinecraftProjectInfo info, MinecraftImportMode mode, Path linkedSource, Path projectRoot) {
        String sources = join(info.structure().sourceFolders());
        String resources = join(info.structure().resourceFolders());
        String modules = join(info.structure().modules());
        return "{\n"
                + "  \"format\": 1,\n"
                + "  \"root\": \"" + esc(projectRoot.toAbsolutePath().normalize().toString().replace('\\', '/')) + "\",\n"
                + "  \"mode\": \"" + esc(mode.name()) + "\",\n"
                + "  \"workingRoot\": \"" + esc((mode == MinecraftImportMode.LINK && linkedSource != null ? linkedSource : projectRoot).toAbsolutePath().normalize().toString().replace('\\', '/')) + "\",\n"
                + "  \"hasGradle\": " + info.structure().hasGradle() + ",\n"
                + "  \"hasWrapper\": " + info.structure().hasWrapper() + ",\n"
                + "  \"sourceFolders\": [" + sources + "],\n"
                + "  \"resourceFolders\": [" + resources + "],\n"
                + "  \"modules\": [" + modules + "],\n"
                + "  \"suggestedRunTasks\": [" + join(info.suggestedRunTasks()) + "]\n"
                + "}\n";
    }

    private static String analysisJson(MinecraftProjectInfo info) {
        StringBuilder authors = new StringBuilder();
        for (int i = 0; i < info.authors().size(); i++) {
            if (i > 0) authors.append(", ");
            authors.append('"').append(esc(info.authors().get(i))).append('"');
        }
        StringBuilder problems = new StringBuilder();
        for (int i = 0; i < info.problems().size(); i++) {
            if (i > 0) problems.append(",\n");
            var p = info.problems().get(i);
            problems.append("    {\"code\":\"").append(esc(p.code())).append("\",\"severity\":\"")
                    .append(esc(p.severity().name())).append("\",\"message\":\"").append(esc(p.message())).append("\"}");
        }
        return "{\n"
                + "  \"format\": 1,\n"
                + "  \"summary\": \"" + esc(info.summary()) + "\",\n"
                + "  \"description\": \"" + esc(info.description()) + "\",\n"
                + "  \"authors\": [" + authors + "],\n"
                + "  \"loaderVersion\": \"" + esc(info.loaderVersion()) + "\",\n"
                + "  \"gradleVersion\": \"" + esc(info.gradle().version()) + "\",\n"
                + "  \"plugins\": [" + join(info.gradle().plugins()) + "],\n"
                + "  \"problems\": [\n" + problems + "\n  ]\n"
                + "}\n";
    }

    private static String dependenciesJson(MinecraftProjectInfo info) {
        return "{\n"
                + "  \"format\": 1,\n"
                + "  \"dependencies\": [" + join(info.dependencies()) + "],\n"
                + "  \"repositories\": [" + join(info.gradle().repositories()) + "]\n"
                + "}\n";
    }

    /** Compact properties fragment for genesis-project.properties (caller merges). */
    public static Properties manifestHints(MinecraftProjectInfo info, MinecraftImportMode mode, Path linkedSource) {
        Properties p = new Properties();
        p.setProperty("minecraft_loader", info.loader().name());
        p.setProperty("minecraft_project_type", info.projectType().name());
        if (!info.modId().isEmpty()) p.setProperty("mod_id", info.modId());
        if (!info.mainClass().isEmpty()) p.setProperty("main_class", info.mainClass());
        if (!info.minecraftVersion().isEmpty()) p.setProperty("minecraft_version", info.minecraftVersion());
        p.setProperty("workspace", mode == MinecraftImportMode.LINK ? "linked_minecraft" : "imported_gradle");
        if (mode == MinecraftImportMode.LINK && linkedSource != null)
            p.setProperty("linked_path", linkedSource.toAbsolutePath().normalize().toString());
        if (!info.suggestedRunTasks().isEmpty() && info.suggestedRunTasks().contains("build"))
            p.setProperty("gradle_task", "build");
        return p;
    }

    public static String encodeManifest(Properties base) throws IOException {
        var out = new StringWriter();
        base.store(out, "Genesis Minecraft import metadata");
        return out.toString();
    }

    private static void atomicWrite(Path target, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 256 * 1024) throw new IOException("Genesis workspace file exceeds 256 KiB: " + target.getFileName());
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, ".genesis-", ".tmp");
        try {
            Files.write(tmp, data, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String join(java.util.List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append('"').append(esc(values.get(i))).append('"');
        }
        return out.toString();
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
