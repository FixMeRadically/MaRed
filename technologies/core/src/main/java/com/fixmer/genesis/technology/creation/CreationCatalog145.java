package com.fixmer.genesis.technology.creation;

import java.util.*;

/** Product directions and reusable tools. Model/animation editors belong to a project's direction. */
public final class CreationCatalog145 {
    private CreationCatalog145() {}
    public enum Direction {
        MODS(0xFFAA77FF), DATAPACKS(0xFF55DDAA), MAPS(0xFF55AAFF), SERVERS(0xFFFFCC55),
        SHADERS(0xFFFF77BB), MODPACKS(0xFFFF9955), RESOURCEPACKS(0xFF77DDDD);
        public final int color;
        Direction(int color){this.color=color;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public String key(){return "mared.creation145.direction."+id();}
        public static Direction fromId(String id){try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException|NullPointerException e){return MODS;}}
    }
    public enum Tool { FILES, SCRIPTS, COMMANDS, SERVER, SUPERVISOR, SERVER_TOOLS, EXTENSIONS }
    public record Section(String id,Tool tool,List<String> paths) {
        public Section{paths=List.copyOf(paths);}
        public String key(){return "mared.creation145.section."+id;}
        public boolean accepts(String file){
            if(paths.isEmpty())return true;
            String path="/"+file.toLowerCase(Locale.ROOT);
            for(String part:paths)if(part.startsWith(".")?path.endsWith(part):(path.contains("/"+part+"/")||path.endsWith("/"+part)))return true;
            return false;
        }
    }
    private static Section files(String id,String... paths){return new Section(id,Tool.FILES,List.of(paths));}
    private static Section tool(String id,Tool tool){return new Section(id,tool,List.of());}
    private static final Section PROJECTS=files("projects"), SCRIPTS=new Section("scripts",Tool.SCRIPTS,List.of(".java",".kt",".kts",".py",".rs",".cpp",".c",".h",".js",".ts",".mr")),
        MODELS=files("models","models",".bbmodel"), ANIMATIONS=files("animations","animations",".animation.json",".bbmodel"),
        CONFIGS=files("configs","config","configs",".toml",".properties",".cfg",".yaml",".yml");
    public static List<Section> sections(Direction direction){var result=new ArrayList<Section>(switch(direction){
        case MODS -> List.of(PROJECTS,SCRIPTS,files("blocks","block","blocks","blockstates"),files("entities","entity","entities"),MODELS,ANIMATIONS,CONFIGS,files("resources","resources","assets"),files("build",".gradle",".kts",".properties"));
        case DATAPACKS -> List.of(PROJECTS,files("functions","function","functions",".mcfunction"),files("recipes","recipe","recipes"),files("loot","loot_table","loot_tables"),files("tags","tags"),files("advancements","advancement","advancements"),SCRIPTS);
        case MAPS -> List.of(tool("commands",Tool.COMMANDS),PROJECTS,SCRIPTS,files("functions","function","functions",".mcfunction"),files("scenarios","scenarios",".mr"),files("data","data",".json"));
        case SERVERS -> List.of(tool("server",Tool.SERVER),tool("server_configs156",Tool.SERVER_TOOLS),tool("server_settings156",Tool.SERVER_TOOLS));
        case SHADERS -> List.of(PROJECTS,files("shader_code",".glsl",".vsh",".fsh",".gsh",".csh",".vert",".frag"),files("programs","program"),CONFIGS,SCRIPTS);
        case MODPACKS -> List.of(PROJECTS,files("mods","mods",".mrpack",".jar"),CONFIGS,files("recipes","recipe","recipes","kubejs","scripts"),files("resources","resourcepacks","datapacks"),SCRIPTS);
        case RESOURCEPACKS -> List.of(PROJECTS,files("textures","textures",".png"),MODELS,ANIMATIONS,files("sounds","sounds",".ogg"),files("fonts","font"),files("translations","lang"),SCRIPTS);
    });if(direction!=Direction.SERVERS)result.add(tool("tools147",Tool.EXTENSIONS));return List.copyOf(result);}
    public static Section section(Direction direction,String id){return sections(direction).stream().filter(s->s.id.equals(id)).findFirst().orElse(sections(direction).get(0));}
}
