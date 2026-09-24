package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.expr.MaredExpr;
import com.fixmer.mared.commands.input.MaredActionRegistry;

/**
 * look <yaw> <pitch> — установить углы напрямую.
 */
public class MaredLookCommand extends MaredScriptCommand {

    private final String yawExpr, pitchExpr;

    public MaredLookCommand(String yawExpr, String pitchExpr) {
        this.yawExpr = yawExpr; this.pitchExpr = pitchExpr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        float yaw = (float) MaredExpr.toNumber(MaredExpr.eval(yawExpr, ctx));
        float pitch = (float) MaredExpr.toNumber(MaredExpr.eval(pitchExpr, ctx));
        MaredActionRegistry.setLook(yaw, pitch);
        ctx.log("[action] look " + yaw + " " + pitch);
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "look " + yawExpr + " " + pitchExpr; }
}