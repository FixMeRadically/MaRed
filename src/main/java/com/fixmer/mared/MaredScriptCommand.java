package com.fixmer.mared;

/**
 * Абстрактная команда скрипта.
 * execute() возвращает true, если выполнение успешно.
 * getDelayTicks() — сколько тиков подождать ПОСЛЕ выполнения (для wait).
 */
public abstract class MaredScriptCommand {

    /** Выполнить команду. */
    public abstract boolean execute(MaredScriptContext ctx);

    /**
     * Сколько тиков подождать перед следующей командой.
     * По умолчанию — 0 (без задержки). Для wait — N тиков.
     */
    public int getDelayTicks() {
        return 0;
    }

    /** Для отладки в логе. */
    public abstract String describe();
}