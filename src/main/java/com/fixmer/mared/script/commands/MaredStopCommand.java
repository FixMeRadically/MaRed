package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * stop — сбросить все действия игрока (зажатые клавиши, поворот).
 */
public class MaredStopCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.stopAll();
        ctx.log("[action] stop — все действия сброшены");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "stop"; }
}