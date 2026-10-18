package com.fixmer.genesis.technology.minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects declared mod / Maven dependencies from metadata and Gradle text. */
public final class MinecraftDependencyReader {
    private static final Pattern FABRIC_DEP = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");

    private MinecraftDependencyReader() {}

    public static List<String> read(ProjectStructure structure, MinecraftLoader loader, MinecraftMetadataReader.ModMetadata meta) throws IOException {
        Objects.requireNonNull(structure);
        Objects.requireNonNull(loader);
        Set<String> deps = new LinkedHashSet<>();
        Path root = structure.rootPath();
        switch (loader) {
            case NEOFORGE -> {
                Path file=root.resolve("src/main/resources/META-INF/neoforge.mods.toml");
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))file=root.resolve("src/main/templates/META-INF/neoforge.mods.toml");
                readTomlDeps(file,deps);
            }
            case FORGE -> readTomlDeps(root.resolve("src/main/resources/META-INF/mods.toml"), deps);
            case FABRIC -> readJsonDeps(root.resolve("src/main/resources/fabric.mod.json"), deps);
            case QUILT -> readJsonDeps(root.resolve("src/main/resources/quilt.mod.json"), deps);
            case UNKNOWN -> {
                for (String path : structure.metadataFiles()) {
                    Path file = root.resolve(path);
                    if (path.endsWith(".toml")) readTomlDeps(file, deps);
                    else if (path.endsWith(".json")) readJsonDeps(file, deps);
                }
            }
        }
        if (structure.hasGradle()) {
            MinecraftGradleAnalysis.GradleSnapshot gradle = MinecraftGradleAnalysis.analyze(root);
            deps.addAll(gradle.dependencies());
            var resolved=new LinkedHashSet<String>();for(String dependency:deps)resolved.add(MinecraftMetadataReader.resolve(dependency,gradle.properties()));
            deps.clear();deps.addAll(resolved);
        }
        if (meta != null && !meta.modId().isEmpty()) deps.removeIf(d -> d.toLowerCase(Locale.ROOT).contains("modid=" + meta.modId().toLowerCase(Locale.ROOT)));
        List<String> result = new ArrayList<>(deps);
        if (result.size() > 128) return List.copyOf(result.subList(0, 128));
        return List.copyOf(result);
    }

    private static void readTomlDeps(Path file, Set<String> deps) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        String text=MinecraftGradleAnalysis.read(file),id="",range="",type="required",multiline="";boolean dependency=false;
        for(String raw:text.split("\\R")){
            if(!multiline.isEmpty()){if(raw.contains(multiline))multiline="";continue;}
            String line=MinecraftMetadataReader.tomlLine(raw).strip();
            int triple=line.indexOf("\"\"\"");String marker="\"\"\"";
            if(triple<0){triple=line.indexOf("'''");marker="'''";}
            if(triple>=0&&line.indexOf(marker,triple+3)<0){multiline=marker;continue;}
            if(line.startsWith("[")){
                if(dependency)addTomlDependency(deps,id,range,type);
                dependency=line.startsWith("[[dependencies.")&&line.endsWith("]]");id="";range="";type="required";continue;
            }
            if(!dependency)continue;
            int equals=line.indexOf('=');if(equals<0)continue;
            String key=line.substring(0,equals).strip(),value=MinecraftMetadataReader.unquote(line.substring(equals+1));
            switch(key){
                case "modId" -> id=value;
                case "versionRange" -> range=value;
                case "type" -> type=value;
                case "mandatory" -> type=value.equals("false")?"optional":"required";
                default -> { }
            }
        }
        if(dependency)addTomlDependency(deps,id,range,type);
    }
    private static void addTomlDependency(Set<String> deps,String id,String range,String type){
        if(deps.size()<128&&!id.isEmpty())deps.add("mod:"+id+":"+range+" ("+type+")");
    }

    private static void readJsonDeps(Path file, Set<String> deps) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        String text = MinecraftGradleAnalysis.read(file);
        Matcher block = Pattern.compile("\"(?:depends|depends_overrides|breaking)\"\\s*:\\s*\\{([^}]*)}").matcher(text);
        while (block.find()) {
            Matcher item = FABRIC_DEP.matcher(block.group(1));
            while (item.find() && deps.size() < 128) deps.add("mod:" + item.group(1) + ":" + item.group(2));
        }
    }
}
