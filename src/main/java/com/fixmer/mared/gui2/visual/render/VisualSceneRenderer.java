package com.fixmer.mared.gui2.visual.render;


import com.fixmer.mared.gui2.visual.effect.VisualEffect;
import com.fixmer.mared.gui2.visual.scene.VisualScene;
import com.fixmer.mared.gui2.visual.object.VisualObject;
import com.fixmer.mared.gui2.visual.camera.CameraTransform;
import com.fixmer.mared.gui2.visual.camera.render.DefaultVisualObjectRenderer;


import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;



public final class VisualSceneRenderer {



    private static final VisualObjectRenderer OBJECT_RENDERER =
            new DefaultVisualObjectRenderer();



    private VisualSceneRenderer(){

    }





    public static void render(

            GuiGraphics graphics,

            VisualScene scene,

            CameraTransform camera,

            int width,

            int height

    ){


        if(graphics == null || scene == null){

            return;

        }



        RenderSystem.setShaderColor(

                1f,

                1f,

                1f,

                1f

        );



        graphics.fill(

                0,

                0,

                width,

                height,

                scene.environment()
                        .backgroundColor()

        );





        for(
                VisualEffect effect :
                scene.effects()
        ){

            if(effect != null){

                effect.render(
                        graphics
                );

            }

        }





        for(
                VisualObject object :
                scene.objects()
        ){

            if(object == null){

                continue;

            }


            OBJECT_RENDERER.render(

                    object,

                    new VisualRenderContext(

                            graphics,

                            camera,

                            width,

                            height

                    )

            );

        }


    }


}