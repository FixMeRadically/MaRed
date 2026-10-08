package com.fixmer.mared.gui2.background;



public final class MaredBackgroundAnimator {



    private float progress;



    private float target;



    private float speed =
            0.03f;




    public void setTarget(
            float value
    ){

        target = value;

    }





    public void tick(){


        progress +=
                (target - progress)
                        *
                speed;


    }





    public float progress(){

        return progress;

    }



}