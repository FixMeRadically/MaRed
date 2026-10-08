package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredActionRegistry;

/**
 * stop — сбросить все действия игрока (зажатые клавиши, поворот).
 */
public class MaredStopCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.stop(ctx.executionScope());
        ctx.log("[action] stop — все действия сброшены");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "stop"; }
}