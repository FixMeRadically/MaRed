import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** Real JDK file contract used by BootstrapLauncher; not a Minecraft launch test. */
public final class LegacyClasspathChecks64 {
    public static void main(String[] args) throws Exception {
        var entries = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        if (entries.size() != Integer.parseInt(args[1]))
            throw new AssertionError("Wrong number of individual classpath entries");
        for (String entry : entries) {
            if (entry.isBlank() || entry.contains("{profile}") || !Files.exists(Path.of(entry)))
                throw new AssertionError("Classpath entry is unresolved or missing");
        }
        System.out.println("Actual Files.readAllLines classpath check passed: " + entries.size());
    }
}
