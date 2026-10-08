package com.fixmer.mared.gui2.welcome.scene;


import com.fixmer.mared.gui2.modules.ModuleId;



public final class WelcomeSceneBootstrap {



    private WelcomeSceneBootstrap(){

    }





    public static void bootstrap(){



        WelcomeSceneRegistry.register(

                new WelcomeJsonSceneProvider(

                        ModuleId.LOGIC,

                        "/assets/mared/welcome/logic.json"

                )

        );





        WelcomeSceneRegistry.register(

                new WelcomeJsonSceneProvider(

                        ModuleId.CONTENT,

                        "/assets/mared/welcome/content.json"

                )

        );





        WelcomeSceneRegistry.register(

                new WelcomeJsonSceneProvider(

                        ModuleId.WORLD,

                        "/assets/mared/welcome/world.json"

                )

        );





        WelcomeSceneRegistry.register(

                new WelcomeJsonSceneProvider(

                        ModuleId.RESOURCES,

                        "/assets/mared/welcome/resources.json"

                )

        );





        WelcomeSceneRegistry.register(

                new WelcomeJsonSceneProvider(

                        ModuleId.TOOLS,

                        "/assets/mared/welcome/tools.json"

                )

        );





        WelcomeSceneRegistry.register(

                new WelcomeJsonSceneProvider(

                        ModuleId.SCENARIOS,

                        "/assets/mared/welcome/scenarios.json"

                )

        );


    }


}