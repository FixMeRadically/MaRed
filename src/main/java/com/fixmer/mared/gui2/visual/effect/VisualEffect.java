package com.fixmer.mared.gui2.visual.effect;


import net.minecraft.client.gui.GuiGraphics;



public interface VisualEffect {



    default void start(){

    }



    default void tick(){

    }



    default void render(
            GuiGraphics graphics
    ){

    }



    default boolean finished(){

        return false;

    }


}