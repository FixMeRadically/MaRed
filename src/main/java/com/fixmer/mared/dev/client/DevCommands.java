package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.technology.scripts.BackgroundAutoApply;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.api.distmarker.Dist;

/** Local commands; never invoke a client-only service on a dedicated server. */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class DevCommands {

    private DevCommands() {}

    private static int autoControl(CommandSourceStack source,java.util.concurrent.CompletableFuture<Boolean> task){
        task.whenComplete((ok,error)->net.minecraft.client.Minecraft.getInstance().execute(()->{
            if(error!=null||!Boolean.TRUE.equals(ok))source.sendFailure(Component.literal(com.fixmer.mared.MaredLang.get("mared.scripts.background.control_failed")));
            else source.sendSuccess(()->Component.literal(BackgroundAutoApply.describe()),false);
        }));return 1;
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> dev = Commands.literal("dev")
            .then(Commands.literal("auto")
                .then(Commands.literal("status").executes(ctx->{ctx.getSource().sendSuccess(()->Component.literal(BackgroundAutoApply.describe()),false);return 1;}))
                .then(Commands.literal("log").executes(ctx->{ctx.getSource().sendSuccess(()->Component.literal(BackgroundAutoApply.chatReport()),false);return 1;}))
                .then(Commands.literal("pause").executes(ctx->{return autoControl(ctx.getSource(),BackgroundAutoApply.pause());}))
                .then(Commands.literal("resume").executes(ctx->{return autoControl(ctx.getSource(),BackgroundAutoApply.resume());}))
                .then(Commands.literal("retry").executes(ctx->{return autoControl(ctx.getSource(),BackgroundAutoApply.retry());}))
                .then(Commands.literal("stop").executes(ctx->{return autoControl(ctx.getSource(),BackgroundAutoApply.stop());}))
            )
            .then(Commands.literal("history").executes(ctx->{var mc=net.minecraft.client.Minecraft.getInstance();mc.tell(()->mc.setScreen(new RestartHistoryScreen97(mc.screen)));return 1;}))
            .then(Commands.literal("restart")
                .executes(ctx -> {
                    boolean ok = DevRestartService.requestRestart();
                    if (!ok) {
                        ctx.getSource().sendFailure(
                            Component.literal("mared: dev restart declined (see chat)"));
                        return 0;
                    }
                    return 1;
                })
            )
            .then(Commands.literal("status")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    src.sendSuccess(() -> Component.literal(
                        "mared dev: " + DevMode.sourceDescription()
                        + " | " + DevRestartService.describe()), false);
                    return 1;
                })
            );

        event.getDispatcher().register(
            Commands.literal("mared").then(dev)
        );
    }
}