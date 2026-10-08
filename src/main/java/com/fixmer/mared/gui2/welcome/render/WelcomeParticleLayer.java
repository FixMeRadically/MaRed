package com.fixmer.mared.gui2.welcome.render;


import com.fixmer.mared.gui2.welcome.particles.MaredParticleSystem;


import net.minecraft.client.gui.GuiGraphics;



public final class WelcomeParticleLayer
        implements WelcomeLayerRenderer {



    private final MaredParticleSystem particles;




    public WelcomeParticleLayer(){

        particles =
                new MaredParticleSystem();

    }





    public MaredParticleSystem particles(){

        return particles;

    }





    @Override
    public void render(

            GuiGraphics graphics,

            int width,

            int height,

            float partialTick

    ){


        particles.render(
                graphics
        );


    }



}