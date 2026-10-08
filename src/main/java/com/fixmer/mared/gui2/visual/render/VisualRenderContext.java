package com.fixmer.mared.gui2.visual.render;


import com.fixmer.mared.gui2.visual.camera.CameraTransform;

import net.minecraft.client.gui.GuiGraphics;



public final class VisualRenderContext {



    private final GuiGraphics graphics;



    private final CameraTransform camera;



    private final int width;



    private final int height;





    public VisualRenderContext(

            GuiGraphics graphics,

            CameraTransform camera,

            int width,

            int height

    ){

        this.graphics = graphics;

        this.camera = camera;

        this.width = width;

        this.height = height;

    }





    public GuiGraphics graphics(){

        return graphics;

    }





    public CameraTransform camera(){

        return camera;

    }





    public int width(){

        return width;

    }





    public int height(){

        return height;

    }


}