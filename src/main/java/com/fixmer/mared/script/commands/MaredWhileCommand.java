package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;
import com.fixmer.mared.script.MaredScriptExecutor.LoopOwner;

/**
 * while <условие> { ... }
 *
 * FIX: лимит снижен с 100_000 до 10_000 — защита от фриза клиента.
 * Если нужно больше итераций — добавь `wait 1 tick` в тело.
 */
public class MaredWhileCommand extends MaredScriptCommand {

    private static final int MAX_ITER = 10_000;

    private final String condition;
    private final List<MaredScriptCommand> body;

    public MaredWhileCommand(String condition, List<MaredScriptCommand> body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        if (body.isEmpty()) return;

        if (!MaredIfCommand.evaluateStatic(condition, ctx)) return;

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
                    c.log(MaredLang.format("mared.log.mared.while_limit", MAX_ITER));
                    return false;
                }
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