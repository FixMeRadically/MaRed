package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.expr.MaredExpr;
import com.fixmer.mared.commands.input.MaredActionRegistry;

/**
 * look_at <x> <y> <z> — повернуть камеру на точку.
 */
public class MaredLookAtCommand extends MaredScriptCommand {

    private final String xExpr, yExpr, zExpr;

    public MaredLookAtCommand(String xExpr, String yExpr, String zExpr) {
        this.xExpr = xExpr; this.yExpr = yExpr; this.zExpr = zExpr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        double x = MaredExpr.toNumber(MaredExpr.eval(xExpr, ctx));
        double y = MaredExpr.toNumber(MaredExpr.eval(yExpr, ctx));
        double z = MaredExpr.toNumber(MaredExpr.eval(zExpr, ctx));
        MaredActionRegistry.lookAt(x, y, z);
        ctx.log("[action] look_at " + x + " " + y + " " + z);
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "look_at " + xExpr + " " + yExpr + " " + zExpr; }
}