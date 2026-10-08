package com.fixmer.mared.gui2.visual.camera;


import com.fixmer.mared.gui2.visual.scene.VisualScene;



public final class ParallaxLayer {


    public static final ParallaxLayer BACKGROUND =
            new ParallaxLayer(
                    "BACKGROUND",
                    0.05f
            );


    public static final ParallaxLayer PARTICLES =
            new ParallaxLayer(
                    "PARTICLES",
                    0.25f
            );


    public static final ParallaxLayer SYMBOLS =
            new ParallaxLayer(
                    "SYMBOLS",
                    0.45f
            );


    public static final ParallaxLayer OBJECTS =
            new ParallaxLayer(
                    "OBJECTS",
                    0.80f
            );



    private final String name;


    private final float strength;


    private VisualScene scene;



    public ParallaxLayer(
            String name,
            float strength
    ){

        this.name = name;

        this.strength = strength;

    }



    public String name(){

        return name;

    }



    public float strength(){

        return strength;

    }



    public float depth(){

        return strength;

    }



    public VisualScene scene(){

        return scene;

    }



    public void setScene(
            VisualScene scene
    ){

        this.scene = scene;

    }



    @Override
    public String toString(){

        return name;

    }


}