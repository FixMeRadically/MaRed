package com.fixmer.mared.gui2.theme.module;


import com.fixmer.mared.gui2.modules.ModuleId;


import java.util.EnumMap;
import java.util.Map;



public final class ModuleVisualThemeRegistry {



    private static final Map<ModuleId, ModuleVisualTheme> THEMES =
            new EnumMap<>(ModuleId.class);





    static {


        /*
         * CONTENT
         * Создание / магия / контент
         */

        THEMES.put(

                ModuleId.CONTENT,

                new ModuleVisualTheme(

                        0xFF9B59FF,

                        0xFF6C38CC,

                        0xFFD6B8FF,

                        0xFFB47CFF,

                        0xFF120A20

                )

        );





        /*
         * WORLD
         * Природа / мир
         */

        THEMES.put(

                ModuleId.WORLD,

                new ModuleVisualTheme(

                        0xFF55D66B,

                        0xFF2EA84A,

                        0xFFA6FFB2,

                        0xFF77FF88,

                        0xFF081A0C

                )

        );





        /*
         * LOGIC
         * Код / технологии
         */

        THEMES.put(

                ModuleId.LOGIC,

                new ModuleVisualTheme(

                        0xFF4DA6FF,

                        0xFF2878CC,

                        0xFFB8DDFF,

                        0xFF55CCFF,

                        0xFF081421

                )

        );





        /*
         * RESOURCES
         * Текстуры / ассеты
         */

        THEMES.put(

                ModuleId.RESOURCES,

                new ModuleVisualTheme(

                        0xFFFFB336,

                        0xFFCC8810,

                        0xFFFFE4A0,

                        0xFFFFCC55,

                        0xFF211505

                )

        );





        /*
         * TOOLS
         * Инструменты
         */

        THEMES.put(

                ModuleId.TOOLS,

                new ModuleVisualTheme(

                        0xFFFF6B6B,

                        0xFFCC3333,

                        0xFFFFB0B0,

                        0xFFFF7777,

                        0xFF210909

                )

        );





        /*
         * SCENARIOS
         * Истории / кампании
         */

        THEMES.put(

                ModuleId.SCENARIOS,

                new ModuleVisualTheme(

                        0xFFFFD166,

                        0xFFCC9922,

                        0xFFFFF0B0,

                        0xFFFFDD77,

                        0xFF211A05

                )

        );


    }





    private ModuleVisualThemeRegistry(){

    }





    public static ModuleVisualTheme get(

            ModuleId id

    ){

        return THEMES.get(id);

    }





    public static boolean contains(

            ModuleId id

    ){

        return THEMES.containsKey(id);

    }


}