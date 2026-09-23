package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * exit — остановить текущий скрипт.
 *
 * FIX 0.2.5+: если exit вызван внутри on-тела (есть активный Entry),
 * он снимает именно этот слушатель из MaredEventRegistry.
 * Так `exit` внутри `on tick_client` реально останавливает повторные срабатывания.
 */
public class MaredExitCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        // Проверяем, есть ли текущий Entry (значит, мы внутри on-тела)
        MaredEventRegistry.Entry entry = MaredEventRegistry.currentEntry();
        if (entry != null) {
            boolean removed = MaredEventRegistry.removeEntry(entry);
            if (removed) {
                ctx.log("[mared] exit — слушатель " + entry.type + " снят");
            } else {
                ctx.log(MaredLang.get("mared.log.mared.exit"));
            }
        } else {
            ctx.log(MaredLang.get("mared.log.mared.exit"));
        }
        exec.stopAll();
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "exit"; }
}