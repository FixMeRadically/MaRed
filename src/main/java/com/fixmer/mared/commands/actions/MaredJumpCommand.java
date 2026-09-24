package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredActionRegistry;

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