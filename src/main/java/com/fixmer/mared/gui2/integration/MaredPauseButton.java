package com.fixmer.mared.gui2.integration;


import com.fixmer.mared.gui2.launcher.MaredScreenManager;

import net.minecraft.client.gui.components.Button;



public final class MaredPauseButton extends Button {


    public MaredPauseButton(
            int x,
            int y,
            int width,
            int height
    ){

        super(
                x,
                y,
                width,
                height,

                net.minecraft.network.chat.Component.literal(
                        "MaRed Studio"
                ),

                button -> {

                    MaredScreenManager.openStudio();

                },

                DEFAULT_NARRATION
        );

    }

}