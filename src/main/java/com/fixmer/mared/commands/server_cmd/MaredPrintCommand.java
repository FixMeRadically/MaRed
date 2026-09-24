package com.fixmer.mared.commands.server_cmd;

import com.fixmer.mared.MaredSettings;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.expr.MaredExpr;

/**
 * print <текст> [scope=self|all]
 *
 * Без scope — только в лог редактора (как раньше).
 * scope=self — в чат только инициатору.
 * scope=all  — в чат всем.
 *
 * FIX 0.2.5: при MaredSettings.isLogChatToEditor() == true
 * каждая строка, уходящая в чат, дублируется в лог редактора
 * с префиксом [chat].
 */
public class MaredPrintCommand extends MaredScriptCommand {

    private final String text;
    private final String scope;

    public MaredPrintCommand(String text) {
        this(text, "");
    }

    public MaredPrintCommand(String text, String scope) {
        this.text = text;
        this.scope = scope == null ? "" : scope;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        raw = MaredDebugCommand.unescapeQuotes(raw);

        if (looksLikeConcat(raw)) {
            try {
                Object v = MaredExpr.eval(raw, ctx);
                raw = MaredExpr.stringify(v);
            } catch (Exception ignored) {}
        } else {
            raw = ctx.substitute(raw);
        }
        raw = raw.replace("\t", "    ");

        boolean chatMode = "self".equalsIgnoreCase(scope) || "all".equalsIgnoreCase(scope);
        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();
        if (server == null && initiator != null) server = initiator.getServer();
        boolean logChat = MaredSettings.isLogChatToEditor();

        for (String line : raw.split("\n", -1)) {
            ctx.log("[print] " + line);

            if (!chatMode) continue;
            if (line.isEmpty()) continue;

            try {
                if ("self".equalsIgnoreCase(scope)) {
                    if (initiator != null) {
                        initiator.sendSystemMessage(Component.literal(line));
                        if (logChat) ctx.log("[chat] " + line);
                    }
                } else {
                    if (server != null) {
                        server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
                        if (logChat) ctx.log("[chat] " + line);
                    }
                }
            } catch (Exception e) {
                ctx.log("[error] print: " + e.getMessage());
            }
        }

        if (chatMode) MaredEventRegistry.suppressChat(500);
        return true;
    }

    private static boolean looksLikeConcat(String s) {
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
        String t = text == null ? "" : text;
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return "print \"" + t + "\"" + (scope.isEmpty() ? "" : " scope=" + scope);
    }
}