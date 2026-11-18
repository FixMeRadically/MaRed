package com.fixmer.mared.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.fixmer.mared.commands.input.MaredKeyBlocker;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method="onPress",at=@At("HEAD"),cancellable=true)
    private void genesis$returnMouse(long window,int button,int action,int modifiers,CallbackInfo ci){
        if(com.fixmer.mared.technology.server.setup.ServerReturnClient108.mouseReplacement(window,button,action))ci.cancel();
    }

    @Inject(method="onMove",at=@At("HEAD"),require=0)
    private void genesis$observeRestartMouse(long window,double x,double y,CallbackInfo ci){
        com.fixmer.mared.dev.client.DevRestartWindow.inputEvent(window,false);
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void mared$blockTurnPlayer(CallbackInfo ci) {
        if (MaredKeyBlocker.isMouseMotionBlocked()) {
            ci.cancel();
        }
    }
}