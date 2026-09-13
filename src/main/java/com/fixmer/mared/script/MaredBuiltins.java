package com.fixmer.mared.script;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MaredBuiltins {

    private MaredBuiltins() {}

    public static Object call(String name, List<Object> args, MaredScriptContext ctx) {
        switch (name) {
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
            case "floor": return (long) Math.floor(MaredExpr.toNumber(arg(args, 0)));
            case "ceil":  return (long) Math.ceil(MaredExpr.toNumber(arg(args, 0)));
            case "round": return (long) Math.round(MaredExpr.toNumber(arg(args, 0)));
            case "sqrt":  return Math.sqrt(MaredExpr.toNumber(arg(args, 0)));
            case "sin":   return Math.sin(MaredExpr.toNumber(arg(args, 0)));
            case "cos":   return Math.cos(MaredExpr.toNumber(arg(args, 0)));
            case "tan":   return Math.tan(MaredExpr.toNumber(arg(args, 0)));
            case "pow":   return MaredExpr.num(Math.pow(MaredExpr.toNumber(arg(args, 0)), MaredExpr.toNumber(arg(args, 1))));
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
            case "int":
                return MaredExpr.toLong(arg(args, 0));
            case "float":
                return MaredExpr.toNumber(arg(args, 0));
            case "len":
                return (long) str(args, 0).length();
            case "upper": return str(args, 0).toUpperCase();
            case "lower": return str(args, 0).toLowerCase();
            case "trim":  return str(args, 0).trim();
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
            case "str": return MaredExpr.stringify(args.isEmpty() ? null : args.get(0));
            case "num": return args.isEmpty() ? 0L : MaredExpr.num(MaredExpr.toNumber(args.get(0)));
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