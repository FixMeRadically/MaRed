package com.fixmer.mared.mixin;
import com.fixmer.mared.technology.server.setup.BootstrapHooks76;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.*;import net.minecraft.client.multiplayer.*;import net.minecraft.client.multiplayer.resolver.ServerAddress;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Intercept before any handshake is started, never after NeoForge rejects the mod list. */
@Mixin(ConnectScreen.class)
public abstract class BootstrapConnectMixin76 {
 @Inject(method="startConnecting",at=@At("HEAD"),cancellable=true)
 private static void genesis$prelogin76(Screen parent,Minecraft mc,ServerAddress address,ServerData data,boolean quick,TransferState transfer,CallbackInfo ci){if(BootstrapHooks76.intercept(parent,mc,address,data,quick,transfer))ci.cancel();}
}
