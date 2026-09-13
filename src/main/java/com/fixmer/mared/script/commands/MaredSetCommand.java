package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

public class MaredSetCommand extends MaredScriptCommand {

    private final String name;
    private final String valueExpr;

    public MaredSetCommand(String name, String valueExpr) {
        this.name = name;
        this.valueExpr = valueExpr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        try {
            Object v = MaredExpr.eval(valueExpr, ctx);
            ctx.setVariable(name, v);
        } catch (Exception e) {
            // fallback: старое поведение — подстановка + запись строки
            ctx.setVariable(name, ctx.substitute(valueExpr));
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "set " + name + " = " + valueExpr; }
}