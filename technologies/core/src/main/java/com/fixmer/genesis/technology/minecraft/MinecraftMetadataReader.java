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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads mod metadata and main-class hints without executing project code. */
public final class MinecraftMetadataReader {
    public record ModMetadata(
            String modId,
            String displayName,
            String version,
            String description,
            List<String> authors,
            String minecraftVersion,
            String javaVersion,
            String loaderVersion,
            String entrypointHint
    ) {
        public ModMetadata {
            modId = Objects.requireNonNullElse(modId, "").trim();
            displayName = Objects.requireNonNullElse(displayName, "").trim();
            version = Objects.requireNonNullElse(version, "").trim();
            description = Objects.requireNonNullElse(description, "").trim();
            authors = authors == null ? List.of() : List.copyOf(authors);
            minecraftVersion = Objects.requireNonNullElse(minecraftVersion, "").trim();
            javaVersion = Objects.requireNonNullElse(javaVersion, "").trim();
            loaderVersion = Objects.requireNonNullElse(loaderVersion, "").trim();
            entrypointHint = Objects.requireNonNullElse(entrypointHint, "").trim();
        }

        public static ModMetadata empty() {
            return new ModMetadata("", "", "", "", List.of(), "", "", "", "");
        }
    }

    private static final Pattern TOML_STRING = Pattern.compile(
            "^(?:\"?([A-Za-z0-9_]+)\"?)\\s*=\\s*(?:\"\"\"([\\s\\S]*?)\"\"\"|'''([\\s\\S]*?)'''|\"([^\"]*)\"|'([^']*)')",
            Pattern.MULTILINE);
    private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern JSON_ARRAY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^]]*)]");
    private static final Pattern MOD_ANNOTATION = Pattern.compile("@Mod\\s*\\(\\s*(?:value\\s*=\\s*)?(?:([A-Za-z_][\\w.]*)\\.MOD_?ID|\"([^\"]+)\"|'([^']+)')");
    private static final Pattern CLASS_NAME = Pattern.compile("(?m)^\\s*(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z_][\\w]*)");
    private static final Pattern PACKAGE_NAME = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;");
    private static final int MAX_JAVA_SCAN = 200;

    private MinecraftMetadataReader() {}

    public static ModMetadata read(ProjectStructure structure, MinecraftLoader loader) throws IOException {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(loader);
        Path root = structure.rootPath();
        return switch (loader) {
            case NEOFORGE -> readNeoForge(root);
            case FORGE -> readToml(root.resolve("src/main/resources/META-INF/mods.toml"), false);
            case FABRIC -> readFabricJson(root.resolve("src/main/resources/fabric.mod.json"));
            case QUILT -> readQuiltJson(root.resolve("src/main/resources/quilt.mod.json"));
            case UNKNOWN -> fallback(structure);
        };
    }

    public static String detectMainClass(ProjectStructure structure, MinecraftLoader loader, ModMetadata meta) throws IOException {
        if (meta != null && !meta.entrypointHint().isEmpty()) return meta.entrypointHint();
        if (loader == MinecraftLoader.FABRIC || loader == MinecraftLoader.QUILT) {
            ModMetadata json = read(structure, loader);
            if (!json.entrypointHint().isEmpty()) return json.entrypointHint();
        }
        return findAnnotatedMod(structure.rootPath());
    }

    private static ModMetadata fallback(ProjectStructure structure) throws IOException {
        for (String path : structure.metadataFiles()) {
            Path file = structure.rootPath().resolve(path);
            String name = path.toLowerCase(Locale.ROOT);
            if (name.endsWith("neoforge.mods.toml")) return readToml(file, true);
            if (name.endsWith("mods.toml")) return readToml(file, false);
            if (name.endsWith("fabric.mod.json")) return readFabricJson(file);
            if (name.endsWith("quilt.mod.json")) return readQuiltJson(file);
        }
        return ModMetadata.empty();
    }

    private static ModMetadata readNeoForge(Path root)throws IOException {
        Path file=root.resolve("src/main/resources/META-INF/neoforge.mods.toml");
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))file=root.resolve("src/main/templates/META-INF/neoforge.mods.toml");
        var metadata=readToml(file,true);var properties=new java.util.Properties();
        Path props=root.resolve("gradle.properties");
        if(Files.isRegularFile(props,LinkOption.NOFOLLOW_LINKS))try{properties.load(new java.io.StringReader(MinecraftGradleAnalysis.read(props)));}catch(IllegalArgumentException malformed){/* retain unresolved values */}
        return new ModMetadata(resolve(metadata.modId(),properties),resolve(metadata.displayName(),properties),resolve(metadata.version(),properties),resolve(metadata.description(),properties),metadata.authors().stream().map(a->resolve(a,properties)).toList(),resolve(metadata.minecraftVersion(),properties),resolve(metadata.javaVersion(),properties),resolve(metadata.loaderVersion(),properties),metadata.entrypointHint());
    }
    static String resolve(String value,java.util.Properties properties){
        var match=Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}").matcher(value);var out=new StringBuffer();
        while(match.find())match.appendReplacement(out,Matcher.quoteReplacement(properties.getProperty(match.group(1),match.group())));
        match.appendTail(out);return out.toString();
    }
    static String tomlLine(String value){
        char quote=0;boolean escape=false;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(quote!=0){if(escape)escape=false;else if(c=='\\'&&quote=='"')escape=true;else if(c==quote)quote=0;}
            else if(c=='"'||c=='\'')quote=c;
            else if(c=='#')return value.substring(0,i);
        }
        return value;
    }

    private static ModMetadata readToml(Path file, boolean neo) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return ModMetadata.empty();
        String text = MinecraftGradleAnalysis.read(file);
        String table = "",dependencyModId="";
        String modId = "", displayName = "", version = "", description = "", minecraftVersion = "", loaderVersion = "";
        List<String> authors = new ArrayList<>();
        String[] rows=text.split("\\R");
        for (int row=0;row<rows.length;row++) {
            String raw=rows[row];
            String line = tomlLine(raw).strip();
            if (line.startsWith("[")) {
                table = line;dependencyModId="";
                continue;
            }
            Matcher m = Pattern.compile("^(?:modId|displayName|version|description|authors|versionRange)\\s*=\\s*(.*)$").matcher(line);
            if (!m.find()) continue;
            String key = line.substring(0, line.indexOf('=')).strip().replace("\"", "");
            String rawValue=m.group(1).strip();
            if(rawValue.startsWith("\"\"\"")||rawValue.startsWith("'''")){
                String marker=rawValue.substring(0,3);
                if(rawValue.indexOf(marker,3)<0){
                    var valueLines=new StringBuilder(rawValue);boolean closed=false;
                    while(++row<rows.length){valueLines.append('\n').append(rows[row]);if(rows[row].contains(marker)){closed=true;break;}}
                    if(!closed)continue;
                    rawValue=valueLines.toString();int end=rawValue.indexOf(marker,3);rawValue=rawValue.substring(0,end+3);
                }
            }
            String value = unquote(rawValue);
            if (table.equals("[[mods]]") || table.isEmpty()) {
                switch (key) {
                    case "modId" -> { if (modId.isEmpty()) modId = value; }
                    case "displayName" -> { if (displayName.isEmpty()) displayName = value; }
                    case "version" -> { if (version.isEmpty()) version = value; }
                    case "description" -> { if (description.isEmpty()) description = value; }
                    case "authors" -> { if (authors.isEmpty() && !value.isBlank()) authors = List.of(value.split("\\s*,\\s*")); }
                    default -> { }
                }
            }
            if(key.equals("modId")&&table.contains("dependencies"))dependencyModId=value;
            if (key.equals("versionRange") && table.contains("dependencies")) {
                if (dependencyModId.equals("minecraft"))
                    minecraftVersion = value;
                if (neo && dependencyModId.equals("neoforge")) loaderVersion = value;
                if (!neo && dependencyModId.equals("forge")) loaderVersion = value;
            }
        }
        // Simpler pass for dependency version ranges near modId="minecraft"
        Matcher dep = Pattern.compile("modId\\s*=\\s*\"(minecraft|neoforge|forge)\"[\\s\\S]*?versionRange\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(text);
        while (dep.find()) {
            if (dep.group(1).equalsIgnoreCase("minecraft") && minecraftVersion.isEmpty()) minecraftVersion = dep.group(2);
            if ((dep.group(1).equalsIgnoreCase("neoforge") || dep.group(1).equalsIgnoreCase("forge")) && loaderVersion.isEmpty())
                loaderVersion = dep.group(2);
        }
        return new ModMetadata(modId, displayName, version, description, authors, minecraftVersion, "", loaderVersion, "");
    }

    private static boolean previousModIdWas(String text, String table, String id) {
        return table.toLowerCase(Locale.ROOT).contains("dependencies") && text.toLowerCase(Locale.ROOT).contains("modid=\"" + id + "\"");
    }

    private static ModMetadata readFabricJson(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return ModMetadata.empty();
        String text = MinecraftGradleAnalysis.read(file);
        String id = jsonString(text, "id");
        String name = jsonString(text, "name");
        String version = jsonString(text, "version");
        String description = jsonString(text, "description");
        List<String> authors = jsonStringArray(text, "authors");
        String entry = firstEntrypoint(text);
        String minecraft = dependencyVersion(text, "minecraft");
        String loader = dependencyVersion(text, "fabricloader");
        String java = dependencyVersion(text, "java");
        return new ModMetadata(id, name, version, description, authors, minecraft, java, loader, entry);
    }

    private static ModMetadata readQuiltJson(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return ModMetadata.empty();
        String text = MinecraftGradleAnalysis.read(file);
        // Quilt often nests under quilt_loader
        String id = firstNonBlank(jsonNested(text, "id"), jsonString(text, "id"));
        String name = firstNonBlank(jsonNested(text, "name"), jsonString(text, "name"), id);
        String version = firstNonBlank(jsonNested(text, "version"), jsonString(text, "version"));
        String description = jsonString(text, "description");
        List<String> authors = jsonStringArray(text, "authors");
        String entry = firstEntrypoint(text);
        String minecraft = dependencyVersion(text, "minecraft");
        String loader = dependencyVersion(text, "quilt_loader");
        return new ModMetadata(id, name, version, description, authors, minecraft, "", loader, entry);
    }

    private static String findAnnotatedMod(Path root) throws IOException {
        List<String> hits = new ArrayList<>();
        Path src = root.resolve("src");
        if (!Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) return "";
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            private int count;
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (count >= MAX_JAVA_SCAN) return FileVisitResult.TERMINATE;
                if (!file.getFileName().toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                if (Files.isSymbolicLink(file) || attrs.size() > MinecraftGradleAnalysis.MAX_FILE_BYTES) return FileVisitResult.CONTINUE;
                count++;
                String text = MinecraftGradleAnalysis.read(file);
                Matcher mod = MOD_ANNOTATION.matcher(text);
                if (!mod.find()) return FileVisitResult.CONTINUE;
                Matcher pkg = PACKAGE_NAME.matcher(text);
                Matcher cls = CLASS_NAME.matcher(text);
                if (cls.find()) {
                    String simple = cls.group(1);
                    String fqn = pkg.find() ? pkg.group(1) + "." + simple : simple;
                    int annotationEnd=text.indexOf(')',mod.start());
                    String annotation=annotationEnd<0?"":text.substring(mod.start(),annotationEnd+1);
                    boolean clientOnly=Pattern.compile("\\bdist\\s*=\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.)*CLIENT\\b").matcher(annotation).find();
                    hits.add((clientOnly?"1:":"0:")+fqn);
                }
                return hits.size() >= 8 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return Files.isSymbolicLink(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
        });
        return hits.stream().sorted().map(hit->hit.substring(2)).findFirst().orElse("");
    }

    private static String jsonString(String text, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static String jsonNested(String text, String key) {
        Matcher m = Pattern.compile("\"quilt_loader\"\\s*:\\s*\\{([\\s\\S]*?)}").matcher(text);
        return m.find() ? jsonString(m.group(1), key) : "";
    }

    private static List<String> jsonStringArray(String text, String key) {
        Matcher m = JSON_ARRAY.matcher(text);
        while (m.find()) {
            if (!m.group(1).equals(key)) continue;
            List<String> values = new ArrayList<>();
            Matcher item = Pattern.compile("\"([^\"]+)\"").matcher(m.group(2));
            while (item.find() && values.size() < 16) values.add(item.group(1));
            return List.copyOf(values);
        }
        String single = jsonString(text, key);
        return single.isEmpty() ? List.of() : List.of(single);
    }

    private static String firstEntrypoint(String text) {
        Matcher block = Pattern.compile("\"entrypoints\"\\s*:\\s*\\{([\\s\\S]*?)}").matcher(text);
        if (!block.find()) return "";
        Matcher item = Pattern.compile("\"(?:main|client|server)\"\\s*:\\s*(?:\"([^\"]+)\"|\\[\\s*\"([^\"]+)\")").matcher(block.group(1));
        return item.find() ? firstNonBlank(item.group(1), item.group(2)) : "";
    }

    private static String dependencyVersion(String text, String id) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(id) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
        return m.find() ? m.group(1) : "";
    }

    static String unquote(String value) {
        value = value.strip();
        if (value.length()>=6&&((value.startsWith("\"\"\"") && value.endsWith("\"\"\"")) || (value.startsWith("'''") && value.endsWith("'''"))))
            return value.substring(3, value.length() - 3).strip();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))))
            return value.substring(1, value.length() - 1);
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }
}
