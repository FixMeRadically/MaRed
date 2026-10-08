package com.fixmer.mared.commands.control;

import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.genesis.technology.runtime.FrameExecutor.Frame;
import com.fixmer.mared.commands.engine.MaredScriptExecutor.LoopOwner;
import com.fixmer.mared.commands.expr.MaredExpr;

/**
 * repeat N { ... }  или  repeat $n { ... }
 */
public class MaredRepeatCommand extends MaredScriptCommand {

    private final String countExpr;
    private final List<MaredScriptCommand> body;

    public MaredRepeatCommand(String countExpr, List<MaredScriptCommand> body) {
        this.countExpr = countExpr;
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        int count = 0;
        try {
            count = (int) MaredExpr.toLong(MaredExpr.eval(countExpr, ctx));
        } catch (Exception e) {
            ctx.log(MaredLang.format("mared.log.eval.error", countExpr, e.getMessage()));
            return;
        }

        if (count <= 0 || body.isEmpty()) return;

        final int total = count;

        LoopOwner owner = new LoopOwner() {
            int iteration = 0;

            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor ex, Frame bodyFrame) {
                if (bodyFrame.breakRequested) {
                    bodyFrame.breakRequested = false;
                    // F9: только в verbose
                    if (MaredSettings.isVerboseScriptLog()) {
                        c.log("[mared] break — выход из repeat");
                    }
                    return false;
                }
                if (bodyFrame.continueRequested) {
                    bodyFrame.continueRequested = false;
                }
                iteration++;
                if (iteration >= total) return false;

                ex.pushLoopBody(body, this);
                return true;
            }

            @Override
            public String describe() { return "repeat " + total; }
        };

        exec.pushLoopBody(body, owner);
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "repeat " + countExpr; }
}