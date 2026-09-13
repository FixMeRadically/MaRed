package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

/**
 * debug $x  — вывести значение переменной или выражения в лог редактора.
 */
public class MaredDebugCommand extends MaredScriptCommand {

    private final String expr;

    public MaredDebugCommand(String expr) {
        this.expr = expr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String resolved = ctx.substitute(expr);
        ctx.log("[debug] " + expr + " = " + resolved);
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "debug " + expr; }
}