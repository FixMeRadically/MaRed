package com.fixmer.mared.gui2.theme.module;


import com.fixmer.mared.gui2.modules.ModuleId;



public final class ModuleThemeResolver {



    private ModuleThemeResolver(){

    }





    public static ModuleVisualTheme resolve(

            ModuleId module

    ){


        ModuleVisualTheme theme =
                ModuleVisualThemeRegistry.get(module);



        if(theme == null){


            return new ModuleVisualTheme(

                    0xFFFFFFFF,

                    0xFFAAAAAA,

                    0xFFFFFFFF,

                    0xFFFFFFFF,

                    0xFF000000

            );


        }



        return theme;

    }


}