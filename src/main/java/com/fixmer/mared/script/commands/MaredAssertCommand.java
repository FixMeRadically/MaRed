package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * assert <условие> ["сообщение"] — проверка. Если условие ложно —
 * вывести сообщение и остановить скрипт.
 */
public class MaredAssertCommand extends MaredScriptCommand {

    private final String condition;
    private final String message;

    public MaredAssertCommand(String condition, String message) {
        this.condition = condition;
        this.message = message;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        boolean ok = MaredIfCommand.evaluateStatic(condition, ctx);
        if (!ok) {
            String m = message != null ? ctx.substitute(message) : "(нет сообщения)";
            ctx.log("[assert fail] " + condition + " — " + m);
            exec.stopAll();
        } else {
            ctx.log("[assert ok] " + condition);
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "assert " + condition; }
}