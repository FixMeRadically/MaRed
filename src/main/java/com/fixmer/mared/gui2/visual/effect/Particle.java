package com.fixmer.mared.gui2.visual.effect;



public final class Particle {



    private float life = 1;



    private float x;


    private float y;





    public void tick(){


        life -= 0.01f;


        y -= 0.5f;


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


}