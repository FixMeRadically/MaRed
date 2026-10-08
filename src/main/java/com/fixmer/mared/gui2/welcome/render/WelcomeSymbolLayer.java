package com.fixmer.mared.gui2.welcome.render;


import com.fixmer.mared.gui2.welcome.render.animation.AnimatedWelcomeLayer;
import com.fixmer.mared.gui2.welcome.render.animation.LayerAnimationState;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;



public final class WelcomeSymbolLayer
        extends AnimatedWelcomeLayer
        implements WelcomeLayerRenderer {



    private String symbol = "";





    public WelcomeSymbolLayer(){


        registerLayer(
                "symbols"
        );


    }





    public void setSymbol(

            String symbol

    ){

        this.symbol = symbol;

    }





    public void updateAnimation(

            float value

    ){

        update(

                "symbols",

                value

        );

    }





    @Override
    public void render(

            GuiGraphics graphics,

            int width,

            int height,

            float partialTick

    ){


        if(symbol.isEmpty())

            return;



        LayerAnimationState state =
                state("symbols");



        if(state == null)

            return;




        int alpha =
                (int)
                (
                        state.opacity()
                        *
                        255
                );



        int color =
                alpha << 24
                |
                0xFFFFFF;




        graphics.drawCenteredString(

                Minecraft.getInstance()
                        .font,

                Component.literal(symbol),

                width / 2,

                height / 2,

                color

        );


    }


}