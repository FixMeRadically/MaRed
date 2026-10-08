package com.fixmer.mared.commands.control;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.genesis.technology.runtime.FrameExecutor.Frame;
import com.fixmer.mared.commands.expr.MaredExpr;

/**
 * call name(arg1, arg2) — вызов функции.
 *
 * Аргументы передаются как ОБЪЕКТЫ. Если arg содержит арифметику
 * (например $n - 1), он вычисляется как выражение.
 *
 * FIX: resolveArg при отсутствии переменной возвращает null,
 * а не строку "$name".
 */
public class MaredCallCommand extends MaredScriptCommand {

    private final String name;
    private final List<String> args;

    public MaredCallCommand(String name, List<String> args) {
        this.name = name;
        this.args = args;
    }

    public String getName() { return name; }
    public List<String> getArgs() { return args; }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        MaredScriptContext.Func fn = ctx.getFunction(name);
        if (fn == null) {
            ctx.log("[error] функция не найдена: " + name);
            return;
        }

        // Resolve every argument before touching the caller's bindings.
        List<Object> values = new ArrayList<>(fn.params.size());
        for (int i = 0; i < fn.params.size(); i++)
            values.add(i < args.size() ? resolveArg(args.get(i), ctx) : null);
        Map<String, Object> backup = new HashMap<>();
        Set<String> present = new HashSet<>();
        for (String p : fn.params) {
            if (ctx.hasVariable(p)) present.add(p);
            backup.put(p, ctx.getVariable(p));
        }

        List<MaredScriptCommand> body = fn.body;
        final Map<String, Object> backupFinal = backup;
        final String fnName = name;

        MaredScriptExecutor.LoopOwner owner = new MaredScriptExecutor.LoopOwner() {
            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor e, Frame bodyFrame) {
                restore(c);

                if (e.hasReturnValue()) {
                    c.setVariable("__last_return", e.getReturnValue());
                    e.clearReturnValue();
                }

                return false;
            }

            private void restore(MaredScriptContext c) {
                for (String p : fn.params) {
                    if (present.contains(p)) c.setVariable(p, backupFinal.get(p));
                    else c.removeVariable(p);
                }
            }
            @Override public void onDiscard(MaredScriptContext c) { restore(c); }
            @Override
            public String describe() { return "call " + fnName; }
        };

        exec.pushFunctionBody(body, owner);
        for (int i = 0; i < fn.params.size(); i++) ctx.setVariable(fn.params.get(i), values.get(i));
    }

    /**
     * Определяет значение аргумента:
     *   $var        → значение переменной (любой тип)
     *   ${expr}     → вычисленное выражение
     *   "строка"    → строка без кавычек
     *   число       → Long или Double
     *   выражение   → вычисленное MaredExpr.eval
     *
     * FIX: если переменная не найдена — возвращает null, а не "$var".
     */
    public static Object resolveArg(String arg, MaredScriptContext ctx) {
        if (arg == null) return null;
        String s = arg.trim();

        if (s.isEmpty()) return "";

        if (s.startsWith("${") && s.endsWith("}")) return MaredExpr.eval(s.substring(2, s.length()-1), ctx);
        if (s.startsWith("$") || s.startsWith("\"") || s.startsWith("'")) return MaredExpr.eval(s, ctx);

        try {
            if (s.contains(".") || s.contains("e") || s.contains("E")) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {}

        try { return MaredExpr.eval(s, ctx); }
        catch (Exception e) { return s; }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "call " + name + "(" + String.join(", ", args) + ")"; }
}