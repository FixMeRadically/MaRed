package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * Базовый класс всех команд Mared.
 *
 * Простые команды (say, set, give, wait, log, debug, assert) переопределяют
 * execute(ctx) — executor вызовет её из execute(ctx, exec).
 *
 * Блоковые команды (if, repeat, while, for, func/call) переопределяют
 * execute(ctx, exec) и толкают кадры в executor.
 */
public abstract class MaredScriptCommand {

    /**
     * Простая команда. Возвращает true — успех, false — прервать весь скрипт.
     * По умолчанию ничего не делает и возвращает true.
     */
    public boolean execute(MaredScriptContext ctx) {
        return true;
    }

    /**
     * Основной вход. Executor вызывает именно этот метод.
     * По умолчанию — просто вызывает execute(ctx).
     * Блоковые команды переопределяют и работают со стеком executor'а.
     */
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        boolean ok = execute(ctx);
        if (!ok) exec.stopAll();
    }

    /** Задержка после выполнения в тиках. Только для простых команд. */
    public int getDelayTicks() { return 0; }

    /** Человекочитаемое имя для логов. */
    public abstract String describe();
}