package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * return <выражение> — вернуть значение из функции.
 *
 * Устанавливает returnValue и прерывает все кадры до ближайшего functionCall включительно.
 * Если return вне функции — просто останавливает выполнение (как return из скрипта).
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
            // return вне функции — просто останавливаем весь скрипт
            ctx.log("[mared] return (вне функции)");
            exec.stopAll();
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "return" + (expression != null && !expression.isEmpty() ? " " + expression : "");
    }
}