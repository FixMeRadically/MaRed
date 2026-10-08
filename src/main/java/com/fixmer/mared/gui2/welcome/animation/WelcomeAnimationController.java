package com.fixmer.mared.gui2.welcome.animation;



public final class WelcomeAnimationController {



    private final WelcomeAnimationState state;



    private final WelcomeRevealAnimation reveal;





    public WelcomeAnimationController(){


        state =
                new WelcomeAnimationState();



        reveal =
                new WelcomeRevealAnimation();


    }





    public void play(){


        state.start();


    }





    public void tick(){


        state.tick(
                0.006f
        );


    }





    public float value(
            String id
    ){


        return reveal.timeline()
                .get(

                        id,

                        state.progress()

                );


    }





    public float progress(){

        return state.progress();

    }





    public boolean finished(){

        return state.finished();

    }


}