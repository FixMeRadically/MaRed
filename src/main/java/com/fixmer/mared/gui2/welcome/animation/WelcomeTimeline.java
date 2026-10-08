package com.fixmer.mared.gui2.welcome.animation;


import java.util.ArrayList;
import java.util.List;



public final class WelcomeTimeline {



    private final List<WelcomeAnimationStep> steps =
            new ArrayList<>();





    public void add(

            WelcomeAnimationStep step

    ){

        steps.add(step);

    }





    public float get(

            String id,

            float progress

    ){


        for(
                WelcomeAnimationStep step :
                steps
        ){


            if(step.id().equals(id)){


                return step.value(progress);


            }


        }



        return 0f;

    }





    public List<WelcomeAnimationStep> steps(){

        return steps;

    }


}