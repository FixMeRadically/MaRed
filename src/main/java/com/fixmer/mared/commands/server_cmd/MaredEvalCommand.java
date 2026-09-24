package com.fixmer.mared.commands.server_cmd;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.expr.MaredExpr;

/**
 * "Тихая" команда: вычисляет выражение и отбрасывает результат.
 * Используется для вызовов методов с побочным эффектом:
 *   $items.push(4)
 *   $items.sort()
 *   $name.upper()      ← бесполезно, но допустимо
 */
public class MaredEvalCommand extends MaredScriptCommand {

    private final String expr;

    public MaredEvalCommand(String expr) {
        this.expr = expr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        try {
            MaredExpr.eval(expr, ctx);
        } catch (Exception e) {
            ctx.log("[error] " + expr + " — " + e.getMessage());
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return expr; }
}