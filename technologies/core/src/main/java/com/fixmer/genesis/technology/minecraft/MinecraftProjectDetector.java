package com.fixmer.genesis.technology.minecraft;

import com.fixmer.genesis.technology.scripts.BuildTools;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Phase 1 file discovery and project-type detection. Does not import or rewrite anything.
 */
public final class MinecraftProjectDetector {
    private static final List<String> BUILD_NAMES = List.of(
            "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties");
    private static final List<String> METADATA_PATHS = List.of(
            "src/main/resources/META-INF/neoforge.mods.toml",
            "src/main/resources/META-INF/mods.toml",
            "src/main/resources/fabric.mod.json",
            "src/main/resources/quilt.mod.json",
            "src/main/resources/META-INF/mods.toml.template");
    private static final List<String> SOURCE_CANDIDATES = List.of(
            "src/main/java", "src/main/kotlin", "src/client/java", "src/common/java", "src/server/java",
            "src/client", "src/common", "src/server", "src");
    private static final List<String> RESOURCE_CANDIDATES = List.of(
            "src/main/resources", "src/client/resources", "src/common/resources", "src/server/resources", "resources");

    private MinecraftProjectDetector() {}

    public static ProjectStructure discover(Path root) throws IOException {
        Objects.requireNonNull(root);
        if (!root.isAbsolute()) throw new IOException("Select an absolute project directory");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("Select a regular project directory");

        List<String> buildFiles = new ArrayList<>();
        for (String name : BUILD_NAMES) {
            if (Files.isRegularFile(root.resolve(name), LinkOption.NOFOLLOW_LINKS)) buildFiles.add(name);
        }
        List<String> metadataFiles = new ArrayList<>();
        for (String path : METADATA_PATHS) {
            if (Files.isRegularFile(root.resolve(path), LinkOption.NOFOLLOW_LINKS)) metadataFiles.add(path);
        }
        List<String> sources = presentDirs(root, SOURCE_CANDIDATES);
        List<String> resources = presentDirs(root, RESOURCE_CANDIDATES);
        List<String> modules = discoverModules(root);
        boolean hasWrapper = Files.isRegularFile(root.resolve("gradle/wrapper/gradle-wrapper.jar"), LinkOption.NOFOLLOW_LINKS)
                || Files.isRegularFile(root.resolve("gradle/wrapper/gradle-wrapper.properties"), LinkOption.NOFOLLOW_LINKS);
        boolean hasBuildLibs = Files.isDirectory(root.resolve("build/libs"), LinkOption.NOFOLLOW_LINKS);
        boolean hasGradle = BuildTools.gradleRoot(root);
        return new ProjectStructure(root.toAbsolutePath().normalize(), hasGradle, !metadataFiles.isEmpty(),
                sources, resources, modules, buildFiles, metadataFiles, hasBuildLibs, hasWrapper);
    }

    public static ProjectType detectType(ProjectStructure structure, MinecraftLoader loader) {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(loader);
        if (looksLikeServerModule(structure)) return ProjectType.SERVER_MODULE;
        if (loader != MinecraftLoader.UNKNOWN || structure.hasMinecraftMetadata()) {
            if (looksLikeLibrary(structure)) return ProjectType.MINECRAFT_LIBRARY;
            return ProjectType.MINECRAFT_MOD;
        }
        if (structure.hasGradle()) {
            if (looksLikeLibrary(structure)) return ProjectType.JAVA_LIBRARY;
            return ProjectType.GRADLE;
        }
        if (!structure.sourceFolders().isEmpty()) return ProjectType.JAVA;
        return ProjectType.UNKNOWN;
    }

