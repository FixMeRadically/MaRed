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
 * FIX A: табы заменяются на 4 пробела — шрифт MC не поддерживает \t.
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
        resolved = sanitizeTabs(resolved);

        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();
        if (server == null && initiator != null) server = initiator.getServer();
        if (server == null) { ctx.log("[warn] say: server is null"); return true; }

        try {
            if ("self".equalsIgnoreCase(scope)) {
                if (initiator == null) {
                    Component msg = Component.literal("[Server] " + resolved);
                    server.getPlayerList().broadcastSystemMessage(msg, false);
                    ctx.log("[say] (no initiator) " + resolved);
                } else {
                    initiator.sendSystemMessage(Component.literal(resolved));
                }
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

    /** FIX A: \t → 4 пробела. Работает и после resolveText. */
    private static String sanitizeTabs(String s) {
        if (s == null) return "";
        return s.replace("\t", "    ");
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

    @Override public String describe() {
        String t = text;
        if (t != null && t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return "say \"" + t + "\"";
    }
}