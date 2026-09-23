package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * use — использовать предмет в главной руке (ПКМ).
 */
public class MaredUseCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueUse();
        ctx.log("[action] use");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "use"; }
}