package com.fixmer.mared.commands.control;

import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;

/**
 * func name($a, $b) { ... } — регистрирует функцию.
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

    public String name(){return name;}
    public List<String> parameters(){return List.copyOf(params);}
    public List<MaredScriptCommand> body(){return List.copyOf(body);}

    @Override
    public boolean execute(MaredScriptContext ctx) {
        ctx.registerFunction(name, params, body);

        // F9: только в verbose
        if (MaredSettings.isVerboseScriptLog()) {
            ctx.log("[mared] функция объявлена: " + name
                + "(" + String.join(", ", params) + ")");
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "func " + name; }
}