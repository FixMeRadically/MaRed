package com.fixmer.mared.gui2.welcome.animation;


public final class WelcomeRevealAnimation {



    private final WelcomeTimeline timeline;





    public WelcomeRevealAnimation(){


        timeline =
                new WelcomeTimeline();



        createDefault();


    }





    private void createDefault(){


        timeline.add(

                new WelcomeAnimationStep(

                        "code",

                        0f,

                        0.30f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "symbols",

                        0.25f,

                        0.60f,

                        WelcomeTransitionCurve.EASE_IN_OUT

                )

        );



        timeline.add(

                new WelcomeAnimationStep(

                        "object",

                        0.55f,

                        1f,

                        WelcomeTransitionCurve.EASE_OUT

                )

        );


    }





    public WelcomeTimeline timeline(){

        return timeline;

    }


}