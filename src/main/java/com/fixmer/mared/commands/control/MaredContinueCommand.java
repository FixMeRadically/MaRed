package com.fixmer.mared.commands.control;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.genesis.technology.runtime.FrameExecutor.Frame;

public class MaredContinueCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Frame loop = exec.findEnclosingLoop();
        if (loop == null) {
            ctx.log("[warn] continue вне цикла");
            return;
        }

        exec.abortFramesUpTo(loop);
        loop.continueRequested = true;

        if (MaredSettings.isVerboseScriptLog()) {
            ctx.log("[mared] continue");
        }
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "continue"; }
}