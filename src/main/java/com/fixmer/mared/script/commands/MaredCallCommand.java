package com.fixmer.mared.script.commands;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * call name(arg1, arg2) — вызов функции.
 * Толкает тело функции кадром с меткой functionCall.
 *
 * Аргументы передаются как ОБЪЕКТЫ:
 *   $items       → значение переменной items (List, число, строка)
 *   "строка"     → строка без кавычек
 *   123          → Long
 *   1.5          → Double
 *   ${expr}      → вычисленное выражение
 *
 * ВАЖНО: числа возвращаются как Long / Double, потому что MaredExpr
 * работает только с этими типами.
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

        Map<String, Object> backup = new HashMap<>();
        for (String p : fn.params) {
            backup.put(p, ctx.getVariable(p));
        }

        for (int i = 0; i < fn.params.size(); i++) {
            String p = fn.params.get(i);
            Object value = (i < args.size()) ? resolveArg(args.get(i), ctx) : null;
            ctx.setVariable(p, value);
        }

        List<MaredScriptCommand> body = fn.body;
        final Map<String, Object> backupFinal = backup;
        final String fnName = name;

        MaredScriptExecutor.LoopOwner owner = new MaredScriptExecutor.LoopOwner() {
            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor e, Frame bodyFrame) {
                for (Map.Entry<String, Object> en : backupFinal.entrySet()) {
                    c.setVariable(en.getKey(), en.getValue());
                }

                if (e.hasReturnValue()) {
                    c.setVariable("__last_return", e.getReturnValue());
                    e.clearReturnValue();
                }

                return false;
            }

            @Override
            public String describe() { return "call " + fnName; }
        };

        exec.pushFunctionBody(body, owner);
    }

    /**
     * Определяет значение аргумента:
     *   $var        → значение переменной (любой тип: число, строка, List)
     *   ${expr}     → вычисленное выражение
     *   "строка"    → строка без кавычек
     *   число       → Long или Double (как в MaredExpr)
     *   выражение   → вычисленное MaredExpr.eval
     */
    public static Object resolveArg(String arg, MaredScriptContext ctx) {
        if (arg == null) return null;
        String s = arg.trim();

        if (s.isEmpty()) return "";

        // $var → значение переменной
        if (s.startsWith("$") && !s.startsWith("${")) {
            String varName = s.substring(1);
            Object v = ctx.getVariable(varName);
            if (v != null) return v;
            return s;
        }

        // ${expr} → вычислить выражение
        if (s.startsWith("${") && s.endsWith("}")) {
            String expr = s.substring(2, s.length() - 1);
            try { return MaredExpr.eval(expr, ctx); }
            catch (Exception e) { return s; }
        }

        // "строка" → снять кавычки
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }

        // число → Long или Double (как в MaredExpr)
        try {
            if (s.contains(".") || s.contains("e") || s.contains("E")) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {}

        // выражение без $ → вычислить
        try { return MaredExpr.eval(s, ctx); }
        catch (Exception e) { return s; }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "call " + name + "(" + String.join(", ", args) + ")"; }
}