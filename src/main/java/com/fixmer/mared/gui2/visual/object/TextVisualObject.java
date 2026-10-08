package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;



public class TextVisualObject
        extends AbstractVisualObject {



    private String text;




    private int color;





    public TextVisualObject(

            String text,

            int color

    ){

        super(
                ParallaxLayer.SYMBOLS
        );


        this.text = text;

        this.color = color;

    }





    public void setText(

            String text

    ){

        this.text = text;

    }





    @Override
    public void render(

            GuiGraphics graphics

    ){



        graphics.drawCenteredString(

                Minecraft.getInstance()
                        .font,

                Component.literal(text),

                (int)x,

                (int)y,

                color

        );


    }


}