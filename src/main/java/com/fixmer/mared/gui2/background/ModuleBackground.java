package com.fixmer.mared.gui2.background;


public final class ModuleBackground {



    private final MaredParallaxScene scene;


    private final MaredBackgroundAnimator animator;




    public ModuleBackground(
            MaredParallaxScene scene
    ){

        this.scene = scene;

        this.animator =
                new MaredBackgroundAnimator();

    }





    public void setStage(
            BackgroundStage stage
    ){


        animator.setTarget(
                switch(stage){

                    case CODE -> 0f;

                    case SYMBOLS -> 0.33f;

                    case OBJECT -> 0.66f;

                    case FULL -> 1f;

                }
        );


    }





    public void tick(){

        animator.tick();

    }





    public MaredParallaxScene scene(){

        return scene;

    }





    public float progress(){

        return animator.progress();

    }



}