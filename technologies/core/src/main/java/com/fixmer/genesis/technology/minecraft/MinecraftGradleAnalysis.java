package com.fixmer.genesis.technology.minecraft;

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only Gradle text analysis. Never rewrites build scripts or evaluates Gradle.
 */
public final class MinecraftGradleAnalysis {
    public static final int MAX_FILE_BYTES = 512 * 1024;

    public record GradleSnapshot(
            String version,
            List<String> plugins,
            List<String> dependencies,
            List<String> repositories,
            List<String> declaredTasks,
            Properties properties
    ) {
        public GradleSnapshot {
            version = Objects.requireNonNullElse(version, "").trim();
            plugins = plugins == null ? List.of() : List.copyOf(plugins);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            repositories = repositories == null ? List.of() : List.copyOf(repositories);
            declaredTasks = declaredTasks == null ? List.of() : List.copyOf(declaredTasks);
            properties = copy(properties);
        }

        public static GradleSnapshot empty() {
            return new GradleSnapshot("", List.of(), List.of(), List.of(), List.of(), new Properties());
        }

        private static Properties copy(Properties source) {
            Properties out = new Properties();
            if (source != null) out.putAll(source);
            return out;
        }
    }

    private static final Pattern PLUGIN_ID = Pattern.compile("id\\s*[('\"]([^'\")]+)['\")]");
    private static final Pattern DEPENDENCY = Pattern.compile(
            "(implementation|api|compileOnly|runtimeOnly|modImplementation|modApi|modCompileOnly|minecraft|neoForge|forge)\\s*[(\\[]?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern REPOSITORY = Pattern.compile(
            "maven\\s*\\{[^}]*url\\s*=?\\s*(?:uri\\()?['\"](https?://[^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TASK = Pattern.compile("(?:tasks\\.)?(?:named|register|create)\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern RUNS = Pattern.compile("(?m)^\\s*(client|server|gameTestServer|data|clientData)\\s*\\{");

    private MinecraftGradleAnalysis() {}

    public static GradleSnapshot analyze(Path root) throws IOException {
        Objects.requireNonNull(root);
        Properties properties = new Properties();
        Path props = root.resolve("gradle.properties");
        if (Files.isRegularFile(props, LinkOption.NOFOLLOW_LINKS)) {
            String text = read(props);
            try {
                properties.load(new java.io.StringReader(text));
            } catch (IllegalArgumentException ignored) {
                /* keep empty; caller surfaces soft warnings elsewhere */
            }
        }
        StringBuilder scripts = new StringBuilder();
        for (String name : List.of("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")) {
            Path file = root.resolve(name);
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) scripts.append(read(file)).append('\n');
        }
        String body = scripts.toString();
        Set<String> plugins = new LinkedHashSet<>();
        Matcher pluginMatcher = PLUGIN_ID.matcher(body);
        while (pluginMatcher.find() && plugins.size() < 64) plugins.add(pluginMatcher.group(1).trim());

        Set<String> dependencies = new LinkedHashSet<>();
        Matcher depMatcher = DEPENDENCY.matcher(body);
        while (depMatcher.find() && dependencies.size() < 128) {
            dependencies.add(depMatcher.group(1) + " " + depMatcher.group(2).trim());
        }

        Set<String> repositories = new LinkedHashSet<>();
        if (body.toLowerCase(Locale.ROOT).contains("mavenCentral()")) repositories.add("mavenCentral()");
        Matcher repoMatcher = REPOSITORY.matcher(body);
        while (repoMatcher.find() && repositories.size() < 32) repositories.add(repoMatcher.group(1).trim());

        Set<String> tasks = new LinkedHashSet<>();
        Matcher taskMatcher = TASK.matcher(body);
        while (taskMatcher.find() && tasks.size() < 64) tasks.add(taskMatcher.group(1).trim());
        Matcher runMatcher = RUNS.matcher(body);
        while (runMatcher.find() && tasks.size() < 64) {
            String run = runMatcher.group(1);
            tasks.add("run" + Character.toUpperCase(run.charAt(0)) + run.substring(1));
        }
        for (String common : List.of("build", "clean", "jar", "runClient", "runServer", "runGameTestServer", "genIntellijRuns")) {
            if (body.contains(common) || body.contains("'" + common + "'") || body.contains("\"" + common + "\""))
                tasks.add(common);
        }

        String version = firstNonBlank(
                properties.getProperty("mod_version"),
                properties.getProperty("version"),
                extractAssignment(body, "version")
        );
        return new GradleSnapshot(version, new ArrayList<>(plugins), new ArrayList<>(dependencies),
                new ArrayList<>(repositories), new ArrayList<>(tasks), properties);
    }

    private static String extractAssignment(String body, String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*['\"]?([^'\\\"\\n]+)").matcher(body);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return "";
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) throw new IOException("Gradle file exceeds analysis limit: " + file.getFileName());
        byte[] bytes = Files.readAllBytes(file);
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
