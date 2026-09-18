package com.fixmer.mared.script;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MaredBuiltins {

    private MaredBuiltins() {}

    public static Object call(String name, List<Object> args, MaredScriptContext ctx) {
        switch (name) {
            // ---- математика ----
            case "abs": {
                Object v = arg(args, 0);
                if (v instanceof Long l) return Math.abs(l);
                return Math.abs(MaredExpr.toNumber(v));
            }
            case "min": {
                Object a = arg(args, 0), b = arg(args, 1);
                if (a instanceof Long la && b instanceof Long lb) return Math.min(la, lb);
                return Math.min(MaredExpr.toNumber(a), MaredExpr.toNumber(b));
            }
            case "max": {
                Object a = arg(args, 0), b = arg(args, 1);
                if (a instanceof Long la && b instanceof Long lb) return Math.max(la, lb);
                return Math.max(MaredExpr.toNumber(a), MaredExpr.toNumber(b));
            }
            case "clamp": {
                double v = MaredExpr.toNumber(arg(args, 0));
                double lo = MaredExpr.toNumber(arg(args, 1));
                double hi = MaredExpr.toNumber(arg(args, 2));
                if (lo > hi) { double t = lo; lo = hi; hi = t; }
                return MaredExpr.num(Math.max(lo, Math.min(hi, v)));
            }
            case "floor": return (long) Math.floor(MaredExpr.toNumber(arg(args, 0)));
            case "ceil":  return (long) Math.ceil(MaredExpr.toNumber(arg(args, 0)));
            case "round": return (long) Math.round(MaredExpr.toNumber(arg(args, 0)));
            case "sqrt":  return Math.sqrt(MaredExpr.toNumber(arg(args, 0)));
            case "sin":   return Math.sin(MaredExpr.toNumber(arg(args, 0)));
            case "cos":   return Math.cos(MaredExpr.toNumber(arg(args, 0)));
            case "tan":   return Math.tan(MaredExpr.toNumber(arg(args, 0)));
            case "pow":   return MaredExpr.num(Math.pow(MaredExpr.toNumber(arg(args, 0)), MaredExpr.toNumber(arg(args, 1))));
            case "sign": {
                double v = MaredExpr.toNumber(arg(args, 0));
                return v > 0 ? 1L : (v < 0 ? -1L : 0L);
            }
            case "random":
                if (args.isEmpty()) return ThreadLocalRandom.current().nextDouble();
                if (args.size() == 1) {
                    long max = MaredExpr.toLong(arg(args, 0));
                    if (max <= 0) return 0L;
                    return ThreadLocalRandom.current().nextLong(max);
                }
                long min = MaredExpr.toLong(arg(args, 0));
                long max = MaredExpr.toLong(arg(args, 1));
                if (max < min) { long t = min; min = max; max = t; }
                if (max == min) return min;
                return ThreadLocalRandom.current().nextLong(min, max + 1);

            // ---- конвертация ----
            case "int":
                return MaredExpr.toLong(arg(args, 0));
            case "float":
                return MaredExpr.toNumber(arg(args, 0));
            case "str":
                return MaredExpr.stringify(args.isEmpty() ? null : args.get(0));
            case "num":
                return args.isEmpty() ? 0L : MaredExpr.num(MaredExpr.toNumber(args.get(0)));
            case "bool":
                return args.isEmpty() ? false : MaredExpr.truthy(args.get(0));

            // ---- строки ----
            case "len":
                return (long) str(args, 0).length();
            case "upper": return str(args, 0).toUpperCase();
            case "lower": return str(args, 0).toLowerCase();
            case "trim":  return str(args, 0).trim();
            case "trimStart": return str(args, 0).stripLeading();
            case "trimEnd":   return str(args, 0).stripTrailing();
            case "sub": {
                String s = str(args, 0);
                int start = (int) MaredExpr.toLong(arg(args, 1));
                int end = args.size() >= 3 ? (int) MaredExpr.toLong(arg(args, 2)) : s.length();
                if (start < 0) start = 0;
                if (end > s.length()) end = s.length();
                if (start > end) { int t = start; start = end; end = t; }
                return s.substring(start, end);
            }
            case "find": {
                String s = str(args, 0);
                String sub = str(args, 1);
                return (long) s.indexOf(sub);
            }
            case "contains":   return str(args, 0).contains(str(args, 1));
            case "startsWith": return str(args, 0).startsWith(str(args, 1));
            case "endsWith":   return str(args, 0).endsWith(str(args, 1));
            case "replace":    return str(args, 0).replace(str(args, 1), str(args, 2));
            case "replaceAll": return str(args, 0).replaceAll(str(args, 1), str(args, 2));
            case "concat": {
                StringBuilder sb = new StringBuilder();
                for (Object a : args) sb.append(MaredExpr.stringify(a));
                return sb.toString();
            }
            case "repeat": {
                String s = str(args, 0);
                int count = (int) MaredExpr.toLong(arg(args, 1));
                return s.repeat(Math.max(0, count));
            }
            case "split": {
                String s = str(args, 0);
                String sep = args.size() >= 2 ? str(args, 1) : " ";
                String[] parts = s.split(java.util.regex.Pattern.quote(sep), -1);
                List<Object> list = new java.util.ArrayList<>();
                for (String p : parts) list.add(p);
                return list;
            }
            case "join": {
                // join(array, sep)
                Object arr = arg(args, 0);
                String sep = args.size() >= 2 ? str(args, 1) : ", ";
                if (!(arr instanceof List<?> list)) return "";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(sep);
                    Object item = list.get(i);
                    if (item instanceof String) sb.append(item);
                    else sb.append(MaredExpr.stringify(item));
                }
                return sb.toString();
            }
            case "format": {
                // format("pattern", arg1, arg2, ...) → подставляет %s / %d
                if (args.isEmpty()) return "";
                String pattern = str(args, 0);
                Object[] rest = args.subList(1, args.size()).toArray();
                try {
                    return String.format(pattern, rest);
                } catch (Exception e) {
                    return pattern;
                }
            }
            case "startsWithAny": {
                String s = str(args, 0);
                for (int i = 1; i < args.size(); i++) {
                    if (s.startsWith(str(args, i))) return true;
                }
                return false;
            }

            // ---- коллекции (статические функции) ----
            case "sum": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list)) return 0L;
                double total = 0;
                boolean allInt = true;
                for (Object v : list) {
                    if (!(v instanceof Long)) allInt = false;
                    total += MaredExpr.toNumber(v);
                }
                return allInt ? (Object) (long) total : MaredExpr.num(total);
            }
            case "minList": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list) || list.isEmpty()) return null;
                double m = MaredExpr.toNumber(list.get(0));
                boolean allInt = list.get(0) instanceof Long;
                for (Object v : list) {
                    double d = MaredExpr.toNumber(v);
                    if (d < m) m = d;
                    if (!(v instanceof Long)) allInt = false;
                }
                return allInt ? (Object) (long) m : MaredExpr.num(m);
            }
            case "maxList": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list) || list.isEmpty()) return null;
                double m = MaredExpr.toNumber(list.get(0));
                boolean allInt = list.get(0) instanceof Long;
                for (Object v : list) {
                    double d = MaredExpr.toNumber(v);
                    if (d > m) m = d;
                    if (!(v instanceof Long)) allInt = false;
                }
                return allInt ? (Object) (long) m : MaredExpr.num(m);
            }
            case "avg": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list) || list.isEmpty()) return 0.0;
                double total = 0;
                for (Object v : list) total += MaredExpr.toNumber(v);
                return total / list.size();
            }
            case "range": {
                // range(a, b) → [a, a+1, ..., b-1] (полуинтервал, как в Python)
                long a = MaredExpr.toLong(arg(args, 0));
                long b = args.size() >= 2 ? MaredExpr.toLong(arg(args, 1)) : 0;
                if (args.size() < 2) { b = a; a = 0; }
                List<Object> list = new java.util.ArrayList<>();
                if (a < b) {
                    for (long i = a; i < b; i++) list.add(i);
                } else {
                    for (long i = a; i > b; i--) list.add(i);
                }
                return list;
            }

            default:
                throw new RuntimeException("unknown function: " + name);
        }
    }

    private static Object arg(List<Object> args, int idx) {
        if (idx >= args.size()) return null;
        return args.get(idx);
    }

    private static String str(List<Object> args, int idx) {
        if (idx >= args.size()) return "";
        return MaredExpr.stringify(args.get(idx));
    }
}