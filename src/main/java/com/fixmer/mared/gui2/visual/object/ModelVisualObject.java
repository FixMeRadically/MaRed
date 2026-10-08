package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.assets.ModelAsset;


import net.minecraft.client.gui.GuiGraphics;



public final class ModelVisualObject
        extends AssetVisualObject {



    private final ModelAsset model;





    public ModelVisualObject(

            ModelAsset model

    ){

        super(model);

        this.model = model;

    }





    @Override
    public void render(

            GuiGraphics graphics

    ){


        /*
         * Заглушка первого уровня.
         *
         * Здесь позже будет:
         *
         * ModelRenderer
         * Mesh
         * AnimationController
         * Camera
         *
         */


        graphics.fill(

                (int)x,

                (int)y,

                (int)x + 100,

                (int)y + 100,

                0xFF888888

        );


    }


}