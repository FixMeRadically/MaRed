package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * continue — переходит к следующей итерации цикла.
 * Работает внутри тела repeat/while/for.
 */
public class MaredContinueCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Frame top = exec.peekTopFrame();
        if (top != null) {
            top.continueRequested = true;
            // Пропускаем оставшиеся команды текущего кадра
            top.index = top.commands.size();
            ctx.log("[mared] continue");
        } else {
            ctx.log("[warn] continue вне цикла");
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "continue"; }
}