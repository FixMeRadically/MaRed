package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * say <текст> [scope=all|self|server]
 *
 * scope=all (по умолчанию) — broadcastSystemMessage.
 * scope=self             — только инициатору.
 *
 * НЕ использует performPrefixedCommand — Brigadier падал на
 * незакрытых кавычках и вешал игру.
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
        String resolved = resolveText(text, ctx);

        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();

        if (server == null && initiator != null) {
            server = initiator.getServer();
        }
        if (server == null) {
            ctx.log("[warn] say: server is null");
            return true;
        }

        try {
            if ("self".equalsIgnoreCase(scope)) {
                if (initiator == null) {
                    ctx.log("[warn] say self: initiator is null");
                    return true;
                }
                initiator.sendSystemMessage(Component.literal(resolved));
                MaredEventRegistry.suppressChat(500);
            } else {
                Component msg = Component.literal("[Server] " + resolved);
                server.getPlayerList().broadcastSystemMessage(msg, false);
                ctx.log("[say] " + resolved);
                MaredEventRegistry.suppressChat(500);
            }
        } catch (Exception e) {
            ctx.log("[error] say: " + e.getMessage());
        }
        return true;
    }

    private String resolveText(String raw, MaredScriptContext ctx) {
        if (raw == null || raw.isEmpty()) return "";

        if (looksLikeConcat(raw)) {
            try {
                Object v = MaredExpr.eval(raw, ctx);
                return MaredExpr.stringify(v);
            } catch (Exception ignored) {}
        }

        return ctx.substitute(raw);
    }

    private boolean looksLikeConcat(String s) {
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString && c == '\\' && i + 1 < s.length()) { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString && c == '+') return true;
        }
        return false;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "say \"" + text + "\""; }
}