package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * drop — выбросить один предмет из главной руки.
 */
public class MaredDropCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueDrop();
        ctx.log("[action] drop");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "drop"; }
}