package com.fixmer.mared.commands.control;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.engine.MaredScriptExecutor.Frame;

public class MaredBreakCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Frame loop = exec.findEnclosingLoop();
        if (loop == null) {
            ctx.log("[warn] break вне цикла");
            return;
        }

        exec.abortFramesUpTo(loop);
        loop.breakRequested = true;

        if (MaredSettings.isVerboseScriptLog()) {
            ctx.log("[mared] break");
        }
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "break"; }
}