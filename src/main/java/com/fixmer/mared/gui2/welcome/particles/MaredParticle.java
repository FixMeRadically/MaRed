package com.fixmer.mared.gui2.welcome.particles;


public class MaredParticle {



    private float x;

    private float y;



    private float velocityX;

    private float velocityY;



    private float life;



    private float maxLife;



    private float size;



    private int color;



    private String symbol;





    public MaredParticle(
            float x,
            float y,
            String symbol,
            int color
    ){

        this.x = x;
        this.y = y;

        this.symbol = symbol;

        this.color = color;


        this.size = 1f;


        this.maxLife = 1f;

        this.life = maxLife;

    }





    public void update(){


        x += velocityX;

        y += velocityY;


        life -= 0.005f;


    }





    public boolean dead(){

        return life <= 0;

    }





    public float x(){

        return x;

    }



    public float y(){

        return y;

    }



    public float life(){

        return life;

    }



    public float size(){

        return size;

    }



    public String symbol(){

        return symbol;

    }



    public int color(){

        return color;

    }





    public void velocity(
            float x,
            float y
    ){

        velocityX = x;

        velocityY = y;

    }


}