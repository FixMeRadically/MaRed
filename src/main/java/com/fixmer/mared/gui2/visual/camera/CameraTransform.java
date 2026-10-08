package com.fixmer.mared.gui2.visual.camera;



public final class CameraTransform {


    private float offsetX;


    private float offsetY;



    public CameraTransform(){

    }




    public void set(

            float x,

            float y

    ){

        offsetX = x;

        offsetY = y;

    }





    public float x(){

        return offsetX;

    }





    public float y(){

        return offsetY;

    }


}