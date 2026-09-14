package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * break — прерывает ближайший цикл.
 *
 * Находит первый кадр-цикл (loopBody=true) в стеке executor'а.
 * Устанавливает breakRequested=true в этом кадре.
 * Прерывает все кадры выше него (pop) и сам цикл (index = size).
 * Тогда onBodyFinished увидит флаг и завершит цикл.
 */
public class MaredBreakCommand extends MaredScriptCommand {

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Frame loop = exec.findEnclosingLoop();
        if (loop == null) {
            ctx.log("[warn] break вне цикла");
            return;
        }

        exec.abortFramesUpTo(loop);

        loop.breakRequested = true;
        ctx.log("[mared] break");
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "break"; }
}