package com.fixmer.mared.commands.control;

import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.engine.MaredScriptExecutor.Frame;
import com.fixmer.mared.commands.engine.MaredScriptExecutor.LoopOwner;

/**
 * while <условие> { ... }
 *
 * FIX: MAX_ITER увеличен до 1_000_000 (защита через LOOP_BATCH_SIZE в executor'е).
 */
public class MaredWhileCommand extends MaredScriptCommand {

    private static final int MAX_ITER = 1_000_000;

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
                    if (MaredSettings.isVerboseScriptLog()) {
                        c.log("[mared] break — выход из while");
                    }
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