package com.fixmer.mared.gui2.background;


import com.fixmer.mared.gui2.modules.ModuleId;

import java.util.EnumMap;
import java.util.Map;



public final class ModuleBackgroundRegistry {



    private static final Map<ModuleId, ModuleBackground>
            BACKGROUNDS =
            new EnumMap<>(ModuleId.class);




    static {


        register(
                ModuleId.LOGIC,
                createLogic()
        );


        register(
                ModuleId.WORLD,
                createWorld()
        );


        register(
                ModuleId.CONTENT,
                createContent()
        );


    }





    private ModuleBackgroundRegistry(){}





    private static void register(
            ModuleId id,
            ModuleBackground background
    ){

        BACKGROUNDS.put(
                id,
                background
        );

    }





    public static ModuleBackground get(
            ModuleId id
    ){

        return BACKGROUNDS.get(id);

    }






    private static ModuleBackground createLogic(){


        MaredParallaxScene scene =
                new MaredParallaxScene();



        scene.add(
                new MaredParallaxLayer(
                        "code",
                        0.01f
                )
        );



        scene.add(
                new MaredParallaxLayer(
                        "symbols",
                        0.03f
                )
        );



        scene.add(
                new MaredParallaxLayer(
                        "nodes",
                        0.06f
                )
        );



        return new ModuleBackground(scene);

    }





    private static ModuleBackground createWorld(){


        MaredParallaxScene scene =
                new MaredParallaxScene();



        scene.add(
                new MaredParallaxLayer(
                        "terrain_code",
                        0.01f
                )
        );


        scene.add(
                new MaredParallaxLayer(
                        "leaves",
                        0.03f
                )
        );


        scene.add(
                new MaredParallaxLayer(
                        "landscape",
                        0.07f
                )
        );


        return new ModuleBackground(scene);

    }





    private static ModuleBackground createContent(){


        MaredParallaxScene scene =
                new MaredParallaxScene();



        scene.add(
                new MaredParallaxLayer(
                        "texture",
                        0.02f
                )
        );



        scene.add(
                new MaredParallaxLayer(
                        "block",
                        0.05f
                )
        );



        scene.add(
                new MaredParallaxLayer(
                        "model",
                        0.08f
                )
        );



        return new ModuleBackground(scene);

    }


}