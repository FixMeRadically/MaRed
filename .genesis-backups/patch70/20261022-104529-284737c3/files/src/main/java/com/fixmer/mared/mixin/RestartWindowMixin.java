package com.fixmer.mared.mixin;

import com.mojang.blaze3d.platform.Window;
import com.fixmer.mared.dev.client.DevRestartWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Constructor return: the window exists, but its first ordinary game frame has not been shown. */
@Mixin(Window.class)
public abstract class RestartWindowMixin {
    @Inject(method="setMode",at=@At("HEAD"),require=0)
    private void genesis$restoreDecoration(CallbackInfo ci){DevRestartWindow.beforeMode((Window)(Object)this);}
    @Inject(method="<init>",at=@At("RETURN"),require=0)
    private void genesis$restoreSurface(CallbackInfo ci){DevRestartWindow.restore((Window)(Object)this);}
    @Inject(method="setMode",at=@At("RETURN"),require=0)
    private void genesis$coverMode(CallbackInfo ci){DevRestartWindow.beforeFrame((Window)(Object)this);}
    @Inject(method="updateDisplay",at=@At("HEAD"),require=0)
    private void genesis$coverBeforeSwap(CallbackInfo ci){DevRestartWindow.beforeFrame((Window)(Object)this);}
    @Inject(method="updateDisplay",at=@At("RETURN"),require=0)
    private void genesis$presentAfterSwap(CallbackInfo ci){DevRestartWindow.afterFrame((Window)(Object)this);com.fixmer.mared.technology.server.firstlaunch.FirstLaunchClient58.afterFrame();}
}
