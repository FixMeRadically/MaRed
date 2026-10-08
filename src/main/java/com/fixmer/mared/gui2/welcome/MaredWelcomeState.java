package com.fixmer.mared.gui2.welcome;


import com.fixmer.mared.gui2.modules.ModuleId;



public final class MaredWelcomeState {



    private ModuleId selected;



    private boolean firstOpen;



    private float animationProgress;



    public MaredWelcomeState(){


        firstOpen = true;

    }





    public ModuleId selected(){

        return selected;

    }




    public void select(
            ModuleId module
    ){

        this.selected = module;

    }





    public boolean firstOpen(){

        return firstOpen;

    }





    public void finishFirstOpen(){

        firstOpen = false;

    }





    public float animationProgress(){

        return animationProgress;

    }





    public void setAnimationProgress(
            float value
    ){

        animationProgress = value;

    }


}