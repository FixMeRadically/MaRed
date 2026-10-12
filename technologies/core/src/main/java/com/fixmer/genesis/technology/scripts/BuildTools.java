package com.fixmer.genesis.technology.scripts;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

/** Static local tool inspection. Does not launch executables, download Gradle or load JAR classes. */
public final class BuildTools {
    private static final int MAX_PROPERTIES_BYTES = 16 * 1024;
    private static final long MAX_WRAPPER_BYTES = 8L * 1024 * 1024;

    private BuildTools() {}

    public record Settings(Path javaHome, Path wrapperJar) {
        public Settings {
            Objects.requireNonNull(javaHome);
            Objects.requireNonNull(wrapperJar);
            if (!javaHome.isAbsolute() || !wrapperJar.isAbsolute())
                throw new IllegalArgumentException("Build tool paths must be absolute");
            javaHome = javaHome.normalize();
            wrapperJar = wrapperJar.normalize();
        }
    }

    /** Accept copied Explorer paths (quoted paths included); no shell expansion. */
    public static String cleanPath(String value) {
        if(value==null)return "";
        value=value.trim();
        if(value.length()>=2 && value.startsWith("\"") && value.endsWith("\""))value=value.substring(1,value.length()-1);
        return value;
    }
    public static Path javaHome(String value) throws IOException {
        value=cleanPath(value);
        if(!validAbsolutePath(value))throw new IOException("JDK: use an absolute local path to a full JDK 21 or newer");
        Path home=Path.of(value).normalize();String leaf=home.getFileName()==null?"":home.getFileName().toString();
        if(Set.of("java","java.exe","javac","javac.exe").contains(leaf.toLowerCase(Locale.ROOT)))home=home.getParent();
        if(home!=null && home.getFileName()!=null && home.getFileName().toString().equalsIgnoreCase("bin"))home=home.getParent();
        validateJavaHome(home);return home;
    }
    public static Path wrapperJar(String value) throws IOException {
        value=cleanPath(value);
        if(!validAbsolutePath(value))throw new IOException("Gradle: select gradle-wrapper.jar or its project folder");
        Path jar=Path.of(value).normalize();
        if(Files.isDirectory(jar)) {
            Path nested=jar.resolve("gradle/wrapper/gradle-wrapper.jar");
            jar=(Files.exists(nested,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.exists(nested.resolveSibling("gradle-wrapper.properties"),java.nio.file.LinkOption.NOFOLLOW_LINKS))?nested:jar.resolve("gradle-wrapper.jar");
        } else if(jar.getFileName()!=null && jar.getFileName().toString().equalsIgnoreCase("gradle-wrapper.properties"))jar=jar.resolveSibling("gradle-wrapper.jar");
        validateWrapper(jar);return jar;
    }
    public static boolean validAbsolutePath(String value) {
        try {
            value=cleanPath(value);return !value.isBlank() && value.length() <= 2048 && value.chars().noneMatch(c->c<32) && Path.of(value).isAbsolute();
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    /** A multi-project root may contain settings only and build files in its subprojects. */
    public static boolean gradleRoot(Path directory){
        for(String name:java.util.List.of("build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts"))
            if(Files.isRegularFile(directory.resolve(name),java.nio.file.LinkOption.NOFOLLOW_LINKS))return true;
        return false;
    }
    public static void validate(Settings settings) throws IOException {
        Objects.requireNonNull(settings);
        validateJavaHome(settings.javaHome());
        validateWrapper(settings.wrapperJar());
    }

    public static int javaFeature(Path home)throws IOException{var value=properties(home.resolve("release")).getProperty("JAVA_VERSION","");var m=java.util.regex.Pattern.compile("\"?(\\d+).*?").matcher(value);if(!m.matches())throw new IOException("Invalid JDK version");try{return Integer.parseInt(m.group(1));}catch(NumberFormatException e){throw new IOException("Invalid JDK version",e);}}
    public static void validateCompilerForHost(Path home)throws IOException{int host=Runtime.version().feature(),compiler=javaFeature(home);if(compiler<host)throw new IOException("Selected compiler JDK "+compiler+" cannot compile for running JVM "+host+"; choose JDK "+host+" or newer (target remains host-compatible)");}
    public static void validateJavaHome(Path home) throws IOException {
        requireAbsolute(home);
        String extension = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? ".exe" : "";
        for (String name : new String[]{"java", "javac"}) {
            Path executable = home.resolve("bin").resolve(name + extension);
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable))
                throw new IOException("Full JDK required: missing executable " + executable);
        }
        String version = properties(home.resolve("release")).getProperty("JAVA_VERSION", "");
        var matcher = java.util.regex.Pattern.compile("\"?(\\d+)(?:[.\\-+][^\"\\r\\n]*)?\"?").matcher(version);
        try {
            if (!matcher.matches() || Integer.parseInt(matcher.group(1)) < 21)
                throw new IOException("JDK 21 or newer required; JAVA_VERSION=" + version);
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid JDK release version", invalid);
        }
    }

    public static void validateWrapper(Path jar) throws IOException {
        requireAbsolute(jar);
        if (!Files.isRegularFile(jar) || Files.size(jar) > MAX_WRAPPER_BYTES)
            throw new IOException("Gradle Wrapper JAR is missing or exceeds 8 MiB: " + jar + ". Select gradle-wrapper.jar, not gradlew.bat; gradle-wrapper.properties must be beside it");
        try (var archive = new JarFile(jar.toFile(), false)) {
            var main = archive.getJarEntry("org/gradle/wrapper/GradleWrapperMain.class");
            if (main == null || main.isDirectory())
                throw new IOException("Selected JAR does not contain GradleWrapperMain");
        }
        Properties props = properties(jar.resolveSibling("gradle-wrapper.properties"));
        String distribution = props.getProperty("distributionUrl", "");
        try {
            URI uri = URI.create(distribution);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || scheme == null || !Set.of("https", "http", "file").contains(scheme.toLowerCase(Locale.ROOT))
                    || !scheme.equalsIgnoreCase("file") && (uri.getHost() == null || uri.getHost().isBlank())
                    || uri.getPath() == null || uri.getPath().isBlank())
                throw new IllegalArgumentException("Expected absolute distribution URL");
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid or missing distributionUrl in gradle-wrapper.properties", invalid);
        }
        String checksum = props.getProperty("distributionSha256Sum");
        if (checksum != null && !checksum.matches("[0-9a-fA-F]{64}"))
            throw new IOException("distributionSha256Sum must contain 64 hexadecimal characters");
    }

    private static void requireAbsolute(Path path) throws IOException {
        if (path == null || !validAbsolutePath(path.toString()))
            throw new IOException("Use an absolute local path");
    }

    private static Properties properties(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Missing tool properties: " + file);
        byte[] data;
        try (var input = Files.newInputStream(file)) {
            data = input.readNBytes(MAX_PROPERTIES_BYTES + 1);
        }
        if (data.length > MAX_PROPERTIES_BYTES) throw new IOException("Tool properties exceed 16 KiB: " + file);
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        var result = new Properties(){@Override public synchronized Object put(Object key,Object value){
            if(containsKey(key))throw new IllegalArgumentException("Duplicate tool property: "+key);return super.put(key,value);
        }};
        try {
            result.load(new StringReader(decoder.decode(ByteBuffer.wrap(data)).toString()));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Malformed tool properties: " + file, invalid);
        }
        return result;
    }
}
