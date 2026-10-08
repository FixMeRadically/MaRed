package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;


import net.minecraft.client.gui.GuiGraphics;



public final class LandscapeVisualObject
        extends AbstractVisualObject {



    private int color;



    private int width;



    private int height;





    public LandscapeVisualObject(

            int color

    ){

        super(
                ParallaxLayer.OBJECTS
        );


        this.color = color;


        width = 400;

        height = 150;

    }





    @Override
    public void render(

            GuiGraphics graphics

    ){



        graphics.fill(

                (int)x,

                (int)y,

                (int)x + width,

                (int)y + height,

                color

        );


    }


}