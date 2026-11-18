package com.fixmer.mared.commands.data;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.expr.MaredExpr;

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
            if(valueExpr.matches("[\\p{L}\\p{N}_ -]+"))ctx.setVariable(name,ctx.substitute(valueExpr));
            else throw new IllegalArgumentException("set expression failed: "+valueExpr,e);
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "set " + name + " = " + valueExpr; }
}