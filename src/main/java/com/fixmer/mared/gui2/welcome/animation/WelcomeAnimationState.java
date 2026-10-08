package com.fixmer.mared.gui2.welcome.animation;


public final class WelcomeAnimationState {


    private float progress;


    private boolean playing;


    private boolean finished;




    public void start(){

        progress = 0f;

        playing = true;

        finished = false;

    }





    public void stop(){

        playing = false;

    }





    public void reset(){

        progress = 0f;

        finished = false;

    }





    public void tick(
            float speed
    ){


        if(!playing)
            return;



        progress += speed;



        if(progress >= 1f){

            progress = 1f;

            finished = true;

            playing = false;

        }


    }





    public float progress(){

        return progress;

    }





    public boolean playing(){

        return playing;

    }





    public boolean finished(){

        return finished;

    }


}