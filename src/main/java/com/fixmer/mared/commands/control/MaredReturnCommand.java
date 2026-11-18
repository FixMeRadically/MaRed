package com.fixmer.mared.commands.control;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.genesis.technology.runtime.FrameExecutor.Frame;
import com.fixmer.mared.commands.expr.MaredExpr;
import com.fixmer.mared.commands.server_cmd.MaredDebugCommand;
import com.fixmer.mared.MaredLang;

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
                value=MaredExpr.eval(trimmed,ctx);
                if(MaredExpr.isStringLiteral(trimmed))value=ctx.substitute((String)value);
            } catch (Exception e) {
                ctx.log(MaredLang.format("mared.log.eval.error", expression, e.getMessage()));
                throw new IllegalStateException("Return expression failed: " + expression, e);
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