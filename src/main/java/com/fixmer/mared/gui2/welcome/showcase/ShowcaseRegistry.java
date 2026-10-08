package com.fixmer.mared.gui2.welcome.showcase;


import java.util.HashMap;
import java.util.Map;



public final class ShowcaseRegistry {



    private static final Map<String, ModuleShowcaseProfile> PROFILES =
            new HashMap<>();





    private ShowcaseRegistry(){

    }





    public static void register(

            ModuleShowcaseProfile profile

    ){

        PROFILES.put(

                profile.moduleId(),

                profile

        );

    }





    public static ModuleShowcaseProfile get(

            String id

    ){

        return PROFILES.get(id);

    }





    public static boolean contains(

            String id

    ){

        return PROFILES.containsKey(id);

    }


}