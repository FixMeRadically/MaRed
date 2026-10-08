package com.fixmer.mared.gui2.visual.camera.render;


import com.fixmer.mared.gui2.visual.object.VisualObject;
import com.fixmer.mared.gui2.visual.render.VisualObjectRenderer;
import com.fixmer.mared.gui2.visual.render.VisualRenderContext;


import net.minecraft.client.gui.GuiGraphics;



public final class DefaultVisualObjectRenderer
        implements VisualObjectRenderer {



    @Override
    public void render(

            VisualObject object,

            VisualRenderContext context

    ){


        GuiGraphics graphics =
                context.graphics();



        float depth =
                object.layer()
                        .strength();




        float x =

                (float) object.x()

                +

                (float) context.camera()
                        .x()

                *

                depth;





        float y =

                (float) object.y()

                +

                (float) context.camera()
                        .y()

                *

                depth;





        graphics.pose()
                .pushPose();





        graphics.pose()
                .translate(

                        x,

                        y,

                        0

                );





        object.render(

                graphics

        );





        graphics.pose()
                .popPose();


    }


}