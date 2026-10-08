
package com.fixmer.mared.modules;


import java.util.ArrayList;
import java.util.List;



public final class ModuleRegistry {


    private static final List<ModuleDescriptor> MODULES =
            new ArrayList<>();


    private ModuleRegistry(){}



    public static void register(
            ModuleDescriptor module
    ){
        MODULES.add(module);
    }



    public static List<ModuleDescriptor> all(){

        return MODULES;

    }



    public static void bootstrap(){

    }


}

