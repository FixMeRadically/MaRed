package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;
import com.fixmer.mared.script.MaredScriptExecutor.LoopOwner;

/**
 * repeat N { ... }
 * Толкает тело кадром N раз. Если внутри срабатывает break — цикл прерывается.
 */
public class MaredRepeatCommand extends MaredScriptCommand {

    private final int count;
    private final List<MaredScriptCommand> body;

    public MaredRepeatCommand(int count, List<MaredScriptCommand> body) {
        this.count = count;
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        if (count <= 0 || body.isEmpty()) return;

        // Кадр-родитель: мы хотим, чтобы тело выполнялось в отдельном кадре
        // и родитель следил за его завершением.
        LoopOwner owner = new LoopOwner() {
            int iteration = 0;

            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor ex, Frame bodyFrame) {
                // break — прекращаем
                if (bodyFrame.breakRequested) {
                    bodyFrame.breakRequested = false;
                    c.log("[mared] break — выход из repeat");
                    return false;
                }
                // continue — считаем итерацию и продолжаем, если ещё есть
                if (bodyFrame.continueRequested) {
                    bodyFrame.continueRequested = false;
                }
                iteration++;
                if (iteration >= count) return false;

                // запустить следующую итерацию
                ex.pushLoopBody(body, this);
                return true;
            }

            @Override
            public String describe() { return "repeat " + count; }
        };

        // Первая итерация
        exec.pushLoopBody(body, owner);
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "repeat " + count; }
}