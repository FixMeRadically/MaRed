package com.fixmer.mared.gui2.welcome.render;


import com.fixmer.mared.gui2.visual.scene.VisualScene;
import com.fixmer.mared.gui2.visual.camera.VisualCamera;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;



public final class WelcomeSceneController {


    private final VisualCamera camera;


    private VisualScene scene;



    public WelcomeSceneController(){

        camera = new VisualCamera();

    }




    public void tick(){

        camera.tick();


        if(scene != null){

            scene.tick();

        }

    }





    public void mouseMoved(

            double x,

            double y,

            int width,

            int height

    ){

        camera.moveMouse(

                (float)x,

                (float)y,

                width,

                height

        );

    }





    public VisualCamera camera(){

        return camera;

    }





    public VisualScene scene(){

        return scene;

    }





    public void setScene(

            VisualScene scene

    ){

        this.scene = scene;

    }





    public void render(

            GuiGraphics graphics,

            int width,

            int height,

            float partialTick

    ){


        if(scene == null){

            return;

        }



        graphics.flush();



        graphics.pose().pushPose();



        RenderSystem.setShaderColor(

                1f,

                1f,

                1f,

                1f

        );



        com.fixmer.mared.gui2.visual.render.VisualSceneRenderer.render(

                graphics,

                scene,

                camera.transform(),

                width,

                height

        );



        graphics.pose().popPose();



        graphics.flush();


    }



}