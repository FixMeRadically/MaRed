package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;
import com.fixmer.mared.script.MaredScriptExecutor.LoopOwner;

public class MaredForCommand extends MaredScriptCommand {

    private static final int MAX_ITER = 100_000;

    private final String var;
    private final String fromExpr;
    private final String toExpr;
    private final List<MaredScriptCommand> body;

    public MaredForCommand(String var, String fromExpr, String toExpr,
                           List<MaredScriptCommand> body) {
        this.var = var;
        this.fromExpr = fromExpr;
        this.toExpr = toExpr;
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        int from, to;
        try {
            from = (int) MaredExpr.toLong(MaredExpr.eval(fromExpr, ctx));
            to   = (int) MaredExpr.toLong(MaredExpr.eval(toExpr, ctx));
        } catch (Exception e) {
            ctx.log(MaredLang.format("mared.log.eval.error",
                fromExpr + " to " + toExpr, e.getMessage()));
            return;
        }

        final int startValue = from;
        final int endValue   = to;

        // FIX C: сохраняем старое значение переменной цикла
        final boolean hadVar = ctx.hasVariable(var);
        final Object oldVar = ctx.getVariable(var);

        LoopOwner owner = new LoopOwner() {
            int current = startValue;
            int iteration = 0;
            boolean forward = endValue >= startValue;

            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor ex, Frame bodyFrame) {
                if (bodyFrame.breakRequested) {
                    bodyFrame.breakRequested = false;
                    c.log("[mared] break — выход из for");
                    restoreVar(c);
                    return false;
                }
                if (bodyFrame.continueRequested) {
                    bodyFrame.continueRequested = false;
                }
                iteration++;
                if (iteration >= MAX_ITER) {
                    c.log(MaredLang.format("mared.log.mared.for_limit", MAX_ITER));
                    restoreVar(c);
                    return false;
                }

                current += forward ? 1 : -1;
                boolean done = forward ? current > endValue : current < endValue;
                if (done) {
                    restoreVar(c);
                    return false;
                }

                c.setVariable(var, current);
                ex.pushLoopBody(body, this);
                return true;
            }

            private void restoreVar(MaredScriptContext c) {
                if (hadVar) {
                    c.setVariable(var, oldVar);
                } else {
                    c.setVariable(var, null);
                }
            }

            @Override
            public String describe() { return "for $" + var + " = " + fromExpr + " to " + toExpr; }
        };

        ctx.setVariable(var, startValue);
        exec.pushLoopBody(body, owner);
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "for $" + var + " = " + fromExpr + " to " + toExpr; }
}