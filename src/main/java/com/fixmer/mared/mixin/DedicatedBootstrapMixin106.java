package com.fixmer.mared.mixin;
import com.fixmer.mared.technology.server.firstlaunch.ServerAuto106;
import net.minecraft.server.MinecraftServer;import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;import org.spongepowered.asm.mixin.injection.Inject;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Initial bootstrap yields normally; no Runtime.halt, System.exit or loaded-world mutation here. */
@Mixin(DedicatedServer.class)
public abstract class DedicatedBootstrapMixin106 {
 @Inject(method="initServer",at=@At("HEAD"),cancellable=true)
 private void genesis$controller106(CallbackInfoReturnable<Boolean> cir){if(ServerAuto106.transfer((MinecraftServer)(Object)this))cir.setReturnValue(false);}
}
