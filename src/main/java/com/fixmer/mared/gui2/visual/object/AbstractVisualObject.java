package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.visual.object.VisualObject;
import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;



public abstract class AbstractVisualObject
        extends VisualObject {



    protected float x;

    protected float y;



    protected final ParallaxLayer layer;





    protected AbstractVisualObject(

            ParallaxLayer layer

    ){

        this.layer = layer;

    }





    @Override
    public float x(){

        return x;

    }





    @Override
    public float y(){

        return y;

    }





    @Override
    public void setPosition(

            float x,

            float y

    ){

        this.x = x;

        this.y = y;

    }





    @Override
    public ParallaxLayer layer(){

        return layer;

    }



}