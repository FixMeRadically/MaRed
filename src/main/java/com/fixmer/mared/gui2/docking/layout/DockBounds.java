package com.fixmer.mared.gui2.docking.layout;



/**
 * Геометрия Dock панели.
 *
 * Аналог Rectangle,
 * но специально для UI.
 */
public final class DockBounds {



    private int x;

    private int y;

    private int width;

    private int height;





    public DockBounds(
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





    public void set(
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


}