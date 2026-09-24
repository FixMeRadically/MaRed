package com.fixmer.mared.commands.actions;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.expr.MaredExpr;
import com.fixmer.mared.commands.input.MaredActionRegistry;

/**
 * select_slot <n> — выбрать слот хотбара (0-8).
 */
public class MaredSelectSlotCommand extends MaredScriptCommand {

    private final String slotExpr;

    public MaredSelectSlotCommand(String slotExpr) {
        this.slotExpr = slotExpr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        int slot = (int) MaredExpr.toLong(MaredExpr.eval(slotExpr, ctx));
        MaredActionRegistry.selectSlot(slot);
        ctx.log("[action] select_slot " + slot);
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "select_slot " + slotExpr; }
}