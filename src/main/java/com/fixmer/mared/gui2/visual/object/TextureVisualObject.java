package com.fixmer.mared.gui2.visual.object;


import com.fixmer.mared.gui2.assets.TextureAsset;



import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;



public final class TextureVisualObject
        extends AssetVisualObject {



    private final TextureAsset texture;





    public TextureVisualObject(

            TextureAsset texture

    ){

        super(texture);

        this.texture = texture;

    }





    @Override
    public void render(

            GuiGraphics graphics

    ){



        ResourceLocation location =
                ResourceLocation.parse(

                        texture.path()

                );



        graphics.blit(

                location,

                (int)x,

                (int)y,

                0,

                0,

                128,

                128,

                128,

                128

        );


    }


}