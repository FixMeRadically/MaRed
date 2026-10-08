package com.fixmer.mared.gui2.visual.scene;



public final class VisualEnvironment {



    private int backgroundColor =
            0xFF101018;



    private float fog;



    private float brightness = 1f;





    public int backgroundColor(){

        return backgroundColor;

    }





    public void backgroundColor(

            int color

    ){

        this.backgroundColor = color;

    }





    public float fog(){

        return fog;

    }





    public void fog(

            float value

    ){

        fog = value;

    }





    public float brightness(){

        return brightness;

    }





    public void brightness(

            float value

    ){

        brightness = value;

    }


}