package com.fixmer.mared.commands.server_cmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;

/**
 * mc <команда> — проксирует ванильную команду Minecraft.
 *
 * FIX F: разэкранирование кавычек перед substitute.
 */
public class MaredMcCommand extends MaredScriptCommand {

    private final String command;

    public MaredMcCommand(String command) {
        this.command = command;
    }

    public String getCommand() { return command; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String raw = command == null ? "" : command;
        raw = MaredDebugCommand.unescapeQuotes(raw);
        String resolved = ctx.substitute(raw);
        String cmd = resolved.startsWith("/") ? resolved.substring(1) : resolved;

        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();
        if (server == null && initiator != null) server = initiator.getServer();

        if (server != null) {
            try {
                server.getCommands().performPrefixedCommand(
                    initiator != null
                        ? initiator.createCommandSourceStack()
                        : server.createCommandSourceStack(),
                    cmd
                );
                ctx.log("[mc] /" + cmd);
                return true;
            } catch (Exception e) {
                ctx.log("[mc error] /" + cmd + " — " + e.getMessage());
                return true;
            }
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            ctx.log("[warn] mc: no player connection");
            return true;
        }

        try {
            player.connection.sendCommand(cmd);
            ctx.log("[mc] /" + cmd);
        } catch (Exception e) {
            ctx.log("[mc error] /" + cmd + " — " + e.getMessage());
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "mc " + command; }
}