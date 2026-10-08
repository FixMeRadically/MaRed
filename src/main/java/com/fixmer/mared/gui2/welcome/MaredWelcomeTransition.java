package com.fixmer.mared.gui2.welcome;



public final class MaredWelcomeTransition {



    private float value;



    private float target;



    private float speed =
            0.025f;





    public void moveTo(
            float target
    ){

        this.target = target;

    }





    public void tick(){


        value +=
                (target - value)
                *
                speed;


    }





    public float value(){

        return value;

    }


}