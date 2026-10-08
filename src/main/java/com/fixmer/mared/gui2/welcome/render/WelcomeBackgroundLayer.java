package com.fixmer.mared.gui2.welcome.render;


import com.fixmer.mared.gui2.modules.ModuleId;
import com.fixmer.mared.gui2.theme.module.ModuleThemeResolver;
import com.fixmer.mared.gui2.theme.module.ModuleVisualTheme;


import net.minecraft.client.gui.GuiGraphics;



public final class WelcomeBackgroundLayer
        implements WelcomeLayerRenderer {



    private ModuleId module;




    public void setModule(

            ModuleId module

    ){

        this.module = module;

    }





    @Override
    public void render(

            GuiGraphics graphics,

            int width,

            int height,

            float partialTick

    ){


        ModuleVisualTheme theme =
                ModuleThemeResolver.resolve(module);



        graphics.fill(

                0,

                0,

                width,

                height,

                theme.background()

        );



    }


}