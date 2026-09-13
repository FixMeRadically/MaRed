package com.fixmer.mared.script.commands;

import java.util.List;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * func name($a, $b) { ... } — регистрирует функцию.
 * Само тело не выполняется. Оно выполнится при вызове `call`.
 */
public class MaredFuncCommand extends MaredScriptCommand {

    private final String name;
    private final List<String> params;
    private final List<MaredScriptCommand> body;

    public MaredFuncCommand(String name, List<String> params, List<MaredScriptCommand> body) {
        this.name = name;
        this.params = params;
        this.body = body;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        ctx.registerFunction(name, params, body);
        ctx.log("[mared] функция объявлена: " + name
            + "(" + String.join(", ", params) + ")");
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "func " + name; }
}