package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Обёртка над ванильной /give.
 *
 * Mared-синтаксис:
 *     give <target> <item> [count]
 *
 * Проксирует в:
 *     /give <target> <item> [count]
 *
 * Переменные ($item, $target, ...) подставляются через ctx.substitute().
 * Работает через серверный API — не зависит от готовности клиента.
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
        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();

        if (server == null && initiator != null) {
            server = initiator.getServer();
        }
        if (server == null) {
            ctx.log("[warn] give: server is null");
            return true;
        }

        String resolvedTarget = ctx.substitute(target);
        String resolvedItem = ctx.substitute(item);

        StringBuilder cmd = new StringBuilder("give ");
        cmd.append(resolvedTarget).append(' ');
        cmd.append(resolvedItem);
        if (count != 1) {
            cmd.append(' ').append(count);
        }

        String command = cmd.toString();
        try {
            server.getCommands().performPrefixedCommand(
                initiator != null
                    ? initiator.createCommandSourceStack()
                    : server.createCommandSourceStack(),
                command
            );
            ctx.log("[give] /" + command);
        } catch (Exception e) {
            ctx.log("[give error] /" + command + " — " + e.getMessage());
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "give " + target + " " + item + " " + count; }
}