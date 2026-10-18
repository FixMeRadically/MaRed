package com.fixmer.mared.mixin;

import com.fixmer.mared.dev.client.DevQuickStart;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wait until TitleScreen.init completed; constructors must never open another screen/world. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void mared$quickStart(CallbackInfo ci) { DevQuickStart.intercept(); }
}
