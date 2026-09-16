package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * on <event> [{ ... }]          — replace (по умолчанию)
 * on <event> add [{ ... }]      — add
 * on <event> replace [{ ... }]  — replace (явно)
 *
 * FIX 3: передаёт ctx.isPersistent() в реестр, чтобы событие
 * переживало выход из мира и не требовало перезапуска игры.
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
        // ← FIX 3: пробрасываем persistent-флаг из контекста
        boolean persistent = ctx.isPersistent();
        MaredEventRegistry.register(eventType, body, ctx, replace, persistent);
        String mode = replace ? "replace" : "add";
        String pTag = persistent ? " [persistent]" : "";
        ctx.log("[mared] on " + eventType + " " + mode + pTag + " (" + body.size() + " команд)");
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "on " + eventType + (replace ? "" : " add");
    }
}