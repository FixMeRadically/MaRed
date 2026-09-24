package com.fixmer.mared.commands.expr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fixmer.mared.commands.engine.MaredScriptContext;

/**
 * Быстрый путь для простых выражений.
 *
 * Кэш: ConcurrentHashMap, эвикция по размеру (soft).
 * Negative cache: если выражение не поддержано — храним NOT_SUPPORTED.
 */
final class MaredExprFast {

    private MaredExprFast() {}

    private static final Evaluator NOT_SUPPORTED = ctx -> {
        throw new UnsupportedOperationException();
    };

    @FunctionalInterface
    interface Evaluator {
        Object eval(MaredScriptContext ctx);
    }

    private static final int CACHE_MAX = 1024;
    private static final Map<String, Evaluator> CACHE = new ConcurrentHashMap<>(256);
    private static final AtomicInteger SIZE = new AtomicInteger(0);

    static Evaluator get(String expr) {
        if (expr == null || expr.isEmpty()) return null;

        Evaluator cached = CACHE.get(expr);
        if (cached != null) return cached == NOT_SUPPORTED ? null : cached;

        Evaluator built = tryBuild(expr);
        Evaluator stored = built != null ? built : NOT_SUPPORTED;

        // Мягкая эвикция: если превысили лимит — просто чистим.
        if (SIZE.get() >= CACHE_MAX) {
            CACHE.clear();
            SIZE.set(0);
        }
        if (CACHE.putIfAbsent(expr, stored) == null) SIZE.incrementAndGet();

        return built;
    }

    public static void clearCache() {
        CACHE.clear();
        SIZE.set(0);
    }

    public static int cacheSize() { return CACHE.size(); }

    // ============================================================
    //  Построение
    // ============================================================

    private static Evaluator tryBuild(String expr) {
        String s = expr.trim();
        if (s.isEmpty()) return null;
        int len = s.length();

        // Строковый литерал
        if (len >= 2) {
            char c0 = s.charAt(0);
            char cl = s.charAt(len - 1);
            if ((c0 == '"' && cl == '"') || (c0 == '\'' && cl == '\'')) {
                String inner = s.substring(1, len - 1);
                return ctx -> inner;
            }
        }

        // Унарный минус
        if (s.charAt(0) == '-') {
            Evaluator inner = tryBuildUnary(s.substring(1).trim(), true);
            if (inner != null) return inner;
        }

        // NOT
        if (s.charAt(0) == '!') {
            Evaluator inner = tryBuildUnary(s.substring(1).trim(), false);
            if (inner != null) return ctx -> !MaredExpr.truthy(inner.eval(ctx));
        }

        // Число
        if (isNumber(s)) {
            if (s.indexOf('.') >= 0) {
                double d = Double.parseDouble(s);
                return ctx -> d;
            }
            long l = Long.parseLong(s);
            return ctx -> l;
        }

        // $var или $var.field
        if (s.charAt(0) == '$' && isVarPath(s.substring(1))) {
            String varName = s.substring(1);
            return ctx -> ctx.getVariable(varName);
        }

        return buildBinary(s);
    }

    private static Evaluator tryBuildUnary(String inner, boolean minus) {
        Evaluator e = tryBuild(inner);
        if (e == null) return null;
        if (!minus) return e;
        return ctx -> {
            Object v = e.eval(ctx);
            if (v instanceof Long l) return -l;
            return -MaredExpr.toNumber(v);
        };
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
        return switch (op) {
            case "+"  -> ctx -> MaredExpr.addValues(l.eval(ctx), r.eval(ctx));
            case "-"  -> ctx -> MaredExpr.subValues(l.eval(ctx), r.eval(ctx));
            case "*"  -> ctx -> MaredExpr.mulValues(l.eval(ctx), r.eval(ctx));
            case "/"  -> ctx -> MaredExpr.divValues(l.eval(ctx), r.eval(ctx));
            case "%"  -> ctx -> MaredExpr.modValues(l.eval(ctx), r.eval(ctx));
            case "==" -> ctx -> MaredExpr.equalsValue(l.eval(ctx), r.eval(ctx));
            case "!=" -> ctx -> !MaredExpr.equalsValue(l.eval(ctx), r.eval(ctx));
            case "<"  -> ctx -> MaredExpr.toNumber(l.eval(ctx)) <  MaredExpr.toNumber(r.eval(ctx));
            case ">"  -> ctx -> MaredExpr.toNumber(l.eval(ctx)) >  MaredExpr.toNumber(r.eval(ctx));
            case "<=" -> ctx -> MaredExpr.toNumber(l.eval(ctx)) <= MaredExpr.toNumber(r.eval(ctx));
            case ">=" -> ctx -> MaredExpr.toNumber(l.eval(ctx)) >= MaredExpr.toNumber(r.eval(ctx));
            case "&&" -> ctx -> MaredExpr.truthy(l.eval(ctx)) && MaredExpr.truthy(r.eval(ctx));
            case "||" -> ctx -> MaredExpr.truthy(l.eval(ctx)) || MaredExpr.truthy(r.eval(ctx));
            default   -> null;
        };
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

    // ============================================================
    //  Хелперы
    // ============================================================

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
        if (!isIdentStart(s.charAt(0))) return false;
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
}