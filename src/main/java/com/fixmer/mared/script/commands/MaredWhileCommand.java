package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;
import com.fixmer.mared.script.MaredScriptExecutor.LoopOwner;

/**
 * while <условие> { ... }
 * Повторяет тело, пока условие истинно.
 * Лимит итераций — 100000, чтобы не зависнуть.
 */
public class MaredWhileCommand extends MaredScriptCommand {

    private static final int MAX_ITER = 100_000;

    private final String condition;
    private final List<MaredScriptCommand> body;

    public MaredWhileCommand(String condition, List<MaredScriptCommand> body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        if (body.isEmpty()) return;

        // Проверить условие первый раз
        if (!MaredIfCommand.evaluateStatic(condition, ctx)) {
            return;
        }

        LoopOwner owner = new LoopOwner() {
            int iteration = 0;

            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor ex, Frame bodyFrame) {
                if (bodyFrame.breakRequested) {
                    bodyFrame.breakRequested = false;
                    c.log("[mared] break — выход из while");
                    return false;
                }
                if (bodyFrame.continueRequested) {
                    bodyFrame.continueRequested = false;
                }
                iteration++;
                if (iteration >= MAX_ITER) {
                    c.log("[warn] while: превышен лимит " + MAX_ITER + " итераций");
                    return false;
                }
                // Проверяем условие заново
                if (!MaredIfCommand.evaluateStatic(condition, c)) return false;

                ex.pushLoopBody(body, this);
                return true;
            }

            @Override
            public String describe() { return "while " + condition; }
        };

        exec.pushLoopBody(body, owner);
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "while " + condition; }
}