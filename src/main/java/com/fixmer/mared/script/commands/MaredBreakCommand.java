package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * break — прерывает текущий цикл.
 * Работает, если находится внутри тела repeat/while/for.
 */
public class MaredBreakCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Frame top = exec.peekTopFrame();
        if (top != null) {
            top.breakRequested = true;
            ctx.log("[mared] break");
        } else {
            ctx.log("[warn] break вне цикла");
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "break"; }
}