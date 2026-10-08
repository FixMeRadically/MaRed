package com.fixmer.mared.gui2.welcome.particles;


import net.minecraft.client.gui.GuiGraphics;



public final class MaredParticleSystem {



    private final MaredParticleEmitter emitter =
            new MaredParticleEmitter();





    public void spawnLogic(){

        emitter.emitCode(
                400,
                250
        );

    }





    public void tick(){

        emitter.tick();

    }





    public void render(
            GuiGraphics graphics
    ){


        for(
                MaredParticle particle :
                emitter.particles()
        ){


            MaredParticleRenderer.render(
                    graphics,
                    particle
            );


        }


    }


}