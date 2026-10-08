package com.fixmer.mared.gui2.visual.camera;


public enum ParallaxDepth {


    BACKGROUND(0.05f),

    PARTICLES(0.25f),

    SYMBOLS(0.45f),

    OBJECTS(0.80f);


    private final float strength;


    ParallaxDepth(
            float strength
    ){

        this.strength = strength;

    }


    public float strength(){

        return strength;

    }

}