package com.fixmer.mared.commands.server_cmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;

/**
 * give <target> <item> [count]
 * FIX F: разэкранирование кавычек.
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
    public String getItem() { return item; }
    public int getCount() { return count; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String rawTarget = target == null ? "" : target;
        String rawItem = item == null ? "" : item;
        rawTarget = MaredDebugCommand.unescapeQuotes(rawTarget);
        rawItem = MaredDebugCommand.unescapeQuotes(rawItem);

        String resolvedTarget = ctx.substitute(rawTarget);
        String resolvedItem = ctx.substitute(rawItem);

        StringBuilder sb = new StringBuilder("give ");
        sb.append(resolvedTarget).append(' ').append(resolvedItem);
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
                    command
                );
                ctx.log("[give] /" + command);
                return true;
            } catch (Exception e) {
                ctx.log("[give error] /" + command + " — " + e.getMessage());
                return true;
            }
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

    @Override public String describe() { return "give " + target + " " + item + " " + count; }
}