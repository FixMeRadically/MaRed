package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * continue — пропускает остаток текущей итерации цикла.
 *
 * Находит ближайший кадр-цикл (loopBody=true).
 * Устанавливает continueRequested=true в нём.
 * Прерывает все кадры выше цикла (pop) и сам цикл (index = size).
 * Тогда onBodyFinished обработает continueRequested и запустит следующую итерацию.
 */
public class MaredContinueCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Frame loop = exec.findEnclosingLoop();
        if (loop == null) {
            ctx.log("[warn] continue вне цикла");
            return;
        }

        exec.abortFramesUpTo(loop);

        loop.continueRequested = true;
        ctx.log("[mared] continue");
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "continue"; }
}