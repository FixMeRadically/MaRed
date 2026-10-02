package com.fixmer.mared.gui2.theme;


import java.util.EnumMap;
import java.util.Map;



/**
 * Реестр цветов модулей MaRed Studio.
 */
public final class ModuleColorRegistry {



    private static final Map<ModuleType, ModuleTheme> MODULES =
            new EnumMap<>(ModuleType.class);





    private ModuleColorRegistry(){

    }







    public static void init(){


        if(!MODULES.isEmpty()){

            return;

        }






        register(
                ModuleType.CONTENT,

                new ModuleTheme(
                        "content",
                        "Контент",
                        0xFFFF8800
                )
        );






        register(
                ModuleType.WORLD,

                new ModuleTheme(
                        "world",
                        "Мир",
                        0xFF45C46B
                )
        );






        register(
                ModuleType.LOGIC,

                new ModuleTheme(
                        "logic",
                        "Логика",
                        0xFF3B82F6
                )
        );







        register(
                ModuleType.RESOURCES,

                new ModuleTheme(
                        "resources",
                        "Ресурсы",
                        0xFFA855F7
                )
        );







        register(
                ModuleType.TOOLS,

                new ModuleTheme(
                        "tools",
                        "Инструменты",
                        0xFFEAB308
                )
        );







        register(
                ModuleType.GAMEPLAY,

                new ModuleTheme(
                        "gameplay",
                        "Сценарии и геймплей",
                        0xFFEF4444
                )
        );


    }









    private static void register(
            ModuleType type,
            ModuleTheme theme
    ){

        MODULES.put(
                type,
                theme
        );

    }









    public static ModuleTheme get(
            ModuleType type
    ){

        init();


        return MODULES.get(type);

    }









    public static Map<ModuleType, ModuleTheme> all(){

        init();


        return MODULES;

    }



}