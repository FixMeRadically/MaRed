package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * attack — атака (действие). Бьёт по сущности, на которую смотрит игрок.
 */
public class MaredAttackCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueAttack();
        ctx.log("[action] attack");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "attack"; }
}