package com.fixmer.mared.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fast-path для простых выражений.
 *
 * FIX 0.2.5b: negative caching.
 * Если выражение не поддерживается — запоминаем это (sentinel),
 * чтобы не пытаться построить Evaluator повторно.
 */
final class MaredExprFast {

    private MaredExprFast() {}

    /** Sentinel: выражение не поддерживается. */
    private static final Evaluator NOT_SUPPORTED = ctx -> { throw new UnsupportedOperationException(); };

    interface Evaluator {
        Object eval(MaredScriptContext ctx);
    }

    private static final int CACHE_MAX = 1024;
    private static final Map<String, Evaluator> CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Evaluator> eldest) {
                return size() > CACHE_MAX;
            }
        });

    /**
     * Получить Evaluator для выражения.
     * Возвращает null, если выражение не поддерживается (или помечено NOT_SUPPORTED).
     */
    static Evaluator get(String expr) {
        if (expr == null || expr.isEmpty()) return null;
        Evaluator cached = CACHE.get(expr);
        if (cached != null) {
            if (cached == NOT_SUPPORTED) return null;
            return cached;
        }

        Evaluator built = tryBuild(expr);
        if (built != null) {
            CACHE.put(expr, built);
        } else {
            // FIX: negative caching
            CACHE.put(expr, NOT_SUPPORTED);
        }
        return built;
    }

    // ============================================================
    //  Построение Evaluator
    // ============================================================

    private static Evaluator tryBuild(String expr) {
        String s = expr.trim();
        if (s.isEmpty()) return null;

        int len = s.length();

        // Строка "..." / '...'
        if (len >= 2) {
            char c0 = s.charAt(0);
            char cLast = s.charAt(len - 1);
            if ((c0 == '"' && cLast == '"') || (c0 == '\'' && cLast == '\'')) {
                String inner = s.substring(1, len - 1);
                return ctx -> inner;
            }
        }

        // Унарный минус
        if (s.charAt(0) == '-') {
            String inner = s.substring(1).trim();
            Evaluator e = tryBuildUnary(inner, true);
            if (e != null) return e;
        }

        // NOT
        if (s.charAt(0) == '!') {
            String inner = s.substring(1).trim();
            Evaluator e = tryBuildUnary(inner, false);
            if (e != null) {
                return ctx -> !MaredExpr.truthy(e.eval(ctx));
            }
        }

        // Число
        if (isNumber(s)) {
            if (s.indexOf('.') >= 0) {
                double d = Double.parseDouble(s);
                return ctx -> d;
            } else {
                long l = Long.parseLong(s);
                return ctx -> l;
            }
        }

        // Переменная $name или $name.field
        if (s.charAt(0) == '$' && isVarPath(s.substring(1))) {
            String varName = s.substring(1);
            return ctx -> ctx.getVariable(varName);
        }

        // Бинарные операции
        return buildBinary(s);
    }

    private static Evaluator tryBuildUnary(String inner, boolean minus) {
        Evaluator e = tryBuild(inner);
        if (e == null) return null;
        if (minus) {
            return ctx -> {
                Object v = e.eval(ctx);
                if (v instanceof Long l) return -l;
                return -MaredExpr.toNumber(v);
            };
        }
        return e;
    }

    // ============================================================
    //  Бинарные операции
    // ============================================================

    private static final String[] BINARY_OPS = {
        "||", "&&",
        "==", "!=",
        "<=", ">=", "<", ">",
        "+", "-",
        "*", "/", "%"
    };

    private static Evaluator buildBinary(String s) {
        for (String op : BINARY_OPS) {
            int idx = findTopLevelOp(s, op);
            if (idx < 0) continue;

            String leftStr = s.substring(0, idx).trim();
            String rightStr = s.substring(idx + op.length()).trim();
            if (leftStr.isEmpty() || rightStr.isEmpty()) continue;

            Evaluator left = tryBuild(leftStr);
            if (left == null) continue;
            Evaluator right = tryBuild(rightStr);
            if (right == null) continue;

            return buildBinOp(op, left, right);
        }
        return null;
    }

    private static Evaluator buildBinOp(String op, Evaluator l, Evaluator r) {
        switch (op) {
            case "+":  return ctx -> MaredExpr.addValues(l.eval(ctx), r.eval(ctx));
            case "-":  return ctx -> MaredExpr.subValues(l.eval(ctx), r.eval(ctx));
            case "*":  return ctx -> MaredExpr.mulValues(l.eval(ctx), r.eval(ctx));
            case "/":  return ctx -> MaredExpr.divValues(l.eval(ctx), r.eval(ctx));
            case "%":  return ctx -> MaredExpr.modValues(l.eval(ctx), r.eval(ctx));
            case "==": return ctx -> MaredExpr.equalsValue(l.eval(ctx), r.eval(ctx));
            case "!=": return ctx -> !MaredExpr.equalsValue(l.eval(ctx), r.eval(ctx));
            case "<":  return ctx -> MaredExpr.toNumber(l.eval(ctx)) <  MaredExpr.toNumber(r.eval(ctx));
            case ">":  return ctx -> MaredExpr.toNumber(l.eval(ctx)) >  MaredExpr.toNumber(r.eval(ctx));
            case "<=": return ctx -> MaredExpr.toNumber(l.eval(ctx)) <= MaredExpr.toNumber(r.eval(ctx));
            case ">=": return ctx -> MaredExpr.toNumber(l.eval(ctx)) >= MaredExpr.toNumber(r.eval(ctx));
            case "&&": return ctx -> MaredExpr.truthy(l.eval(ctx)) && MaredExpr.truthy(r.eval(ctx));
            case "||": return ctx -> MaredExpr.truthy(l.eval(ctx)) || MaredExpr.truthy(r.eval(ctx));
            default:   return null;
        }
    }

    private static int findTopLevelOp(String s, String op) {
        boolean inStr = false;
        char q = 0;
        int depth = 0;
        int lastFound = -1;
        int opLen = op.length();
        int n = s.length();

        for (int i = 0; i <= n - opLen; i++) {
            char c = s.charAt(i);

            if (inStr) {
                if (c == '\\' && i + 1 < n) { i++; continue; }
                if (c == q) inStr = false;
                continue;
            }

            if (c == '"' || c == '\'') { inStr = true; q = c; continue; }
            if (c == '(' || c == '[') { depth++; continue; }
            if (c == ')' || c == ']') { depth--; continue; }

            if (depth != 0) continue;

            if (s.startsWith(op, i)) {
                if (op.equals("<") && i + 1 < n && s.charAt(i + 1) == '=') continue;
                if (op.equals(">") && i + 1 < n && s.charAt(i + 1) == '=') continue;
                lastFound = i;
                i += opLen - 1;
            }
        }
        return lastFound;
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) return false;
        int i = 0;
        int n = s.length();
        if (s.charAt(0) == '-') {
            if (n == 1) return false;
            i = 1;
        }
        boolean hasDigit = false;
        boolean hasDot = false;
        for (; i < n; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') { hasDigit = true; continue; }
            if (c == '.' && !hasDot) { hasDot = true; continue; }
            return false;
        }
        return hasDigit;
    }

    private static boolean isVarPath(String s) {
        if (s.isEmpty()) return false;
        char first = s.charAt(0);
        if (!isIdentStart(first)) return false;
        int n = s.length();
        for (int i = 1; i < n; i++) {
            char c = s.charAt(i);
            if (isIdentChar(c)) continue;
            if (c == '.') {
                if (i + 1 >= n) return false;
                if (!isIdentStart(s.charAt(i + 1))) return false;
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_';
    }

    public static void clearCache() { CACHE.clear(); }
    public static int cacheSize() { return CACHE.size(); }
}