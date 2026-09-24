package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredActionRegistry;

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