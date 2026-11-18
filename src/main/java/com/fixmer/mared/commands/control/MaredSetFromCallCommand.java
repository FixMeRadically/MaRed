package com.fixmer.mared.commands.control;

import java.util.List;
import java.util.Map;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.expr.MaredExpr;

/**
 * set x = call func(args) — вызвать функцию и присвоить результат переменной x.
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

        var values=new java.util.ArrayList<Object>();
        for(int i=0;i<fn.params.size();i++)values.add(i<args.size()?MaredCallCommand.resolveArg(args.get(i),ctx):null);
        Map<String,Object> backup=new java.util.HashMap<>();var present=new java.util.HashSet<String>();
        for(String p:fn.params){if(ctx.hasVariable(p))present.add(p);backup.put(p,ctx.getVariable(p));}

        final String targetVar = varName;
        final Map<String, Object> backupFinal = backup;

        MaredScriptExecutor.LoopOwner owner = new MaredScriptExecutor.LoopOwner() {
            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor e, MaredScriptExecutor.Frame bodyFrame) {
                restore(c);

                Object result = null;
                if (e.hasReturnValue()) {
                    result = e.getReturnValue();
                    e.clearReturnValue();
                }
                c.setVariable(targetVar, result);

                // F9: только в verbose
                if (MaredSettings.isVerboseScriptLog()) {
                    c.log("[mared] set " + targetVar + " = " + MaredExpr.stringify(result));
                }

                return false;
            }

            private void restore(MaredScriptContext c){for(String p:fn.params){if(present.contains(p))c.setVariable(p,backupFinal.get(p));else c.removeVariable(p);}}
            @Override public void onDiscard(MaredScriptContext c){restore(c);}
            @Override
            public String describe() { return "set " + targetVar + " = call " + funcName; }
        };

        exec.pushFunctionBody(fn.body, owner);
        for(int i=0;i<fn.params.size();i++)ctx.setVariable(fn.params.get(i),values.get(i));
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "set " + varName + " = call " + funcName + "(" + String.join(", ", args) + ")";
    }
}