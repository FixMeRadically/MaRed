package com.fixmer.mared.mixin;

import com.fixmer.mared.script.MaredKeyBlocker;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void mared$blockTurnPlayer(CallbackInfo ci) {
        if (MaredKeyBlocker.isMouseMotionBlocked()) {
            ci.cancel();
        }
    }
}