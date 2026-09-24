package com.fixmer.mared.commands.server_cmd;

import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * give <target> <item> [count]
 */
public class MaredGiveCommand extends MaredScriptCommand {

    private final String target;
    private final String item;
    private final int count;

    public MaredGiveCommand(String target, String item, int count) {
        this.target = target;
        this.item = item;
        this.count = count;
    }

    public String getTarget() { return target; }
    public String getItem()   { return item; }
    public int getCount()     { return count; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String resolvedTarget = ctx.substitute(MaredDebugCommand.unescapeQuotes(target));
        String resolvedItem = ctx.substitute(MaredDebugCommand.unescapeQuotes(item));

        StringBuilder sb = new StringBuilder(32);
        sb.append("give ").append(resolvedTarget).append(' ').append(resolvedItem);
        if (count != 1) sb.append(' ').append(count);
        String command = sb.toString();

        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();
        if (server == null && initiator != null) server = initiator.getServer();

        if (server != null) {
            try {
                server.getCommands().performPrefixedCommand(
                    initiator != null
                        ? initiator.createCommandSourceStack()
                        : server.createCommandSourceStack(),
                    command);
                ctx.log("[give] /" + command);
            } catch (Exception e) {
                ctx.log("[give error] /" + command + " — " + e.getMessage());
            }
            return true;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            ctx.log("[warn] give: no player connection");
            return true;
        }
        try {
            player.connection.sendCommand(command);
            ctx.log("[give] /" + command);
        } catch (Exception e) {
            ctx.log("[give error] /" + command + " — " + e.getMessage());
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override
    public String describe() { return "give " + target + " " + item + " " + count; }
}