package com.fixmer.mared.script.commands;

import java.util.List;
import java.util.Map;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;

/**
 * set x = call func(args) — вызвать функцию и присвоить результат переменной x.
 *
 * Аргументы передаются как объекты (см. MaredCallCommand.resolveArg).
 */
public class MaredSetFromCallCommand extends MaredScriptCommand {

    private final String varName;
    private final String funcName;
    private final List<String> args;

    public MaredSetFromCallCommand(String varName, String funcName, List<String> args) {
        this.varName = varName;
        this.funcName = funcName;
        this.args = args;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        MaredScriptContext.Func fn = ctx.getFunction(funcName);
        if (fn == null) {
            ctx.log("[error] функция не найдена: " + funcName);
            ctx.setVariable(varName, null);
            return;
        }

        Map<String, Object> backup = new java.util.HashMap<>();
        for (String p : fn.params) {
            backup.put(p, ctx.getVariable(p));
        }
        for (int i = 0; i < fn.params.size(); i++) {
            String p = fn.params.get(i);
            Object value = (i < args.size()) ? MaredCallCommand.resolveArg(args.get(i), ctx) : null;
            ctx.setVariable(p, value);
        }

        final String targetVar = varName;
        final Map<String, Object> backupFinal = backup;

        MaredScriptExecutor.LoopOwner owner = new MaredScriptExecutor.LoopOwner() {
            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor e, MaredScriptExecutor.Frame bodyFrame) {
                for (Map.Entry<String, Object> en : backupFinal.entrySet()) {
                    c.setVariable(en.getKey(), en.getValue());
                }

                Object result = null;
                if (e.hasReturnValue()) {
                    result = e.getReturnValue();
                    e.clearReturnValue();
                }
                c.setVariable(targetVar, result);
                c.log("[mared] set " + targetVar + " = " + MaredExpr.stringify(result));

                return false;
            }

            @Override
            public String describe() { return "set " + targetVar + " = call " + funcName; }
        };

        exec.pushFunctionBody(fn.body, owner);
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "set " + varName + " = call " + funcName + "(" + String.join(", ", args) + ")";
    }
}