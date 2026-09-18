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
 * FIX 0.2.4:
 *   - убраны внешние кавычки в выводе
 *   - \t → 4 пробела
 *   - \n → отдельные строки чата
 *   - scope=self без initiator — молчим
 *   - FIX F: разэкранирование кавычек перед substitute/eval
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

        String[] lines = resolved.split("\n", -1);
        boolean selfOnly = "self".equalsIgnoreCase(scope);

        try {
            for (String line : lines) {
                if (line.isEmpty()) continue;

                if (selfOnly) {
                    if (initiator == null) continue;
                    initiator.sendSystemMessage(Component.literal(line));
                } else {
                    Component msg = Component.literal(line);
                    server.getPlayerList().broadcastSystemMessage(msg, false);
                }
            }

            if (selfOnly) {
                if (initiator != null) {
                    ctx.log("[say] (self) " + resolved);
                    MaredEventRegistry.suppressChat(500);
                }
            } else {
                ctx.log("[say] " + resolved);
                MaredEventRegistry.suppressChat(500);
            }
        } catch (Exception e) {
            ctx.log("[error] say: " + e.getMessage());
        }
        return true;
    }

    private static String stripOuterQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String sanitizeTabs(String s) {
        if (s == null) return "";
        return s.replace("\t", "    ");
    }

    private String resolveText(String raw, MaredScriptContext ctx) {
        if (raw == null || raw.isEmpty()) return "";

        // 1. Конкатенация ("a" + $b) → вычислить как выражение
        if (looksLikeConcat(raw)) {
            try {
                Object v = MaredExpr.eval(raw, ctx);
                return MaredExpr.stringify(v);
            } catch (Exception ignored) {}
        }

        // 2. Обычная строка — снять кавычки, разэкранировать, подставить
        String stripped = stripOuterQuotes(raw);
        stripped = MaredDebugCommand.unescapeQuotes(stripped);
        return ctx.substitute(stripped);
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