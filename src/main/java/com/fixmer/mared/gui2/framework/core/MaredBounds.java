package com.fixmer.mared.gui2.framework.core;


/**
 * Геометрия любого GUI элемента MaRed.
 *
 * Все панели работают только через Bounds.
 * Никаких screenWidth - x.
 */
public final class MaredBounds {

    private int x;
    private int y;

    private int width;
    private int height;


    public MaredBounds(
            int x,
            int y,
            int width,
            int height
    ){
        set(x,y,width,height);
    }



    public void set(
            int x,
            int y,
            int width,
            int height
    ){

        this.x = x;
        this.y = y;

        this.width = Math.max(0,width);
        this.height = Math.max(0,height);
    }



    public boolean contains(
            double mouseX,
            double mouseY
    ){

        return mouseX >= x &&
               mouseX <= x + width &&
               mouseY >= y &&
               mouseY <= y + height;
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



    public int right(){
        return x + width;
    }


    public int bottom(){
        return y + height;
    }



    public MaredBounds copy(){

        return new MaredBounds(
                x,
                y,
                width,
                height
        );
    }



    public void clampSize(
            int minWidth,
            int minHeight,
            int maxWidth,
            int maxHeight
    ){

        width = Math.max(
                minWidth,
                Math.min(
                        width,
                        maxWidth
                )
        );


        height = Math.max(
                minHeight,
                Math.min(
                        height,
                        maxHeight
                )
        );

    }


    @Override
    public String toString(){

        return "Bounds{" +
                x + "," +
                y + " " +
                width + "x" +
                height +
                "}";

    }

}