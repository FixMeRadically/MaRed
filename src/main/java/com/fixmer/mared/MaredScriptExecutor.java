package com.fixmer.mared;

import java.util.List;

import net.minecraft.server.MinecraftServer;

/**
 * Исполнитель скрипта.
 * Выполняет команды по одной. Для wait — откладывает продолжение.
 */
public class MaredScriptExecutor {

    private final MaredScriptContext context;
    private final List<MaredScriptCommand> commands;
    private int index = 0;
    private int waitTicks = 0;
    private boolean finished = false;

    public MaredScriptExecutor(MaredScriptContext context, List<MaredScriptCommand> commands) {
        this.context = context;
        this.commands = commands;
    }

    public boolean isFinished() {
        return finished;
    }

    public MaredScriptContext getContext() {
        return context;
    }

    /**
     * Вызывается каждый тик сервера.
     * Если waitTicks > 0 — уменьшаем, не выполняем команды.
     * Иначе — выполняем следующую команду.
     */
    public void tick() {
        if (finished) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (index >= commands.size()) {
            finished = true;
            return;
        }

        MaredScriptCommand cmd = commands.get(index);
        index++;

        try {
            boolean ok = cmd.execute(context);
            if (!ok) {
                context.log("[error] команда не выполнена: " + cmd.describe());
                finished = true;
                return;
            }
        } catch (Exception e) {
            context.log("[error] " + e.getMessage());
            finished = true;
            return;
        }

        int delay = cmd.getDelayTicks();
        if (delay > 0) {
            waitTicks = delay;
        }
    }
}