package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * on <event> [{ ... }]          — replace (по умолчанию)
 * on <event> add [{ ... }]      — add
 * on <event> replace [{ ... }]  — replace (явно)
 */
public class MaredOnCommand extends MaredScriptCommand {

    private final String eventType;
    private final List<MaredScriptCommand> body;
    private final boolean replace;

    public MaredOnCommand(String eventType, List<MaredScriptCommand> body, boolean replace) {
        this.eventType = eventType;
        this.body = body;
        this.replace = replace;
    }

    public String getEventType() { return eventType; }
    public List<MaredScriptCommand> getBody() { return body; }
    public boolean isReplace() { return replace; }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        MaredEventRegistry.register(eventType, body, ctx, replace);
        String mode = replace ? "replace" : "add";
        ctx.log("[mared] on " + eventType + " " + mode + " (" + body.size() + " команд)");
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "on " + eventType + (replace ? "" : " add");
    }
}