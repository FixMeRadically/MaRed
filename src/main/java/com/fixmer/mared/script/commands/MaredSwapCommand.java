package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * swap_hands — поменять предметы в руках.
 */
public class MaredSwapCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueSwap();
        ctx.log("[action] swap_hands");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "swap_hands"; }
}