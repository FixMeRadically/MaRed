package com.fixmer.mared.gui2.welcome.animation;


public final class WelcomeAnimationStep {



    private final String id;



    private final float start;



    private final float end;



    private final WelcomeTransitionCurve curve;





    public WelcomeAnimationStep(

            String id,

            float start,

            float end,

            WelcomeTransitionCurve curve

    ){

        this.id = id;

        this.start = start;

        this.end = end;

        this.curve = curve;

    }





    public float value(
            float progress
    ){


        if(progress <= start)

            return 0f;



        if(progress >= end)

            return 1f;



        float local =

                (
                        progress - start
                )
                /
                (
                        end - start
                );



        return curve.apply(local);


    }





    public String id(){

        return id;

    }


}