package com.fixmer.mared.gui2.welcome.render.animation;



public final class LayerAnimationState {



    private float opacity;


    private float scale;


    private float translateX;


    private float translateY;


    private float rotation;





    public void update(
            float progress
    ){


        opacity =
                progress;



        scale =
                0.5f
                +
                (0.5f * progress);



        translateY =
                40f
                -
                (40f * progress);



        translateX =
                0;



        rotation =
                180f
                -
                (180f * progress);


    }





    public float opacity(){

        return opacity;

    }





    public float scale(){

        return scale;

    }





    public float translateX(){

        return translateX;

    }





    public float translateY(){

        return translateY;

    }





    public float rotation(){

        return rotation;

    }


}