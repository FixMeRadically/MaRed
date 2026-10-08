package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.visual.camera.CameraTransform;
import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;

import net.minecraft.client.gui.GuiGraphics;



public abstract class VisualObject {


    protected float x;


    protected float y;



    private CameraTransform cameraTransform;



    private ParallaxLayer layer =
            ParallaxLayer.OBJECTS;




    public VisualObject(){

    }





    public void setPosition(

            float x,

            float y

    ){

        this.x = x;

        this.y = y;

    }





    public void setCameraTransform(

            CameraTransform transform

    ){

        this.cameraTransform = transform;

    }





    public void setLayer(

            ParallaxLayer layer

    ){

        if(layer != null){

            this.layer = layer;

        }

    }





    public ParallaxLayer layer(){

        return layer;

    }





    protected float renderX(){


        if(cameraTransform == null){

            return x;

        }



        return x

                +

                cameraTransform.x()

                *

                layer.strength();


    }





    protected float renderY(){


        if(cameraTransform == null){

            return y;

        }



        return y

                +

                cameraTransform.y()

                *

                layer.strength();


    }





    public abstract void render(

            GuiGraphics graphics

    );





    public void tick(){

    }





    public float x(){

        return x;

    }





    public float y(){

        return y;

    }


}