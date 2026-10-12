package com.fixmer.genesis.technology.minecraft;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Security gate for Minecraft import. Rejects traversal escapes, unsafe links,
 * suspicious payloads, and oversized trees before copy/link.
 */
public final class MinecraftImportValidator {
    public static final int MAX_ENTRIES = 2000;
    public static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;
    public static final long MAX_ARCHIVE_BYTES = 64L * 1024 * 1024;

    private static final Set<String> SUSPICIOUS_NAMES = Set.of(
            "payload.exe", "miner.exe", "crack.exe", "keygen.exe", "autorun.inf");
    private static final Set<String> SUSPICIOUS_EXT = Set.of(
            ".exe", ".dll", ".bat", ".cmd", ".ps1", ".scr", ".vbs", ".js", ".msi", ".com");
    private static final Set<String> ARCHIVE_EXT = Set.of(".zip", ".jar", ".rar", ".7z", ".tar", ".gz");
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".gradle", ".idea", ".vscode", ".settings", "build", "out", "run", ".genesis-build");

    public record ValidationResult(boolean ok, List<MinecraftImportProblem> problems) {
        public ValidationResult {
            problems = problems == null ? List.of() : List.copyOf(problems);
        }
    }

    private MinecraftImportValidator() {}

    public static ValidationResult validateRoot(Path root) {
        Objects.requireNonNull(root);
        List<MinecraftImportProblem> problems = new ArrayList<>();
        try {
            if (!root.isAbsolute()) {
                problems.add(MinecraftImportProblem.error("relative_path",
                        "Use an absolute project path",
                        MinecraftImportProblem.Solution.SELECT_MANUALLY));
                return new ValidationResult(false, problems);
            }
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                problems.add(MinecraftImportProblem.error("not_directory",
                        "Selected path is not a directory",
                        MinecraftImportProblem.Solution.SELECT_MANUALLY));
                return new ValidationResult(false, problems);
            }
            if (Files.isSymbolicLink(root)) {
                problems.add(MinecraftImportProblem.error("symlink_root",
                        "Symbolic link / junction project roots are not allowed",
                        MinecraftImportProblem.Solution.SELECT_MANUALLY));
                return new ValidationResult(false, problems);
            }
            Path real = root.toRealPath();
            scan(root, real, problems);
            boolean fatal = problems.stream().anyMatch(p -> p.severity() == MinecraftImportProblem.Severity.ERROR);
            return new ValidationResult(!fatal, problems);
        } catch (IOException error) {
            problems.add(MinecraftImportProblem.error("io",
                    error.getMessage() == null ? "Unable to validate project" : error.getMessage(),
                    MinecraftImportProblem.Solution.RETRY,
                    MinecraftImportProblem.Solution.SELECT_MANUALLY));
            return new ValidationResult(false, problems);
        }
    }

    private static void scan(Path root, Path realRoot, List<MinecraftImportProblem> problems) throws IOException {
        long[] total = {0};
        int[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                attrs = Files.readAttributes(dir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String name = dir.getFileName().toString();
                if (SKIP_DIRS.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) {
                    problems.add(MinecraftImportProblem.error("symlink",
                            "Unsafe symbolic link refused: " + root.relativize(dir),
                            MinecraftImportProblem.Solution.SELECT_MANUALLY));
                    return FileVisitResult.TERMINATE;
                }
                if (!dir.toRealPath().startsWith(realRoot)) {
                    problems.add(MinecraftImportProblem.error("path_traversal",
                            "Directory escapes project root: " + root.relativize(dir),
                            MinecraftImportProblem.Solution.SELECT_MANUALLY));
                    return FileVisitResult.TERMINATE;
                }
                if (++count[0] > MAX_ENTRIES) {
                    problems.add(MinecraftImportProblem.error("too_many_entries",
                            "Project exceeds " + MAX_ENTRIES + " entries",
                            MinecraftImportProblem.Solution.USE_LINK_MODE,
                            MinecraftImportProblem.Solution.IGNORE));
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.contains("..")) {
                    problems.add(MinecraftImportProblem.error("path_traversal",
                            "Path traversal refused: " + relative,
                            MinecraftImportProblem.Solution.SELECT_MANUALLY));
                    return FileVisitResult.TERMINATE;
                }
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    problems.add(MinecraftImportProblem.error("symlink",
                            "Unsafe symbolic link refused: " + relative,
                            MinecraftImportProblem.Solution.SELECT_MANUALLY));
                    return FileVisitResult.TERMINATE;
                }
                if (!file.toRealPath().startsWith(realRoot)) {
                    problems.add(MinecraftImportProblem.error("path_traversal",
                            "File escapes project root: " + relative,
                            MinecraftImportProblem.Solution.SELECT_MANUALLY));
                    return FileVisitResult.TERMINATE;
                }
                if (++count[0] > MAX_ENTRIES) {
                    problems.add(MinecraftImportProblem.error("too_many_entries",
                            "Project exceeds " + MAX_ENTRIES + " entries",
                            MinecraftImportProblem.Solution.USE_LINK_MODE));
                    return FileVisitResult.TERMINATE;
                }
                if (!attrs.isRegularFile()) {
                    problems.add(MinecraftImportProblem.error("not_regular",
                            "Not a regular file: " + relative,
                            MinecraftImportProblem.Solution.IGNORE));
                    return FileVisitResult.TERMINATE;
                }
                long size = attrs.size();
                String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (SUSPICIOUS_NAMES.contains(lower) || hasSuspiciousExtension(lower, relative)) {
                    problems.add(MinecraftImportProblem.error("suspicious_payload",
                            "Suspicious executable payload refused: " + relative,
                            MinecraftImportProblem.Solution.SELECT_MANUALLY,
                            MinecraftImportProblem.Solution.IGNORE));
                    return FileVisitResult.TERMINATE;
                }
                if (isArchive(lower) && size > MAX_ARCHIVE_BYTES) {
                    problems.add(MinecraftImportProblem.error("huge_archive",
                            "Archive exceeds 64 MiB: " + relative,
                            MinecraftImportProblem.Solution.USE_LINK_MODE,
                            MinecraftImportProblem.Solution.IGNORE));
                    return FileVisitResult.TERMINATE;
                }
                if (size > MAX_FILE_BYTES) {
                    problems.add(MinecraftImportProblem.error("huge_file",
                            "File exceeds 16 MiB: " + relative,
                            MinecraftImportProblem.Solution.USE_LINK_MODE,
                            MinecraftImportProblem.Solution.IGNORE));
                    return FileVisitResult.TERMINATE;
                }
                total[0] += size;
                if (total[0] > MAX_TOTAL_BYTES) {
                    problems.add(MinecraftImportProblem.error("huge_project",
                            "Project exceeds 128 MiB",
                            MinecraftImportProblem.Solution.USE_LINK_MODE));
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean hasSuspiciousExtension(String lowerName, String relative) {
        // Gradle wrapper scripts and shell helpers in repo roots are allowed; nested payload-like names are not.
        if (lowerName.equals("gradlew") || lowerName.equals("gradlew.bat") || lowerName.equals("gradlew.cmd")) return false;
        if (relative.startsWith("gradle/")) return false;
        for (String ext : SUSPICIOUS_EXT) {
            if (lowerName.endsWith(ext)) {
                // Allow common Minecraft/dev scripts only at project root tooling paths.
                if ((ext.equals(".bat") || ext.equals(".cmd") || ext.equals(".ps1") || ext.equals(".sh"))
                        && !relative.contains("/")) return false;
                return true;
            }
        }
        return false;
    }

    private static boolean isArchive(String lowerName) {
        for (String ext : ARCHIVE_EXT) if (lowerName.endsWith(ext)) return true;
        return false;
    }
}
