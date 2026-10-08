package com.fixmer.mared.gui2.welcome.render;


import com.fixmer.mared.gui2.visual.object.VisualObject;


import net.minecraft.client.gui.GuiGraphics;



public final class WelcomeObjectRenderer {



    public void render(

            GuiGraphics graphics,

            VisualObject object

    ){


        if(object == null){

            return;

        }


        object.render(

                graphics

        );


    }


}