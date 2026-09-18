package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * return <выражение> — вернуть значение из функции.
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
                value = MaredExpr.eval(expression, ctx);
            } catch (Exception e) {
                ctx.log(MaredLang.format("mared.log.eval.error", expression, e.getMessage()));
                value = null;
            }
        }
        exec.setReturnValue(value);

        Frame fn = exec.findEnclosingFunction();
        if (fn != null) {
            exec.abortToFunction(fn);
            ctx.log(MaredLang.get("mared.log.mared.return"));
        } else {
            ctx.log(MaredLang.get("mared.log.mared.return_outside"));
            exec.stopAll();
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "return" + (expression != null && !expression.isEmpty() ? " " + expression : "");
    }
}