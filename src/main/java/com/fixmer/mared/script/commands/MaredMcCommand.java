package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * mc <команда> — проксирует ванильную команду Minecraft через сервер.
 *
 * Переменные внутри команды подставляются через ctx.substitute().
 * Пример: mc give @s $item 1  →  /give @s apple 1
 *
 * Работает и на клиенте, и в persistent-контексте (где mc.player ещё null),
 * потому что используется серверный API, а не player.connection.sendCommand.
 */
public class MaredMcCommand extends MaredScriptCommand {

    private final String command;

    public MaredMcCommand(String command) {
        this.command = command;
    }

    public String getCommand() { return command; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();

        if (server == null && initiator != null) {
            server = initiator.getServer();
        }
        if (server == null) {
            ctx.log("[warn] mc: server is null");
            return true;
        }

        String resolved = ctx.substitute(command);
        String cmd = resolved.startsWith("/") ? resolved.substring(1) : resolved;

        try {
            // Если есть инициатор — команда выполняется от его имени
            // (учитывает его позицию, права, инвентарь).
            // Иначе — от консоли сервера.
            server.getCommands().performPrefixedCommand(
                initiator != null
                    ? initiator.createCommandSourceStack()
                    : server.createCommandSourceStack(),
                cmd
            );
            ctx.log("[mc] /" + cmd);
        } catch (Exception e) {
            ctx.log("[mc error] /" + cmd + " — " + e.getMessage());
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "mc " + command; }
}