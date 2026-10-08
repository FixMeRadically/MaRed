package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredActionRegistry;

/**
 * swap_hands — поменять предметы в руках.
 */
public class MaredSwapCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueSwap(ctx.executionScope());
        ctx.log("[action] swap_hands");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "swap_hands"; }
}