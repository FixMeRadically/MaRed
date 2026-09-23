package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class MaredMethods {

    private MaredMethods() {}

    /** ThreadLocal-контекст для map/filter — не создаём новый на каждый вызов. */
    private static final ThreadLocal<MaredScriptContext> MAP_FILTER_CTX =
        ThreadLocal.withInitial(() -> new MaredScriptContext(null, null, msg -> {}));

    public static Object call(Object target, String method, List<Object> args) {
        if (target == null) {
            throw new RuntimeException("method '" + method + "' on null");
        }

        if (target instanceof String s) {
            return stringMethod(s, method, args);
        }

        if (target instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) target;
            return listMethod(list, method, args);
        }

        switch (method) {
            case "str":   return MaredExpr.stringify(target);
            case "num":   return MaredExpr.toNumber(target);
            case "type":  return typeName(target);
            case "abs":   return MaredExpr.num(Math.abs(MaredExpr.toNumber(target)));
            default:
                throw new RuntimeException("method '" + method + "' not found on " + typeName(target));
        }
    }

    // ============================================================
    //  List methods
    // ============================================================

    private static Object listMethod(List<Object> list, String method, List<Object> args) {
        switch (method) {
            case "push":
            case "add": {
                list.add(arg(args, 0));
                return list;
            }
            case "pop": {
                int n = list.size();
                return n == 0 ? null : list.remove(n - 1);
            }
            case "insert": {
                int idx = (int) MaredExpr.toLong(arg(args, 0));
                Object val = arg(args, 1);
                if (idx < 0) idx = 0;
                int n = list.size();
                if (idx > n) idx = n;
                list.add(idx, val);
                return list;
            }
            case "remove": {
                Object a = arg(args, 0);
                if (a instanceof Long || a instanceof Double) {
                    int n = list.size();
                    int idx = (int) MaredExpr.toLong(a);
                    if (idx < 0) idx = n + idx;
                    if (idx < 0 || idx >= n) return null;
                    return list.remove(idx);
                }
                return list.remove(a);
            }
            case "set": {
                int idx = (int) MaredExpr.toLong(arg(args, 0));
                Object val = arg(args, 1);
                int n = list.size();
                if (idx < 0) idx = n + idx;
                if (idx < 0 || idx >= n) return list;
                list.set(idx, val);
                return list;
            }
            case "clear": {
                list.clear();
                return list;
            }
            case "shuffle": {
                Collections.shuffle(list, new Random());
                return list;
            }
            case "sort": {
                list.sort(MaredMethods::compareValues);
                return list;
            }
            case "reverse": {
                Collections.reverse(list);
                return list;
            }
            case "get": {
                int n = list.size();
                int idx = (int) MaredExpr.toLong(arg(args, 0));
                if (idx < 0) idx = n + idx;
                if (idx < 0 || idx >= n) return null;
                return list.get(idx);
            }
            case "size":
            case "len":
                return (long) list.size();
            case "isEmpty":
                return list.isEmpty();
            case "contains":
                return list.contains(arg(args, 0));
            case "indexOf":
                return (long) list.indexOf(arg(args, 0));
            case "copy":
                return new ArrayList<>(list);
            case "sorted": {
                List<Object> copy = new ArrayList<>(list);
                copy.sort(MaredMethods::compareValues);
                return copy;
            }
            case "reversed": {
                List<Object> copy = new ArrayList<>(list);
                Collections.reverse(copy);
                return copy;
            }
            case "join": {
                String sep = args.isEmpty() ? ", " : MaredExpr.stringify(arg(args, 0));
                int n = list.size();
                if (n == 0) return "";
                StringBuilder sb = new StringBuilder(n * 8);
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(sep);
                    Object item = list.get(i);
                    if (item instanceof String) sb.append((String) item);
                    else sb.append(MaredExpr.stringify(item));
                }
                return sb.toString();
            }
            case "slice": {
                int n = list.size();
                int from = (int) MaredExpr.toLong(arg(args, 0));
                int to = args.size() >= 2 ? (int) MaredExpr.toLong(arg(args, 1)) : n;
                if (from < 0) from = n + from;
                if (to < 0) to = n + to;
                if (from < 0) from = 0;
                if (to > n) to = n;
                if (from > to) { int t = from; from = to; to = t; }
                return new ArrayList<>(list.subList(from, to));
            }
            case "first":
                return list.isEmpty() ? null : list.get(0);
            case "last":
                return list.isEmpty() ? null : list.get(list.size() - 1);

            // ---- агрегаты ----
            case "sum": {
                int n = list.size();
                if (n == 0) return 0L;
                long lsum = 0;
                double dsum = 0;
                boolean allInt = true;
                for (int i = 0; i < n; i++) {
                    Object v = list.get(i);
                    if (v instanceof Long lv) {
                        lsum += lv;
                    } else {
                        allInt = false;
                        dsum += MaredExpr.toNumber(v);
                    }
                }
                return allInt ? (Object) lsum : MaredExpr.num(lsum + dsum);
            }
            case "avg":
            case "average": {
                int n = list.size();
                if (n == 0) return 0.0;
                double total = 0;
                for (int i = 0; i < n; i++) total += MaredExpr.toNumber(list.get(i));
                return total / n;
            }
            case "min": {
                int n = list.size();
                if (n == 0) return null;
                return reduceList(list, true);
            }
            case "max": {
                int n = list.size();
                if (n == 0) return null;
                return reduceList(list, false);
            }

            // ---- take / drop / distinct / flatten ----
            case "take": {
                int n = list.size();
                int k = (int) MaredExpr.toLong(arg(args, 0));
                if (k < 0) k = 0;
                if (k > n) k = n;
                return new ArrayList<>(list.subList(0, k));
            }
            case "drop": {
                int n = list.size();
                int k = (int) MaredExpr.toLong(arg(args, 0));
                if (k < 0) k = 0;
                if (k > n) k = n;
                return new ArrayList<>(list.subList(k, n));
            }
            case "distinct":
            case "unique": {
                int n = list.size();
                LinkedHashSet<Object> set = new LinkedHashSet<>(n * 2);
                set.addAll(list);
                return new ArrayList<>(set);
            }
            case "flatten": {
                int n = list.size();
                List<Object> out = new ArrayList<>(n * 2);
                for (int i = 0; i < n; i++) {
                    Object v = list.get(i);
                    if (v instanceof List<?> sub) out.addAll(sub);
                    else out.add(v);
                }
                return out;
            }

            // ---- map / filter ----
            case "map": {
                int n = list.size();
                if (n == 0 || args.isEmpty()) return new ArrayList<>(list);
                String expr = MaredExpr.stringify(args.get(0));
                return applyMap(list, expr);
            }
            case "filter": {
                int n = list.size();
                if (n == 0 || args.isEmpty()) return new ArrayList<>(list);
                String expr = MaredExpr.stringify(args.get(0));
                return applyFilter(list, expr);
            }
            case "type":
                return "list";
            default:
                throw new RuntimeException("list method '" + method + "' not found");
        }
    }

    private static Object applyMap(List<Object> list, String expr) {
        MaredScriptContext ctx = MAP_FILTER_CTX.get();
        int n = list.size();
        List<Object> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Object v = list.get(i);
            ctx.setVariable("v", v);
            ctx.setVariable("i", (long) i);
            try {
                result.add(MaredExpr.eval(expr, ctx));
            } catch (Exception e) {
                result.add(null);
            }
        }
        return result;
    }

    private static Object applyFilter(List<Object> list, String expr) {
        MaredScriptContext ctx = MAP_FILTER_CTX.get();
        int n = list.size();
        List<Object> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Object v = list.get(i);
            ctx.setVariable("v", v);
            ctx.setVariable("i", (long) i);
            try {
                Object r = MaredExpr.eval(expr, ctx);
                if (MaredExpr.truthy(r)) result.add(v);
            } catch (Exception e) {
                result.add(v);
            }
        }
        return result;
    }

    private static Object reduceList(List<?> list, boolean findMin) {
        int n = list.size();
        Object first = list.get(0);
        if (first instanceof Long) {
            long best = (Long) first;
            boolean allInt = true;
            for (int i = 1; i < n; i++) {
                Object v = list.get(i);
                if (v instanceof Long lv) {
                    if (findMin ? lv < best : lv > best) best = lv;
                } else {
                    allInt = false;
                    break;
                }
            }
            if (allInt) return best;
        }
        double best = MaredExpr.toNumber(first);
        for (int i = 0; i < n; i++) {
            double d = MaredExpr.toNumber(list.get(i));
            if (findMin ? d < best : d > best) best = d;
        }
        return MaredExpr.num(best);
    }

    // ============================================================
    //  String methods
    // ============================================================

    private static Object stringMethod(String s, String method, List<Object> args) {
        switch (method) {
            case "len":
            case "size":
                return (long) s.length();
            case "upper":
                return s.toUpperCase(Locale.ROOT);
            case "lower":
                return s.toLowerCase(Locale.ROOT);
            case "trim":
                return s.trim();
            case "trimStart":
                return s.stripLeading();
            case "trimEnd":
                return s.stripTrailing();
            case "sub":
            case "substring": {
                int n = s.length();
                int from = (int) MaredExpr.toLong(arg(args, 0));
                int to = args.size() >= 2 ? (int) MaredExpr.toLong(arg(args, 1)) : n;
                if (from < 0) from = n + from;
                if (to < 0) to = n + to;
                if (from < 0) from = 0;
                if (to > n) to = n;
                if (from > to) { int t = from; from = to; to = t; }
                return s.substring(from, to);
            }
            case "find":
            case "indexOf":
                return (long) s.indexOf(MaredExpr.stringify(arg(args, 0)));
            case "contains":
                return s.contains(MaredExpr.stringify(arg(args, 0)));
            case "startsWith":
                return s.startsWith(MaredExpr.stringify(arg(args, 0)));
            case "endsWith":
                return s.endsWith(MaredExpr.stringify(arg(args, 0)));
            case "replace":
                return s.replace(
                    MaredExpr.stringify(arg(args, 0)),
                    MaredExpr.stringify(arg(args, 1)));
            case "replaceAll":
                return s.replaceAll(
                    MaredExpr.stringify(arg(args, 0)),
                    MaredExpr.stringify(arg(args, 1)));
            case "repeat": {
                int k = (int) MaredExpr.toLong(arg(args, 0));
                return k <= 0 ? "" : s.repeat(k);
            }
            case "split": {
                String sep = args.isEmpty() ? " " : MaredExpr.stringify(arg(args, 0));
                return splitFast(s, sep);
            }
            case "charAt": {
                int n = s.length();
                int i = (int) MaredExpr.toLong(arg(args, 0));
                if (i < 0) i = n + i;
                if (i < 0 || i >= n) return "";
                return String.valueOf(s.charAt(i));
            }
            case "isEmpty":
                return s.isEmpty();
            case "num":
            case "toNum":
                return MaredExpr.num(MaredExpr.toNumber(s));
            case "type":
                return "string";
            default:
                throw new RuntimeException("string method '" + method + "' not found");
        }
    }

    /** Быстрый split без regex — тот же, что в MaredBuiltins. */
    private static List<Object> splitFast(String s, String sep) {
        List<Object> out = new ArrayList<>();
        if (sep.isEmpty()) {
            int n = s.length();
            out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(String.valueOf(s.charAt(i)));
            return out;
        }
        int sepLen = sep.length();
        int from = 0;
        while (true) {
            int idx = s.indexOf(sep, from);
            if (idx < 0) {
                out.add(s.substring(from));
                return out;
            }
            out.add(s.substring(from, idx));
            from = idx + sepLen;
        }
    }

    // ============================================================
    //  Compare / type
    // ============================================================

    private static int compareValues(Object a, Object b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a instanceof Long la && b instanceof Long lb) {
            return Long.compare(la, lb);
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        if (a instanceof String sa && b instanceof String sb) {
            return sa.compareTo(sb);
        }
        return MaredExpr.stringify(a).compareTo(MaredExpr.stringify(b));
    }

    private static Object arg(List<Object> args, int idx) {
        return idx < args.size() ? args.get(idx) : null;
    }

    private static String typeName(Object v) {
        if (v == null) return "null";
        if (v instanceof Long) return "int";
        if (v instanceof Double) return "float";
        if (v instanceof String) return "string";
        if (v instanceof Boolean) return "bool";
        if (v instanceof List) return "list";
        return v.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }
}