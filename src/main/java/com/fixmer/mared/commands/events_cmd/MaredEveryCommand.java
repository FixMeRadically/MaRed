package com.fixmer.mared.commands.events_cmd;

import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.events.MaredEventRegistry;

/**
 * every N ticks { ... } — выполнять тело каждые N тиков.
 */
public class MaredEveryCommand extends MaredScriptCommand {

    private final int period;
    private final List<MaredScriptCommand> body;

    public MaredEveryCommand(int period, List<MaredScriptCommand> body) {
        this.period = Math.max(1, period);
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        MaredEventRegistry.registerEvery(period, body, ctx, ctx.isPersistent());

        // F9: только в verbose
        if (MaredSettings.isVerboseScriptLog()) {
            ctx.log("[mared] every " + period + " ticks (" + body.size() + " команд)");
        }
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "every " + period + " ticks"; }
}