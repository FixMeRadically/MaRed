package com.fixmer.mared.mixin;
import com.fixmer.mared.dev.client.LocalRecovery91;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.client.gui.screens.Screen;import net.minecraft.world.level.LevelSettings;import net.minecraft.world.level.levelgen.WorldOptions;import net.minecraft.world.level.levelgen.WorldDimensions;import net.minecraft.core.RegistryAccess;import net.minecraft.core.LayeredRegistryAccess;import net.minecraft.server.RegistryLayer;import net.minecraft.server.ReloadableServerResources;import net.minecraft.world.level.storage.LevelStorageSource;import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;import org.spongepowered.asm.mixin.injection.Inject;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Existing/new/recreated worlds share the same async local recovery gate. */
@Mixin(WorldOpenFlows.class)
public abstract class LocalWorldOpenMixin91 {
 @Inject(method="openWorld",at=@At("HEAD"),cancellable=true,require=1)
 private void genesis$open91(String id,Runnable fallback,CallbackInfo ci){if(LocalRecovery91.interceptWorldOpen(()->((WorldOpenFlows)(Object)this).openWorld(id,fallback)))ci.cancel();}
 @Inject(method="createFreshLevel",at=@At("HEAD"),cancellable=true,require=1)
 private void genesis$new91(String id,LevelSettings settings,WorldOptions options,java.util.function.Function<RegistryAccess,WorldDimensions> dimensions,Screen previous,CallbackInfo ci){if(LocalRecovery91.interceptWorldOpen(()->((WorldOpenFlows)(Object)this).createFreshLevel(id,settings,options,dimensions,previous)))ci.cancel();}
 @Inject(method="createLevelFromExistingSettings",at=@At("HEAD"),cancellable=true,require=1)
 private void genesis$existing91(LevelStorageSource.LevelStorageAccess storage,ReloadableServerResources resources,LayeredRegistryAccess<RegistryLayer> registry,WorldData data,CallbackInfo ci){if(LocalRecovery91.interceptWorldOpen(()->((WorldOpenFlows)(Object)this).createLevelFromExistingSettings(storage,resources,registry,data),()->{try{storage.close();}catch(java.io.IOException ignored){}},storage))ci.cancel();}
}
