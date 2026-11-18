package com.fixmer.genesis.technology.creation;

import java.util.*;
import static com.fixmer.genesis.technology.creation.CreationCatalog145.Direction;

/** Minimal editable Java Edition 1.21.1 project layouts; no executable downloads or auto-deployment. */
public final class CreationTemplates145 {
    private CreationTemplates145() {}
    public static Map<String,String> files(Direction direction){
        var files=new LinkedHashMap<String,String>();
        files.put("README.md","# MaRed: "+direction.id()+"\n\nProject files are ordinary files. Save before copying or exporting the project.\n");
        switch(direction){
            case DATAPACKS, MAPS -> {
                files.put("pack.mcmeta","{\n  \"pack\": {\"pack_format\": 48, \"description\": \"MaRed data pack\"}\n}\n");
                files.put("data/mared/function/main.mcfunction","# Java 1.21.1 data pack function\nsay Hello from MaRed\n");
                files.put("data/minecraft/tags/function/load.json","{\"values\": [\"mared:main\"]}\n");
                files.put("data/mared/recipe/example.json","{\n  \"type\": \"minecraft:crafting_shapeless\",\n  \"ingredients\": [{\"item\": \"minecraft:dirt\"}],\n  \"result\": {\"id\": \"minecraft:clay_ball\", \"count\": 1}\n}\n");
            }
            case RESOURCEPACKS -> {
                files.put("pack.mcmeta","{\n  \"pack\": {\"pack_format\": 34, \"description\": \"MaRed resource pack\"}\n}\n");
                files.put("assets/minecraft/lang/en_us.json","{}\n");
                files.put("assets/minecraft/models/item/stick.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/stick\"}}\n");
            }
            case SHADERS -> {
                files.put("shaders/shaders.properties","# Shader loader settings. Select a compatible shader loader to test this pack.\n");
                files.put("shaders/lib/color.glsl","// Utility source only; connect it to your shader loader's render pipeline.\nvec3 mared_color(vec3 color) {\n    return color;\n}\n");
            }
            case MODPACKS -> {
                files.put("modpack-plan.json","{\n  \"minecraft\": \"1.21.1\",\n  \"loader\": \"neoforge\",\n  \"mods\": [],\n  \"note\": \"Local project plan; not a Modrinth or CurseForge export manifest\"\n}\n");
                files.put("config/README.md","Place mod configuration files here.\n");
                for(var entry:files(Direction.DATAPACKS).entrySet())if(!entry.getKey().equals("README.md"))files.put("datapacks/mared/"+entry.getKey(),entry.getValue());
            }
            case SERVERS -> {
                files.put("config/server-notes.md","# Server project\n\nOpen the Server tool to manage an actual server. This project stores configuration drafts and scripts.\n");
                files.put("server.properties","# Configuration draft; apply to your server through its management tools.\nmotd=MaRed server\nserver-port=25565\nenable-command-block=true\n");
            }
            case MODS -> files.put("src/README.md","Create or import a NeoForge project from the project menu.\n");
        }
        return Collections.unmodifiableMap(files);
    }
}
