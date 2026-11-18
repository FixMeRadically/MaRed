package com.fixmer.mared.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Same fields used by vanilla ConnectScreen's Cancel button. */
@Mixin(ConnectScreen.class)
public interface FirstLaunchConnectAccessor58 {
    @Accessor("connection") Connection genesisFirst58Connection();
    @Accessor("channelFuture") io.netty.channel.ChannelFuture genesisFirst58Future();
    @Accessor("aborted") void genesisFirst58SetAborted(boolean aborted);
}
