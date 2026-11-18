package com.fixmer.mared.mixin;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observe callback delivery only; keep Minecraft and other mods in control of key handling. */
@Mixin(KeyboardHandler.class)
public abstract class RestartKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),require=0)
    private void genesis$observeRestartKey(long window,int key,int scan,int action,int modifiers,CallbackInfo ci){
        com.fixmer.mared.dev.client.DevRestartWindow.inputEvent(window,true);
    }
}
