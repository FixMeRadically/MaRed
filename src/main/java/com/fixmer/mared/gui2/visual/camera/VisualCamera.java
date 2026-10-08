package com.fixmer.mared.gui2.visual.camera;



public final class VisualCamera {


    private float x;

    private float y;



    public void tick(){

    }





    public void moveMouse(

            float mouseX,

            float mouseY,

            int width,

            int height

    ){


        float nx =
                mouseX / width - 0.5f;


        float ny =
                mouseY / height - 0.5f;



        x =
                nx * 20f;



        y =
                ny * 20f;


    }





    public float x(){

        return x;

    }





    public float y(){

        return y;

    }





    public CameraTransform transform(){
        CameraTransform t = new CameraTransform();
        t.set(x, y);
        return t;
    }


}