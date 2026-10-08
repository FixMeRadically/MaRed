package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredActionRegistry;

/**
 * attack — атака (действие). Бьёт по сущности, на которую смотрит игрок.
 */
public class MaredAttackCommand extends MaredScriptCommand {

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredActionRegistry.queueAttack(ctx.executionScope());
        ctx.log("[action] attack");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "attack"; }
}