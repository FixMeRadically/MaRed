package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredActionRegistry;

/**
 * use — использовать предмет в главной руке (ПКМ).
 */
public class MaredUseCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueUse(ctx.executionScope());
        ctx.log("[action] use");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "use"; }
}