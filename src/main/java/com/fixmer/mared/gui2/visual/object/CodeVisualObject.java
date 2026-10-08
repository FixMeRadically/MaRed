package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.visual.camera.ParallaxLayer;


import net.minecraft.client.gui.GuiGraphics;



public final class CodeVisualObject
        extends AbstractVisualObject {



    private String code;




    public CodeVisualObject(

            String code

    ){

        super(
                ParallaxLayer.SYMBOLS
        );


        this.code = code;

    }





    @Override
    public void render(

            GuiGraphics graphics

    ){


        String[] lines =
                code.split("\n");



        for(
                int i = 0;
                i < lines.length;
                i++
        ){


            graphics.drawString(

                    net.minecraft.client.Minecraft
                            .getInstance()
                            .font,

                    lines[i],

                    (int)x,

                    (int)y + i * 12,

                    0xFFAAAAAA

            );


        }


    }


}