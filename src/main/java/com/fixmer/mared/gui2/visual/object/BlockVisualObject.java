package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;


import net.minecraft.client.gui.GuiGraphics;



public final class BlockVisualObject
        extends AbstractVisualObject {



    private int size = 64;


    private int color;





    public BlockVisualObject(

            int color

    ){

        super(
                ParallaxLayer.OBJECTS
        );


        this.color = color;

    }





    public void size(

            int size

    ){

        this.size = size;

    }





    @Override
    public void render(

            GuiGraphics graphics

    ){


        graphics.fill(

                (int)x,

                (int)y,

                (int)x + size,

                (int)y + size,

                color

        );


    }


}