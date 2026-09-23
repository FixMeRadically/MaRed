package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredActionRegistry;
import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

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