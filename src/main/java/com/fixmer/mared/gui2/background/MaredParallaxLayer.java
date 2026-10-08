package com.fixmer.mared.gui2.background;


public final class MaredParallaxLayer {


    private final String id;


    private final float depth;


    private float offsetX;


    private float offsetY;


    private float opacity;



    public MaredParallaxLayer(
            String id,
            float depth
    ){

        this.id = id;
        this.depth = depth;

        this.opacity = 1.0f;

    }




    public void update(
            float mouseX,
            float mouseY
    ){


        offsetX =
                mouseX * depth;


        offsetY =
                mouseY * depth;


    }





    public String id(){

        return id;

    }



    public float depth(){

        return depth;

    }



    public float offsetX(){

        return offsetX;

    }



    public float offsetY(){

        return offsetY;

    }



    public float opacity(){

        return opacity;

    }



    public void setOpacity(
            float opacity
    ){

        this.opacity = opacity;

    }


}