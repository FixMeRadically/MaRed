package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * wait_until <условие> — блокирует скрипт, пока условие не станет истинным.
 *
 * Проверка на каждом тике. Защита — 24000 тиков (20 минут реального времени).
 * Если условие не выполнилось за это время — выход с предупреждением.
 */
public class MaredWaitUntilCommand extends MaredScriptCommand {

    private static final int MAX_WAIT_TICKS = 24000;
    private static final int CHECK_INTERVAL = 1;

    private final String condition;
    private int elapsedTicks = 0;
    private boolean waiting = false;

    public MaredWaitUntilCommand(String condition) {
        this.condition = condition;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        // Первый вход — проверим сразу
        if (!waiting) {
            boolean ok = MaredIfCommand.evaluateStatic(condition, ctx);
            if (ok) {
                ctx.log("[mared] wait_until: условие уже истинно, идём дальше");
                return;
            }
            waiting = true;
            elapsedTicks = 0;
            ctx.log("[mared] wait_until: ждём $" + condition);
        }

        elapsedTicks++;

        // Лимит
        if (elapsedTicks >= MAX_WAIT_TICKS) {
            ctx.log("[mared] wait_until: превышен лимит " + MAX_WAIT_TICKS + " тиков — выходим");
            waiting = false;
            return;
        }

        // Проверяем каждые CHECK_INTERVAL тиков
        if (elapsedTicks % CHECK_INTERVAL == 0) {
            boolean ok = MaredIfCommand.evaluateStatic(condition, ctx);
            if (ok) {
                ctx.log("[mared] wait_until: условие выполнено за " + elapsedTicks + " тиков");
                waiting = false;
                return;
            }
        }

        // Просим executor подождать ещё один тик
        exec.pauseCurrent(1);
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "wait_until " + condition; }
}