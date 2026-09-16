package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * debug $x  — вывести значение переменной или выражения в лог редактора.
 *
 * FIX 2: использует MaredExpr.eval, чтобы вычислять арифметику
 * ($x + 1, $x * 2 и т.п.), а не только подставлять переменные.
 */
public class MaredDebugCommand extends MaredScriptCommand {

    private final String expr;

    public MaredDebugCommand(String expr) {
        this.expr = expr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        Object value;
        try {
            value = MaredExpr.eval(expr, ctx);
        } catch (Exception e) {
            // fallback: подстановка переменных
            value = ctx.substitute(expr);
        }
        ctx.log("[debug] " + expr + " = " + MaredExpr.stringify(value));
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "debug " + expr; }
}