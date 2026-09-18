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

        if (s.startsWith("${") && s.endsWith("}")) {
            String expr = s.substring(2, s.length() - 1);
            try { return MaredExpr.eval(expr, ctx); }
            catch (Exception e) { return s; }
        }

        if (s.startsWith("$") && !s.startsWith("${") && !containsOperator(s)) {
            String varName = s.substring(1);
            Object v = ctx.getVariable(varName);
            if (v != null) return v;
            // ← FIX: возвращаем null вместо "$name"
            return null;
        }

        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }

        try {
            if (s.contains(".") || s.contains("e") || s.contains("E")) {
                return Double.parseDouble(s);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {}

        try { return MaredExpr.eval(s, ctx); }
        catch (Exception e) { return s; }
    }

    /**
     * Проверяет, содержит ли строка арифметические операторы вне кавычек.
     */
    private static boolean containsOperator(String s) {
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%') {
                if ((c == '-' || c == '+') && i == 0) continue;
                return true;
            }
        }
        return false;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "call " + name + "(" + String.join(", ", args) + ")"; }
}