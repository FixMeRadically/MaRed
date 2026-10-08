package com.fixmer.mared.gui2.modules.style;


import com.fixmer.mared.gui2.modules.ModuleId;



public final class ModuleIcon {


    private ModuleIcon(){}



    public static String get(
            ModuleId id
    ){

        ModuleStyle style =
                ModuleStyleRegistry.get(id);


        if(style == null)
            return "?";


        return style.icon();

    }


}