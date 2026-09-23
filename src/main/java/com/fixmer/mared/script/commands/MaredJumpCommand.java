package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * jump — прыжок (действие).
 */
public class MaredJumpCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueJump();
        ctx.log("[action] jump");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "jump"; }
}