package com.fixmer.mared.gui2.welcome.render;


import java.util.ArrayList;
import java.util.List;


import net.minecraft.client.gui.GuiGraphics;



public final class WelcomeSceneRenderer {



    private final List<WelcomeLayerRenderer> layers =
            new ArrayList<>();





    public void add(

            WelcomeLayerRenderer layer

    ){

        layers.add(layer);

    }





    public void clear(){

        layers.clear();

    }





    public void render(

            GuiGraphics graphics,

            int width,

            int height,

            float partialTick

    ){



        for(
                WelcomeLayerRenderer layer :
                layers

        ){


            layer.render(

                    graphics,

                    width,

                    height,

                    partialTick

            );


        }


    }


}