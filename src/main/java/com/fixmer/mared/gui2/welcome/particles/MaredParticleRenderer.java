package com.fixmer.mared.gui2.welcome.particles;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;



public final class MaredParticleRenderer {



    private MaredParticleRenderer(){}





    public static void render(

            GuiGraphics graphics,

            MaredParticle particle

    ){



        graphics.drawString(

                Minecraft.getInstance().font,

                Component.literal(
                        particle.symbol()
                ),

                (int)particle.x(),

                (int)particle.y(),

                particle.color(),

                false

        );


    }



}