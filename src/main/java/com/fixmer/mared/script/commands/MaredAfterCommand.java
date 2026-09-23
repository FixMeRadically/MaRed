package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * after N ticks { ... } — выполнить тело один раз через N тиков.
 */
public class MaredAfterCommand extends MaredScriptCommand {

    private final int delay;
    private final List<MaredScriptCommand> body;

    public MaredAfterCommand(int delay, List<MaredScriptCommand> body) {
        this.delay = Math.max(1, delay);
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        MaredEventRegistry.registerAfter(delay, body, ctx);

        // F9: только в verbose
        if (MaredSettings.isVerboseScriptLog()) {
            ctx.log("[mared] after " + delay + " ticks (" + body.size() + " команд)");
        }
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "after " + delay + " ticks"; }
}