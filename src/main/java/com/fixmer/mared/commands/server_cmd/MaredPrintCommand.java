package com.fixmer.mared.commands.server_cmd;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.expr.MaredExpr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * print <текст> [scope=self|all]
 *
 * Без scope — только в лог редактора.
 * scope=self — в чат инициатору.
 * scope=all  — в чат всем.
 */
public class MaredPrintCommand extends MaredScriptCommand {

    private final String text;
    private final String scope;

    public MaredPrintCommand(String text) { this(text, ""); }

    public MaredPrintCommand(String text, String scope) {
        this.text = text;
        this.scope = scope == null ? "" : scope;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String raw = text == null ? "" : text.trim();
        int n = raw.length();
        if (n >= 2 && raw.charAt(0) == '"' && raw.charAt(n - 1) == '"') {
            raw = raw.substring(1, n - 1);
        }
        raw = MaredDebugCommand.unescapeQuotes(raw);

        if (looksLikeConcat(raw)) {
            try { raw = MaredExpr.stringify(MaredExpr.eval(raw, ctx)); }
            catch (Exception ignored) {}
        } else {
            raw = ctx.substitute(raw);
        }
        raw = raw.replace("\t", "    ");

        boolean chatMode = "self".equalsIgnoreCase(scope) || "all".equalsIgnoreCase(scope);
        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();
        if (server == null && initiator != null) server = initiator.getServer();
        boolean logChat = MaredSettings.isLogChatToEditor();

        String[] lines = raw.split("\n", -1);
        for (String line : lines) {
            ctx.log("[print] " + line);
            if (!chatMode || line.isEmpty()) continue;

            try {
                if (server == null) {
                    LocalPlayer lp = Minecraft.getInstance().player;
                    if (lp == null) continue;
                    lp.displayClientMessage(Component.literal(line), false);
                    if (logChat) ctx.log("[chat] " + line);
                } else if ("self".equalsIgnoreCase(scope)) {
                    if (initiator != null) {
                        initiator.sendSystemMessage(Component.literal(line));
                        if (logChat) ctx.log("[chat] " + line);
                    }
                } else {
                    server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
                    if (logChat) ctx.log("[chat] " + line);
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
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (inString && c == '\\' && i + 1 < n) { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString && c == '+') return true;
        }
        return false;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override
    public String describe() {
        String t = text == null ? "" : text;
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            t = t.substring(1, t.length() - 1);
        }
        return "print \"" + t + "\"" + (scope.isEmpty() ? "" : " scope=" + scope);
    }
}