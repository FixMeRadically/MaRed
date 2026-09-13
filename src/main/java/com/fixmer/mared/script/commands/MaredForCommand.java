package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;
import com.fixmer.mared.script.MaredScriptExecutor.LoopOwner;

/**
 * for $i = from to to { ... }
 * Цикл с счётчиком. Инклюзивный (включая to).
 * Если from > to — считает в обратную сторону.
 */
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
        int from = parseInt(ctx.substitute(fromExpr));
        int to   = parseInt(ctx.substitute(toExpr));

        int startValue = from;
        int endValue   = to;

        LoopOwner owner = new LoopOwner() {
            int current = startValue;
            int iteration = 0;
            boolean forward = endValue >= startValue;

            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor ex, Frame bodyFrame) {
                if (bodyFrame.breakRequested) {
                    bodyFrame.breakRequested = false;
                    c.log("[mared] break — выход из for");
                    return false;
                }
                if (bodyFrame.continueRequested) {
                    bodyFrame.continueRequested = false;
                }
                iteration++;
                if (iteration >= MAX_ITER) {
                    c.log("[warn] for: превышен лимит " + MAX_ITER + " итераций");
                    return false;
                }

                current += forward ? 1 : -1;
                boolean done = forward ? current > endValue : current < endValue;
                if (done) return false;

                c.setVariable(var, current);
                ex.pushLoopBody(body, this);
                return true;
            }

            @Override
            public String describe() { return "for $" + var + " = " + fromExpr + " to " + toExpr; }
        };

        // Ставим начальное значение и запускаем
        ctx.setVariable(var, startValue);
        exec.pushLoopBody(body, owner);
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "for $" + var + " = " + fromExpr + " to " + toExpr; }
}