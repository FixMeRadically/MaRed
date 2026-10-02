package com.fixmer.mared.mixin.gui2;


import com.fixmer.mared.gui2.integration.MaredPauseButton;

import net.minecraft.client.gui.screens.PauseScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(PauseScreen.class)
public abstract class MaredPauseScreenMixin {



    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void mared$addButton(
            CallbackInfo ci
    ){


        com.fixmer.mared.Mared.LOGGER.info(
                "[MaRed] PauseScreen injected"
        );


        ScreenAccessor accessor =
                (ScreenAccessor)(Object)this;


        accessor.mared$addRenderableWidget(
                new MaredPauseButton(
                        20,
                        20,
                        160,
                        24
                )
        );


        com.fixmer.mared.Mared.LOGGER.info(
                "[MaRed] Button added"
        );

    }


}