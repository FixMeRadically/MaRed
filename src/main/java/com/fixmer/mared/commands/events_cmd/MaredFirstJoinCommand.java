package com.fixmer.mared.commands.events_cmd;

import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.events.MaredEventRegistry;

/**
 * first_join { ... } — выполнить один раз при первом входе игрока.
 */
public class MaredFirstJoinCommand extends MaredScriptCommand {

    private final List<MaredScriptCommand> body;

    public MaredFirstJoinCommand(List<MaredScriptCommand> body) {
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        MaredEventRegistry.register("first_join", body, ctx, true, ctx.isPersistent());

        // F9: только в verbose
        if (MaredSettings.isVerboseScriptLog()) {
            ctx.log("[mared] first_join (" + body.size() + " команд)");
        }
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "first_join"; }
}