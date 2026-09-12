package com.fixmer.mared;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public class MaredGiveCommand extends MaredScriptCommand {

    private final String target;
    private final String item;
    private final int count;

    public MaredGiveCommand(String target, String item, int count) {
        this.target = target;
        this.item = item;
        this.count = count;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MinecraftServer server = ctx.getServer();
        if (server == null) return false;

        // Заменяем @s на имя игрока-инициатора.
        String resolvedTarget = target;
        if ("@s".equals(target)) {
            if (ctx.getInitiator() != null) {
                resolvedTarget = ctx.getInitiator().getName().getString();
            } else {
                ctx.log("[give] нет игрока для @s");
                return false;
            }
        }

        // Выполняем команду от имени сервера.
        String command = "give " + resolvedTarget + " " + item + " " + count;
        CommandSourceStack source = server.createCommandSourceStack()
            .withSuppressedOutput();

        try {
            server.getCommands().performPrefixedCommand(source, command);
            ctx.log("[give] " + resolvedTarget + " ← " + count + " × " + item);
            return true;
        } catch (Exception e) {
            ctx.log("[give] ошибка: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String describe() {
        return "give " + target + " " + item + " " + count;
    }
}