package com.fixmer.mared.gui2.modules.style;


import com.fixmer.mared.gui2.modules.ModuleId;

import java.util.EnumMap;
import java.util.Map;



public final class ModuleStyleRegistry {



    private static final Map<ModuleId, ModuleStyle> STYLES =
            new EnumMap<>(ModuleId.class);



    private ModuleStyleRegistry(){}



    static {


        register(
                ModuleId.CONTENT,

                new ModuleStyle(
                        0xFF9B59FF,
                        0xFF6F3FCC,
                        0xFF181020,
                        "◆",
                        "Items, entities, models and gameplay content"
                )
        );



        register(
                ModuleId.WORLD,

                new ModuleStyle(
                        0xFF55CC77,
                        0xFF339955,
                        0xFF101A14,
                        "◆",
                        "World generation and environment"
                )
        );



        register(
                ModuleId.LOGIC,

                new ModuleStyle(
                        0xFF55AAFF,
                        0xFF3377CC,
                        0xFF10151F,
                        "</>",
                        "Scripts, events and behaviour"
                )
        );



        register(
                ModuleId.RESOURCES,

                new ModuleStyle(
                        0xFFFFAA55,
                        0xFFCC7733,
                        0xFF1F1710,
                        "▣",
                        "Textures, models and assets"
                )
        );



        register(
                ModuleId.TOOLS,

                new ModuleStyle(
                        0xFFFF6666,
                        0xFFCC3333,
                        0xFF201010,
                        "⚙",
                        "Debugging and utilities"
                )
        );



        register(
                ModuleId.SCENARIOS,

                new ModuleStyle(
                        0xFFFFD65A,
                        0xFFCCAA33,
                        0xFF201C10,
                        "★",
                        "Stories, campaigns and gameplay"
                )
        );


    }





    public static void register(
            ModuleId id,
            ModuleStyle style
    ){

        STYLES.put(
                id,
                style
        );

    }




    public static ModuleStyle get(
            ModuleId id
    ){

        return STYLES.get(id);

    }


}