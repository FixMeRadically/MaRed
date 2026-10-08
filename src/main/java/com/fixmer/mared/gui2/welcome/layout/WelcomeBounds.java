package com.fixmer.mared.gui2.welcome.layout;



public final class WelcomeBounds {


    private final int x;


    private final int y;


    private final int width;


    private final int height;



    public WelcomeBounds(

            int x,

            int y,

            int width,

            int height

    ){

        this.x = x;

        this.y = y;

        this.width = width;

        this.height = height;

    }





    public boolean contains(

            double mouseX,

            double mouseY

    ){

        return mouseX >= x

                && mouseX <= x + width

                && mouseY >= y

                && mouseY <= y + height;

    }





    public int x(){

        return x;

    }



    public int y(){

        return y;

    }



    public int width(){

        return width;

    }



    public int height(){

        return height;

    }


}