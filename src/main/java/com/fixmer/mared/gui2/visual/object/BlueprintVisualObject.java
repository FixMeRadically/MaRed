package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;


import net.minecraft.client.gui.GuiGraphics;



public final class BlueprintVisualObject
        extends AbstractVisualObject {



    private int nodes = 5;





    public BlueprintVisualObject(){

        super(
                ParallaxLayer.OBJECTS
        );

    }





    @Override
    public void render(

            GuiGraphics graphics

    ){


        for(
                int i = 0;
                i < nodes;
                i++
        ){


            graphics.fill(

                    (int)x + i * 50,

                    (int)y,

                    (int)x + i * 50 + 30,

                    (int)y + 30,

                    0xFF55AAFF

            );


        }


    }


}