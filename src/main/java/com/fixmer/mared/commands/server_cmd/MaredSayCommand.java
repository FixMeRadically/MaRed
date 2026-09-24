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
 * say <текст> [scope=all|self]
 *
 * В мультиплеере (server == null) — выводим ЛОКАЛЬНО через LocalPlayer.
 * В singleplayer — через серверный broadcast / sendSystemMessage.
 */
public class MaredSayCommand extends MaredScriptCommand {

    private final String text;
    private final String scope;

    public MaredSayCommand(String text, String scope) {
        this.text = text;
        this.scope = scope;
    }

    public String getText()  { return text; }
    public String getScope() { return scope; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String resolved = resolveText(text, ctx).replace("\t", "    ");
        if (resolved.isEmpty()) return true;

        ServerPlayer initiator = ctx.getInitiator();
        MinecraftServer server = ctx.getServer();
        if (server == null && initiator != null) server = initiator.getServer();

        boolean selfOnly = "self".equalsIgnoreCase(scope);
        boolean logChat = MaredSettings.isLogChatToEditor();

        String[] lines = resolved.split("\n", -1);

        try {
            if (server == null) {
                sayLocally(ctx, lines, logChat);
            } else {
                sayServer(ctx, server, initiator, lines, selfOnly, logChat);
            }
            ctx.log((selfOnly ? "[say] (self) " : "[say] ") + resolved);
            MaredEventRegistry.suppressChat(500);
        } catch (Exception e) {
            ctx.log("[error] say: " + e.getMessage());
        }
        return true;
    }

    private void sayLocally(MaredScriptContext ctx, String[] lines, boolean logChat) {
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) return;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            lp.displayClientMessage(Component.literal(line), false);
            if (logChat) ctx.log("[chat] " + line);
        }
    }

    private void sayServer(MaredScriptContext ctx, MinecraftServer server, ServerPlayer initiator,
                           String[] lines, boolean selfOnly, boolean logChat) {
        if (selfOnly) {
            if (initiator == null) return;
            for (String line : lines) {
                if (line.isEmpty()) continue;
                initiator.sendSystemMessage(Component.literal(line));
                if (logChat) ctx.log("[chat] " + line);
            }
            return;
        }
        for (String line : lines) {
            if (line.isEmpty()) continue;
            server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
            if (logChat) ctx.log("[chat] " + line);
        }
    }

    private String resolveText(String raw, MaredScriptContext ctx) {
        if (raw == null || raw.isEmpty()) return "";

        if (looksLikeConcat(raw)) {
            try { return MaredExpr.stringify(MaredExpr.eval(raw, ctx)); }
            catch (Exception ignored) {}
        }
        String stripped = stripOuterQuotes(raw);
        stripped = MaredDebugCommand.unescapeQuotes(stripped);
        return ctx.substitute(stripped);
    }

    private static String stripOuterQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
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
        String t = text;
        if (t != null && t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            t = t.substring(1, t.length() - 1);
        }
        return "say \"" + t + "\"";
    }
}