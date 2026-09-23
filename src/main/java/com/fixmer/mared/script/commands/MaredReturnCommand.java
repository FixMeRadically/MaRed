package com.fixmer.mared.script.commands;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * return <выражение> — вернуть значение из функции.
 *
 * F5 FIX: для строковых литералов применяется substitute.
 * F8 FIX: лог только в verbose-режиме.
 */
public class MaredReturnCommand extends MaredScriptCommand {

    private final String expression;

    public MaredReturnCommand(String expression) {
        this.expression = expression;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Object value = null;
        if (expression != null && !expression.isEmpty()) {
            try {
                String trimmed = expression.trim();
                // F5 FIX: строковый литерал — substitute
                if (trimmed.length() >= 2
                    && trimmed.startsWith("\"")
                    && trimmed.endsWith("\"")) {
                    String inner = trimmed.substring(1, trimmed.length() - 1);
                    inner = MaredDebugCommand.unescapeQuotes(inner);
                    value = ctx.substitute(inner);
                } else {
                    value = MaredExpr.eval(expression, ctx);
                }
            } catch (Exception e) {
                ctx.log(MaredLang.format("mared.log.eval.error", expression, e.getMessage()));
                value = null;
            }
        }
        exec.setReturnValue(value);

        Frame fn = exec.findEnclosingFunction();
        if (fn != null) {
            exec.abortToFunction(fn);
            // F8 FIX: только в verbose
            if (MaredSettings.isVerboseScriptLog()) {
                ctx.log(MaredLang.get("mared.log.mared.return"));
            }
        } else {
            // F8 FIX: предупреждение всегда (важно)
            ctx.log(MaredLang.get("mared.log.mared.return_outside"));
            exec.stopAll();
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "return" + (expression != null && !expression.isEmpty() ? " " + expression : "");
    }
}