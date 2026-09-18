package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * exit — остановить текущий скрипт.
 * Не бросает исключение, просто exec.stopAll().
 * Полезно вместо длинных if-else.
 */
public class MaredExitCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        ctx.log(MaredLang.get("mared.log.mared.exit"));
        exec.stopAll();
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "exit"; }
}