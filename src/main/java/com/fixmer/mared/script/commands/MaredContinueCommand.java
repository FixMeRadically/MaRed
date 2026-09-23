package com.fixmer.mared.script.commands;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

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