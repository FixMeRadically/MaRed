package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * say "текст" [scope=all|self|player:Name]
 *
 * Текст проходит через ctx.substitute():
 *   $name          → подстановка переменной
 *   ${выражение}   → вычисление выражения
 *
 * Если текст был в кавычках — это литерал, кавычки срезаются, содержимое
 * проходит substitute. Если без кавычек — выражение, вычисляется целиком.
 */
public class MaredSayCommand extends MaredScriptCommand {

    private final String rawText;
    private final boolean wasQuoted;
    private final String scope;

    public MaredSayCommand(String rawText, String scope) {
        this.rawText = rawText;
        this.wasQuoted = rawText.length() >= 2
            && rawText.startsWith("\"") && rawText.endsWith("\"");
        this.scope = scope == null ? "all" : scope;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String resolved = resolveText(ctx);
        if (ctx.getServer() == null) return false;

        if ("self".equals(scope)) {
            ServerPlayer p = ctx.getInitiator();
            if (p != null) p.sendSystemMessage(Component.literal(resolved));
            return true;
        }
        if (scope.startsWith("player:")) {
            String name = scope.substring("player:".length());
            ServerPlayer target = ctx.getServer().getPlayerList().getPlayerByName(name);
            if (target != null) target.sendSystemMessage(Component.literal(resolved));
            return true;
        }
        ctx.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal(resolved), false);
        return true;
    }

    /** Разобрать текст: шаблонная подстановка или вычисление. */
    private String resolveText(MaredScriptContext ctx) {
        if (wasQuoted) {
            // Литерал в кавычках — убираем ВНЕШНИЕ кавычки, потом подставляем $name и ${...}
            String inner = rawText;
            if (inner.length() >= 2 && inner.startsWith("\"") && inner.endsWith("\"")) {
                inner = inner.substring(1, inner.length() - 1);
            }
            return ctx.substitute(inner);
        }
        // Без кавычек — пробуем вычислить как выражение
        try {
            Object v = MaredExpr.eval(rawText, ctx);
            return MaredExpr.stringify(v);
        } catch (Exception e) {
            return ctx.substitute(rawText);
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "say " + rawText; }
}