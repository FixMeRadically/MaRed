package com.fixmer.mared.gui2.welcome.input;



public final class WelcomeInputState {



    private double mouseX;


    private double mouseY;



    private boolean pressed;





    public void updateMouse(

            double x,

            double y

    ){

        mouseX = x;

        mouseY = y;

    }





    public void press(){


        pressed = true;


    }





    public void release(){


        pressed = false;


    }





    public double mouseX(){

        return mouseX;

    }





    public double mouseY(){

        return mouseY;

    }





    public boolean pressed(){

        return pressed;

    }


}