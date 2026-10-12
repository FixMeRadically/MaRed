package com.fixmer.genesis.technology.minecraft;

import com.fixmer.genesis.technology.scripts.GradleProjectImport;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BooleanSupplier;

/**
 * Phase 2 Minecraft import orchestrator. Extension layer — does not live in the editor.
 * Never rewrites the user's Gradle build files.
 */
public final class MinecraftProjectImporter {
    public record Result(
            SourceProjects.Project project,
            MinecraftProjectInfo info,
            MinecraftImportMode mode,
            Path workingRoot,
            Path genesisDir,
            GradleProjectImport.Summary summary
    ) {}

    private final SourceProjects storage;

    public MinecraftProjectImporter(SourceProjects storage) {
        this.storage = Objects.requireNonNull(storage);
    }

    /** Analyze only (Phase 1). */
    public MinecraftProjectInfo analyze(Path external) throws IOException {
        return MinecraftProjectDetector.analyze(external);
    }

    public Result importProject(String id, Path external, MinecraftImportMode mode, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(id);
        Objects.requireNonNull(external);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(cancelled);
        if (!SourceProjects.validProject(id)) throw new IOException("Invalid project name");
        if (!external.isAbsolute()) throw new IOException("Select an absolute project directory");
        if (cancelled.getAsBoolean()) throw new IOException("Import cancelled");

        MinecraftImportValidator.ValidationResult security = MinecraftImportValidator.validateRoot(external);
        if (!security.ok()) {
            throw new IOException(security.problems().isEmpty() ? "Import validation failed" : security.problems().get(0).message());
        }
        MinecraftProjectInfo info = MinecraftProjectDetector.analyze(external);
        if (cancelled.getAsBoolean()) throw new IOException("Import cancelled");

        return switch (mode) {
            case COPY -> copy(id, external, info, cancelled);
            case LINK -> link(id, external, info, cancelled);
        };
    }

    private Result copy(String id, Path external, MinecraftProjectInfo info, BooleanSupplier cancelled) throws IOException {
        SourceProjects.Imported imported = info.structure().hasGradle()
                ? storage.importGradle(id, external, cancelled)
                : storage.importFolder(id, external, cancelled);
        Path managed = storage.projectDirectory(id);
        mergeManifest(managed, info, MinecraftImportMode.COPY, null);
        Path genesis = MinecraftWorkspaceCreator.write(managed, info, MinecraftImportMode.COPY, null);
        // Also stamp analysis on the managed copy for tooling that looks beside sources.
        return new Result(imported.project(), info, MinecraftImportMode.COPY, managed, genesis, imported.summary());
    }

    private Result link(String id, Path external, MinecraftProjectInfo info, BooleanSupplier cancelled) throws IOException {
        Path real = external.toRealPath();
        Path managedRoot = storage.root().toRealPath();
        if (real.startsWith(managedRoot) || managedRoot.startsWith(real))
            throw new IOException("Link a project outside the Genesis storage tree");
        if (cancelled.getAsBoolean()) throw new IOException("Import cancelled");

        // Write .genesis on the original project (non-destructive metadata only).
        Path genesisOnSource = MinecraftWorkspaceCreator.write(external, info, MinecraftImportMode.LINK, external);

        SourceProjects.Project project = storage.createMinecraftLink(id, external, info);
        Path stub = storage.projectDirectory(id);
        Path genesisOnStub = MinecraftWorkspaceCreator.write(stub, info, MinecraftImportMode.LINK, external);
        return new Result(project, info, MinecraftImportMode.LINK, external.toAbsolutePath().normalize(),
                genesisOnStub, new GradleProjectImport.Summary(0, 0));
    }

    private void mergeManifest(Path project, MinecraftProjectInfo info, MinecraftImportMode mode, Path linked) throws IOException {
        Path manifest = project.resolve("genesis-project.properties");
        Properties props = new Properties();
        if (Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            try (var reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }
        props.setProperty("format", props.getProperty("format", "1"));
        props.setProperty("language", props.getProperty("language", "JAVA"));
        props.putAll(MinecraftWorkspaceCreator.manifestHints(info, mode, linked));
        String text = MinecraftWorkspaceCreator.encodeManifest(props);
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 4096) throw new IOException("Project metadata size limit");
        Path tmp = Files.createTempFile(project, ".manifest-", ".tmp");
        try {
            Files.write(tmp, data, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, manifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static String reportText(MinecraftProjectInfo info) {
        StringBuilder out = new StringBuilder();
        out.append(info.summary()).append('\n');
        out.append("Type: ").append(info.projectType()).append('\n');
        out.append("Loader: ").append(info.loader()).append('\n');
        if (!info.modId().isEmpty()) out.append("Mod ID: ").append(info.modId()).append('\n');
        if (!info.displayName().isEmpty()) out.append("Name: ").append(info.displayName()).append('\n');
        if (!info.version().isEmpty()) out.append("Version: ").append(info.version()).append('\n');
        if (!info.minecraftVersion().isEmpty()) out.append("Minecraft: ").append(info.minecraftVersion()).append('\n');
        if (!info.javaVersion().isEmpty()) out.append("Java: ").append(info.javaVersion()).append('\n');
        if (!info.mainClass().isEmpty()) out.append("Main: ").append(info.mainClass()).append('\n');
        if (!info.description().isEmpty()) out.append("Description: ").append(info.description()).append('\n');
        if (!info.authors().isEmpty()) out.append("Authors: ").append(String.join(", ", info.authors())).append('\n');
        if (!info.suggestedRunTasks().isEmpty())
            out.append("Tasks: ").append(String.join(", ", info.suggestedRunTasks())).append('\n');
        if (!info.dependencies().isEmpty()) {
            out.append("\nDependencies:\n");
            for (String dep : info.dependencies()) out.append(" - ").append(dep).append('\n');
        }
        if (!info.problems().isEmpty()) {
            out.append("\nProblems:\n");
            for (var problem : info.problems()) {
                out.append(" - [").append(problem.severity()).append("] ").append(problem.message());
                if (!problem.solutions().isEmpty()) out.append(" → ").append(problem.solutions());
                out.append('\n');
            }
        }
        out.append("\nRoot: ").append(info.structure().rootPath()).append('\n');
        return out.toString();
    }
}
