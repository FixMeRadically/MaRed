package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.assets.MaredAsset;
import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;



public abstract class AssetVisualObject
        extends AbstractVisualObject {



    protected final MaredAsset asset;



    protected float scale = 1.0f;



    protected float rotation;



    protected float opacity = 1.0f;





    protected AssetVisualObject(

            MaredAsset asset

    ){

        super(
                ParallaxLayer.OBJECTS
        );


        this.asset = asset;

    }





    public MaredAsset asset(){

        return asset;

    }





    public float scale(){

        return scale;

    }





    public void scale(

            float value

    ){

        scale = value;

    }





    public float rotation(){

        return rotation;

    }





    public void rotation(

            float value

    ){

        rotation = value;

    }





    public float opacity(){

        return opacity;

    }





    public void opacity(

            float value

    ){

        opacity = value;

    }


}