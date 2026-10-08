package com.fixmer.mared.gui2.visual.effect;



import net.minecraft.client.gui.GuiGraphics;



import java.util.ArrayList;
import java.util.List;



public final class SymbolTransitionEffect
        extends MorphEffect {



    private final List<Character> symbols =
            new ArrayList<>();





    public SymbolTransitionEffect(
            String source
    ){


        for(char c : source.toCharArray()){

            symbols.add(c);

        }


    }





    @Override
    public void render(
            GuiGraphics graphics
    ){


        int index = 0;



        for(Character c : symbols){



            float alpha =
                    1.0f - progress();



            int color =
                    ((int)(alpha * 255) << 24)
                    | 0xFFFFFF;



            graphics.drawString(

                    net.minecraft.client.Minecraft
                            .getInstance()
                            .font,

                    String.valueOf(c),

                    300 + index * 8,

                    250,

                    color

            );



            index++;

        }



    }


}