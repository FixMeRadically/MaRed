package com.fixmer.mared.gui2.visual.effect;


import net.minecraft.client.gui.GuiGraphics;



public class MorphEffect
        implements VisualEffect {



    protected EffectState state =
            EffectState.IDLE;



    protected float progress = 0f;




    @Override
    public void start(){

        state =
                EffectState.TRANSFORMING;

        progress =
                0f;

    }





    @Override
    public void tick(){


        if(state != EffectState.TRANSFORMING){

            return;

        }



        progress += 0.02f;



        if(progress >= 1f){

            progress = 1f;

            state =
                    EffectState.COMPLETE;

        }


    }





    @Override
    public void render(

            GuiGraphics graphics

    ){

    }





    public EffectState state(){

        return state;

    }





    public float progress(){

        return progress;

    }





    public boolean finished(){

        return state == EffectState.COMPLETE;

    }


}