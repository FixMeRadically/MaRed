package com.fixmer.mared.gui2.welcome.scene;


import com.fixmer.mared.gui2.modules.ModuleId;
import com.fixmer.mared.gui2.visual.scene.VisualScene;


import java.util.EnumMap;
import java.util.Map;



public final class WelcomeSceneRegistry {



    private static final Map<ModuleId, WelcomeSceneProvider> PROVIDERS =
            new EnumMap<>(ModuleId.class);





    private WelcomeSceneRegistry(){

    }





    public static void register(

            WelcomeSceneProvider provider

    ){

        PROVIDERS.put(

                provider.module(),

                provider

        );

    }





    public static VisualScene create(

            ModuleId module

    ){


        WelcomeSceneProvider provider =
                PROVIDERS.get(module);



        if(provider == null){

            return new VisualScene();

        }



        return provider.create();


    }





    public static boolean contains(

            ModuleId module

    ){

        return PROVIDERS.containsKey(module);

    }


}