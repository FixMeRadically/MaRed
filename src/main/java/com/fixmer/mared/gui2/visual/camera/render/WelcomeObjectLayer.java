package com.fixmer.mared.gui2.welcome.render;


import com.fixmer.mared.gui2.visual.scene.VisualScene;
import com.fixmer.mared.gui2.visual.camera.CameraTransform;
import com.fixmer.mared.gui2.visual.render.VisualSceneRenderer;


import net.minecraft.client.gui.GuiGraphics;



public final class WelcomeObjectLayer
        implements WelcomeLayerRenderer {



    private VisualScene scene;


    private CameraTransform camera;




    public WelcomeObjectLayer(){

    }





    public void setCamera(

            CameraTransform camera

    ){

        this.camera = camera;

    }





    public void setScene(

            VisualScene scene

    ){

        this.scene = scene;

    }





    @Override
    public void render(

            GuiGraphics graphics,

            int width,

            int height,

            float partialTick

    ){


        if(scene == null){

            return;

        }



        if(camera == null){

            return;

        }





        VisualSceneRenderer.render(

                graphics,

                scene,

                camera,

                width,

                height

        );


    }


}