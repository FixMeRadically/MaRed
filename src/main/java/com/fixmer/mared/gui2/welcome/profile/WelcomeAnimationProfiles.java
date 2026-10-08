package com.fixmer.mared.gui2.welcome.profile;


import com.fixmer.mared.gui2.modules.ModuleId;
import com.fixmer.mared.gui2.welcome.animation.WelcomeAnimationStep;
import com.fixmer.mared.gui2.welcome.animation.WelcomeTimeline;
import com.fixmer.mared.gui2.welcome.animation.WelcomeTransitionCurve;


import java.util.EnumMap;
import java.util.Map;



public final class WelcomeAnimationProfiles {



    private static final Map<ModuleId, WelcomeAnimationProfile> PROFILES =
            new EnumMap<>(ModuleId.class);





    static {


        PROFILES.put(

                ModuleId.LOGIC,

                createLogic()

        );



        PROFILES.put(

                ModuleId.CONTENT,

                createContent()

        );



        PROFILES.put(

                ModuleId.WORLD,

                createWorld()

        );


    }





    private WelcomeAnimationProfiles(){

    }





    private static WelcomeAnimationProfile createLogic(){



        WelcomeTimeline timeline =
                new WelcomeTimeline();




        timeline.add(

                new WelcomeAnimationStep(

                        "code",

                        0f,

                        0.25f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "symbols",

                        0.20f,

                        0.55f,

                        WelcomeTransitionCurve.EASE_IN_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "nodes",

                        0.45f,

                        0.85f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "blueprint",

                        0.75f,

                        1f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        return new WelcomeAnimationProfile(

                "logic",

                timeline

        );


    }





    private static WelcomeAnimationProfile createContent(){



        WelcomeTimeline timeline =
                new WelcomeTimeline();




        timeline.add(

                new WelcomeAnimationStep(

                        "json",

                        0f,

                        0.25f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "pixels",

                        0.2f,

                        0.5f,

                        WelcomeTransitionCurve.EASE_IN_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "texture",

                        0.45f,

                        0.75f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "model",

                        0.7f,

                        1f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        return new WelcomeAnimationProfile(

                "content",

                timeline

        );


    }





    private static WelcomeAnimationProfile createWorld(){



        WelcomeTimeline timeline =
                new WelcomeTimeline();




        timeline.add(

                new WelcomeAnimationStep(

                        "noise",

                        0f,

                        0.25f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "particles",

                        0.2f,

                        0.5f,

                        WelcomeTransitionCurve.EASE_IN_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "leaves",

                        0.45f,

                        0.7f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "terrain",

                        0.65f,

                        1f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        return new WelcomeAnimationProfile(

                "world",

                timeline

        );


    }





    public static WelcomeAnimationProfile get(

            ModuleId module

    ){


        return PROFILES.get(module);


    }


}