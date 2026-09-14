package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredScriptContext;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * say <текст> [scope=all|self|server] — отправить сообщение.
 *
 * scope=all (по умолчанию) — через ванильную /say, все увидят.
 * scope=self             — только себе (sendSystemMessage).
 * scope=server           — то же что all, но через /say.
 *
 * ВАЖНО: используется серверный API (ctx.getInitiator()).
 * Клиентский mc.player здесь не нужен — persistent-контекст
 * выполняется до того, как клиент готов.
 */
public class MaredSayCommand extends MaredScriptCommand {

    private final String text;
    private final String scope;

    public MaredSayCommand(String text, String scope) {
        this.text = text;
        this.scope = scope;
    }

    public String getText() { return text; }
    public String getScope() { return scope; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String resolved = ctx.substitute(text);
        if (resolved == null) resolved = "";

        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();

        // Fallback: если сервер недоступен
        if (server == null && initiator != null) {
            server = initiator.getServer();
        }
        if (server == null) {
            ctx.log("[warn] say: server is null");
            return true;
        }

        try {
            if ("self".equalsIgnoreCase(scope)) {
                // Только инициатору — sendSystemMessage работает на сервере
                if (initiator == null) {
                    ctx.log("[warn] say self: initiator is null");
                    return true;
                }
                initiator.sendSystemMessage(Component.literal(resolved));
                MaredEventRegistry.suppressChat(500);
            } else {
                // all / server — ванильная /say через серверную команду
                String cmd = "say " + resolved;
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    cmd
                );
                ctx.log("[mc] /" + cmd);
                MaredEventRegistry.suppressChat(500);
            }
        } catch (Exception e) {
            ctx.log("[error] say: " + e.getMessage());
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "say \"" + text + "\""; }
}