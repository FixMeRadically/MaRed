package com.fixmer.mared.gui2.visual.scene.layer;


import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;



public enum LayerType {


    BACKGROUND(
            ParallaxLayer.BACKGROUND,
            0
    ),


    PARTICLES(
            ParallaxLayer.PARTICLES,
            100
    ),


    SYMBOLS(
            ParallaxLayer.SYMBOLS,
            200
    ),


    OBJECTS(
            ParallaxLayer.OBJECTS,
            300
    );



    private final ParallaxLayer parallax;


    private final int priority;



    LayerType(

            ParallaxLayer parallax,

            int priority

    ){

        this.parallax = parallax;

        this.priority = priority;

    }



    public ParallaxLayer parallax(){

        return parallax;

    }



    public int priority(){

        return priority;

    }


}