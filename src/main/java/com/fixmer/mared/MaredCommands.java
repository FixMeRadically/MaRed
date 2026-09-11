package com.fixmer.mared;

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
                            () -> Component.literal("Привет из мода Mared!"),
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
                                Component.literal("Эту команду может выполнить только игрок.")
                            );
                            return 0;
                        }

                        // Проверка на оператора (уровень 2 — стандартный OP).
                        if (!player.hasPermissions(2)) {
                            context.getSource().sendFailure(
                                Component.literal("У вас нет прав для открытия редактора Mared.")
                            );
                            return 0;
                        }

                        if (FMLEnvironment.dist != Dist.CLIENT) {
                            context.getSource().sendFailure(
                                Component.literal("GUI можно открыть только на клиенте.")
                            );
                            return 0;
                        }

                        Minecraft.getInstance().execute(() -> {
                            Minecraft.getInstance().setScreen(new MaredScriptsScreen());
                        });

                        context.getSource().sendSuccess(
                            () -> Component.literal("Открываю редактор Mared..."),
                            false
                        );
                        return 1;
                    })
                )
        );
    }
}