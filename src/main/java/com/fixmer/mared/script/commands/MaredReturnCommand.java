package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * return <выражение> — вернуть значение из функции.
 *
 * FIX 1: теперь поддерживает `return call func(args)` и любые
 * арифметические выражения с вызовами функций, потому что
 * MaredExpr.eval распознаёт `call` как встроенную конструкцию
 * и синхронно вызывает функцию через MaredScriptContext.callFunction.
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
                ctx.log("[error] return: " + e.getMessage());
                value = null;
            }
        }
        exec.setReturnValue(value);

        Frame fn = exec.findEnclosingFunction();
        if (fn != null) {
            exec.abortToFunction(fn);
            ctx.log("[mared] return");
        } else {
            ctx.log("[mared] return (вне функции)");
            exec.stopAll();
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "return" + (expression != null && !expression.isEmpty() ? " " + expression : "");
    }
}