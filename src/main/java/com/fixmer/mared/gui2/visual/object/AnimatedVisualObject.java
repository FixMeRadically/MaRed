package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.assets.MaredAsset;



public abstract class AnimatedVisualObject
        extends AssetVisualObject {



    protected float animationTime;




    protected AnimatedVisualObject(

            MaredAsset asset

    ){

        super(asset);

    }





    @Override
    public void tick(){


        animationTime += 0.016f;


    }





    public float animationTime(){

        return animationTime;

    }


}