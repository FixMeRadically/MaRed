package com.fixmer.mared.commands.events_cmd;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.events.MaredEventRegistry;

/**
 * off <event> — снять все слушатели события.
 * off every    — снять все every-таймеры.
 * off after    — снять все after-таймеры.
 * off all      — снять всё.
 *
 * F7 FIX: корректная обработка every/after.
 */
public class MaredOffCommand extends MaredScriptCommand {

    private final String target;

    public MaredOffCommand(String target) {
        this.target = target;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        if (ctx.executionScope() != null) {
            MaredEventRegistry.removeOwned(ctx.executionScope(), target);
            ctx.log("[mared] off " + target + " — listeners owned by this run removed");
            return true;
        }
        if ("all".equalsIgnoreCase(target) || "*".equals(target)) {
            MaredEventRegistry.removeAllEvents();
            ctx.log("[mared] off all — все слушатели сняты");
        } else if ("every".equalsIgnoreCase(target)) {
            MaredEventRegistry.removeAllEvery();
            ctx.log("[mared] off every — все every-таймеры сняты");
        } else if ("after".equalsIgnoreCase(target)) {
            MaredEventRegistry.removeAllAfter();
            ctx.log("[mared] off after — все after-таймеры сняты");
        } else {
            MaredEventRegistry.removeAll(target);
            ctx.log("[mared] off " + target + " — слушатели сняты");
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "off " + target; }
}