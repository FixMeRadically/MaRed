package com.fixmer.mared.gui2.visual.camera;


import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;



public final class ParallaxObject {


    private final float baseX;


    private final float baseY;


    private final ParallaxLayer layer;



    private float x;


    private float y;




    public ParallaxObject(

            float x,

            float y,

            ParallaxLayer layer

    ){


        this.baseX = x;

        this.baseY = y;

        this.x = x;

        this.y = y;

        this.layer = layer;


    }





    public void update(

            CameraTransform camera

    ){



        x =

                baseX

                +

                camera.x()

                *

                layer.strength();




        y =

                baseY

                +

                camera.y()

                *

                layer.strength();



    }





    public float x(){

        return x;

    }





    public float y(){

        return y;

    }





    public ParallaxLayer layer(){

        return layer;

    }


}