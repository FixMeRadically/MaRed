package com.fixmer.mared.command;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui.MaredEditorScreen;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Mared.MOD_ID)
public class MaredCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("mared")
                .then(Commands.literal("hello")
                    .executes(context -> {
                        context.getSource().sendSuccess(
                            () -> Component.literal("Hello from Mared!"),
                            false
                        );
                        return 1;
                    })
                )
                .then(Commands.literal("gui")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player == null) {
                            context.getSource().sendFailure(
                                Component.literal("Only a player can run this command.")
                            );
                            return 0;
                        }

                        if (!player.hasPermissions(2)) {
                            context.getSource().sendFailure(
                                Component.literal("You don't have permission to open Mared.")
                            );
                            return 0;
                        }

                        if (FMLEnvironment.dist != Dist.CLIENT) {
                            context.getSource().sendFailure(
                                Component.literal("GUI can only be opened on the client.")
                            );
                            return 0;
                        }

                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().setScreen(new MaredEditorScreen());
                        });

                        context.getSource().sendSuccess(
                            () -> Component.literal("Opening Mared editor..."),
                            false
                        );
                        return 1;
                    })
                )
        );
    }
}