    public static MinecraftProjectInfo analyze(Path root) throws IOException {
        MinecraftImportValidator.ValidationResult security = MinecraftImportValidator.validateRoot(root);
        if (!security.ok()) {
            throw new IOException(security.problems().isEmpty()
                    ? "Import validation failed"
                    : security.problems().get(0).message());
        }
        ProjectStructure structure = discover(root);
        MinecraftLoader loader = MinecraftLoaderDetector.detect(structure);
        ProjectType type = detectType(structure, loader);
        MinecraftMetadataReader.ModMetadata meta = MinecraftMetadataReader.read(structure, loader);
        List<String> dependencies = MinecraftDependencyReader.read(structure, loader, meta);
        String mainClass = MinecraftMetadataReader.detectMainClass(structure, loader, meta);
        MinecraftGradleAnalysis.GradleSnapshot gradle = structure.hasGradle()
                ? MinecraftGradleAnalysis.analyze(structure.rootPath())
                : MinecraftGradleAnalysis.GradleSnapshot.empty();
        List<String> runTasks = MinecraftRunTasks.suggest(structure, loader, gradle);
        List<MinecraftImportProblem> problems = new ArrayList<>(security.problems());
        if (type == ProjectType.UNKNOWN)
            problems.add(MinecraftImportProblem.warning("unknown_project",
                    "Could not classify this folder as a Minecraft or Gradle project",
                    MinecraftImportProblem.Solution.SELECT_MANUALLY,
                    MinecraftImportProblem.Solution.IGNORE));
        if (loader == MinecraftLoader.UNKNOWN && structure.hasMinecraftMetadata())
            problems.add(MinecraftImportProblem.warning("unknown_loader",
                    "Minecraft metadata found, but the loader family is unclear",
                    MinecraftImportProblem.Solution.AUTO_DETECT,
                    MinecraftImportProblem.Solution.SELECT_MANUALLY));
        if (meta.modId().isEmpty() && (type == ProjectType.MINECRAFT_MOD || type == ProjectType.MINECRAFT_LIBRARY))
            problems.add(MinecraftImportProblem.warning("missing_mod_id",
                    "Mod id was not found in metadata; Genesis can still import the project",
                    MinecraftImportProblem.Solution.SELECT_MANUALLY,
                    MinecraftImportProblem.Solution.IGNORE));

        String minecraftVersion = firstNonBlank(meta.minecraftVersion(),
                gradle.properties().getProperty("minecraft_version", ""),
                gradle.properties().getProperty("mc_version", ""));
        String javaVersion = firstNonBlank(meta.javaVersion(),
                gradle.properties().getProperty("java_version", ""),
                extractJavaToolchain(gradle));
        String loaderVersion = firstNonBlank(meta.loaderVersion(),
                gradle.properties().getProperty("neo_version", ""),
                gradle.properties().getProperty("forge_version", ""),
                gradle.properties().getProperty("loader_version", ""));
        String version = firstNonBlank(meta.version(), gradle.version());
        MinecraftProjectMetadata metadata = new MinecraftProjectMetadata(
                loader == MinecraftLoader.UNKNOWN ? "" : loader.name().toLowerCase(Locale.ROOT),
                minecraftVersion, javaVersion, meta.modId(), mainClass, dependencies);
        return new MinecraftProjectInfo(structure, type, loader, meta.modId(), meta.displayName(), version,
                meta.description(), meta.authors(), mainClass, minecraftVersion, javaVersion, loaderVersion,
                dependencies, gradle, runTasks, problems, metadata);
    }

    private static boolean looksLikeLibrary(ProjectStructure structure) {
        String leaf = structure.rootPath().getFileName() == null ? "" : structure.rootPath().getFileName().toString().toLowerCase(Locale.ROOT);
        return leaf.contains("api") || leaf.contains("lib") || leaf.endsWith("-common");
    }

    private static boolean looksLikeServerModule(ProjectStructure structure) {
        return Files.isRegularFile(structure.rootPath().resolve("src/WorldModule.java"), LinkOption.NOFOLLOW_LINKS)
                || Files.isRegularFile(structure.rootPath().resolve("libraries/genesis-server-api-v1.jar"), LinkOption.NOFOLLOW_LINKS);
    }

    private static List<String> presentDirs(Path root, List<String> candidates) {
        List<String> result = new ArrayList<>();
        for (String relative : candidates) {
            Path path = root.resolve(relative);
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) result.add(relative);
        }
        return List.copyOf(result);
    }

    private static List<String> discoverModules(Path root) throws IOException {
        Path settings = firstExisting(root, "settings.gradle", "settings.gradle.kts");
        List<String> modules = new ArrayList<>();
        if (settings != null) {
            String text = MinecraftGradleAnalysis.read(settings);
            for (String line : text.split("\\R")) {
                String trimmed = line.strip();
                if (trimmed.startsWith("include")) {
                    for (String token : trimmed.replace("include", "").split("[,()]")) {
                        String name = token.strip().replace("'", "").replace("\"", "").replace(":", "");
                        if (!name.isEmpty() && name.length() <= 64 && modules.size() < 32) modules.add(name);
                    }
                }
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(child)) continue;
                String name = child.getFileName().toString();
                if (name.startsWith(".") || name.equals("build") || name.equals("src") || name.equals("gradle") || name.equals("run")) continue;
                if (BuildTools.gradleRoot(child) && modules.size() < 32 && !modules.contains(name)) modules.add(name);
            }
        }
        modules.sort(Comparator.naturalOrder());
        return List.copyOf(modules);
    }

    private static Path firstExisting(Path root, String... names) {
        for (String name : names) {
            Path path = root.resolve(name);
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return path;
        }
        return null;
    }

    private static String extractJavaToolchain(MinecraftGradleAnalysis.GradleSnapshot gradle) {
        for (String plugin : gradle.plugins()) {
            if (plugin.contains("java")) {/* marker only */}
        }
        String props = gradle.properties().getProperty("org.gradle.java.home", "");
        return props;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }
}
