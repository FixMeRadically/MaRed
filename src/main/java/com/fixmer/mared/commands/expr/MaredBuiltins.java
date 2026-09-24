package com.fixmer.mared.commands.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.fixmer.mared.commands.engine.MaredScriptContext;

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
            case "cbrt":  return Math.cbrt(MaredExpr.toNumber(arg(args, 0)));
            case "sin":   return Math.sin(MaredExpr.toNumber(arg(args, 0)));
            case "cos":   return Math.cos(MaredExpr.toNumber(arg(args, 0)));
            case "tan":   return Math.tan(MaredExpr.toNumber(arg(args, 0)));
            case "asin":  return Math.asin(MaredExpr.toNumber(arg(args, 0)));
            case "acos":  return Math.acos(MaredExpr.toNumber(arg(args, 0)));
            case "atan":  return Math.atan(MaredExpr.toNumber(arg(args, 0)));
            case "atan2": return Math.atan2(MaredExpr.toNumber(arg(args, 0)),
                                            MaredExpr.toNumber(arg(args, 1)));
            case "log":   return Math.log(MaredExpr.toNumber(arg(args, 0)));
            case "log10": return Math.log10(MaredExpr.toNumber(arg(args, 0)));
            case "pow":   return MaredExpr.num(Math.pow(
                                MaredExpr.toNumber(arg(args, 0)),
                                MaredExpr.toNumber(arg(args, 1))));
            case "sign": {
                double v = MaredExpr.toNumber(arg(args, 0));
                return v > 0 ? 1L : (v < 0 ? -1L : 0L);
            }
            case "random": {
                if (args.isEmpty()) return ThreadLocalRandom.current().nextDouble();
                if (args.size() == 1) {
                    long max = MaredExpr.toLong(arg(args, 0));
                    if (max <= 0) return 0L;
                    return ThreadLocalRandom.current().nextLong(max);
                }
                long mn = MaredExpr.toLong(arg(args, 0));
                long mx = MaredExpr.toLong(arg(args, 1));
                if (mx < mn) { long t = mn; mn = mx; mx = t; }
                if (mx == mn) return mn;
                return ThreadLocalRandom.current().nextLong(mn, mx + 1);
            }

            // ---- конвертация ----
            case "int":   return MaredExpr.toLong(arg(args, 0));
            case "float": return MaredExpr.toNumber(arg(args, 0));
            case "str":   return MaredExpr.stringify(args.isEmpty() ? null : args.get(0));
            case "num":   return args.isEmpty() ? 0L : MaredExpr.num(MaredExpr.toNumber(args.get(0)));
            case "bool":  return !args.isEmpty() && MaredExpr.truthy(args.get(0));

            // ---- строки ----
            case "len":       return (long) str(args, 0).length();
            case "upper":     return str(args, 0).toUpperCase(Locale.ROOT);
            case "lower":     return str(args, 0).toLowerCase(Locale.ROOT);
            case "trim":      return str(args, 0).trim();
            case "trimStart": return str(args, 0).stripLeading();
            case "trimEnd":   return str(args, 0).stripTrailing();
            case "sub": {
                String s = str(args, 0);
                int start = (int) MaredExpr.toLong(arg(args, 1));
                int end = args.size() >= 3 ? (int) MaredExpr.toLong(arg(args, 2)) : s.length();
                int n = s.length();
                if (start < 0) start = Math.max(0, n + start);
                if (end < 0) end = Math.max(0, n + end);
                if (start > n) start = n;
                if (end > n) end = n;
                if (start > end) { int t = start; start = end; end = t; }
                return s.substring(start, end);
            }
            case "find":       return (long) str(args, 0).indexOf(str(args, 1));
            case "contains":   return str(args, 0).contains(str(args, 1));
            case "startsWith": return str(args, 0).startsWith(str(args, 1));
            case "endsWith":   return str(args, 0).endsWith(str(args, 1));
            case "replace":    return str(args, 0).replace(str(args, 1), str(args, 2));
            case "replaceAll": return str(args, 0).replaceAll(str(args, 1), str(args, 2));
            case "matches":    return str(args, 0).matches(str(args, 1));
            case "concat": {
                int n = args.size();
                if (n == 0) return "";
                if (n == 1) return MaredExpr.stringify(args.get(0));
                StringBuilder sb = new StringBuilder(n * 8);
                for (int i = 0; i < n; i++) sb.append(MaredExpr.stringify(args.get(i)));
                return sb.toString();
            }
            case "repeat": {
                String s = str(args, 0);
                int count = (int) MaredExpr.toLong(arg(args, 1));
                return count <= 0 ? "" : s.repeat(count);
            }
            case "split": {
                String s = str(args, 0);
                String sep = args.size() >= 2 ? str(args, 1) : " ";
                return splitFast(s, sep);
            }
            case "join": {
                Object arr = arg(args, 0);
                String sep = args.size() >= 2 ? str(args, 1) : ", ";
                if (!(arr instanceof List<?> list)) return "";
                int n = list.size();
                if (n == 0) return "";
                StringBuilder sb = new StringBuilder(n * 8);
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(sep);
                    Object item = list.get(i);
                    if (item instanceof String s) sb.append(s);
                    else sb.append(MaredExpr.stringify(item));
                }
                return sb.toString();
            }
            case "charAt": {
                String s = str(args, 0);
                int i = (int) MaredExpr.toLong(arg(args, 1));
                int n = s.length();
                if (i < 0) i = n + i;
                if (i < 0 || i >= n) return "";
                return String.valueOf(s.charAt(i));
            }
            case "format": return formatImpl(args);
            case "padStart": {
                String s = str(args, 0);
                int target = (int) MaredExpr.toLong(arg(args, 1));
                String pad = args.size() >= 3 ? str(args, 2) : " ";
                if (s.length() >= target || pad.isEmpty()) return s;
                StringBuilder sb = new StringBuilder(target);
                while (sb.length() < target - s.length()) sb.append(pad);
                return sb.substring(0, target - s.length()) + s;
            }
            case "padEnd": {
                String s = str(args, 0);
                int target = (int) MaredExpr.toLong(arg(args, 1));
                String pad = args.size() >= 3 ? str(args, 2) : " ";
                if (s.length() >= target || pad.isEmpty()) return s;
                StringBuilder sb = new StringBuilder(s);
                while (sb.length() < target) sb.append(pad);
                return sb.substring(0, target);
            }
            case "startsWithAny": {
                String s = str(args, 0);
                for (int i = 1; i < args.size(); i++) {
                    if (s.startsWith(str(args, i))) return true;
                }
                return false;
            }

            // ---- коллекции ----
            case "list": {
                List<Object> out = new ArrayList<>(args.size());
                out.addAll(args);
                return out;
            }
            case "sum": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list)) return 0L;
                int n = list.size();
                if (n == 0) return 0L;
                long lsum = 0;
                double dsum = 0;
                boolean allInt = true;
                for (int i = 0; i < n; i++) {
                    Object v = list.get(i);
                    if (v instanceof Long lv) lsum += lv;
                    else { allInt = false; dsum += MaredExpr.toNumber(v); }
                }
                return allInt ? (Object) lsum : MaredExpr.num(lsum + dsum);
            }
            case "minList": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list) || list.isEmpty()) return null;
                return reduceList(list, true);
            }
            case "maxList": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list) || list.isEmpty()) return null;
                return reduceList(list, false);
            }
            case "avg": {
                Object arr = arg(args, 0);
                if (!(arr instanceof List<?> list) || list.isEmpty()) return 0.0;
                int n = list.size();
                double total = 0;
                for (int i = 0; i < n; i++) total += MaredExpr.toNumber(list.get(i));
                return total / n;
            }
            case "range": {
                long a = MaredExpr.toLong(arg(args, 0));
                long b = args.size() >= 2 ? MaredExpr.toLong(arg(args, 1)) : 0;
                if (args.size() < 2) { b = a; a = 0; }
                if (a == b) return Collections.emptyList();
                long count = Math.abs(b - a);
                if (count > 1_000_000) count = 1_000_000;
                List<Object> list = new ArrayList<>((int) count);
                if (a < b) for (long i = a; i < b; i++) list.add(i);
                else for (long i = a; i > b; i--) list.add(i);
                return list;
            }

            // ---- геометрия / утилиты ----
            case "dist": {
                double x1 = MaredExpr.toNumber(arg(args, 0));
                double y1 = MaredExpr.toNumber(arg(args, 1));
                double z1 = MaredExpr.toNumber(arg(args, 2));
                double x2 = MaredExpr.toNumber(arg(args, 3));
                double y2 = MaredExpr.toNumber(arg(args, 4));
                double z2 = MaredExpr.toNumber(arg(args, 5));
                double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
                return Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            case "dist2d": {
                double x1 = MaredExpr.toNumber(arg(args, 0));
                double z1 = MaredExpr.toNumber(arg(args, 1));
                double x2 = MaredExpr.toNumber(arg(args, 2));
                double z2 = MaredExpr.toNumber(arg(args, 3));
                double dx = x2 - x1, dz = z2 - z1;
                return Math.sqrt(dx * dx + dz * dz);
            }
            case "now":       return System.currentTimeMillis();
            case "timestamp": return System.currentTimeMillis() / 1000L;
            case "uuid":      return java.util.UUID.randomUUID().toString();
            case "lerp": {
                double a = MaredExpr.toNumber(arg(args, 0));
                double b = MaredExpr.toNumber(arg(args, 1));
                double t = MaredExpr.toNumber(arg(args, 2));
                return MaredExpr.num(a + (b - a) * t);
            }
            case "toDeg": return Math.toDegrees(MaredExpr.toNumber(arg(args, 0)));
            case "toRad": return Math.toRadians(MaredExpr.toNumber(arg(args, 0)));
            case "wrap": {
                // wrap(value, min, max) — циклический
                double v = MaredExpr.toNumber(arg(args, 0));
                double mn = MaredExpr.toNumber(arg(args, 1));
                double mx = MaredExpr.toNumber(arg(args, 2));
                if (mx <= mn) return MaredExpr.num(mn);
                double range = mx - mn;
                double w = (v - mn) % range;
                if (w < 0) w += range;
                return MaredExpr.num(mn + w);
            }

            default:
                throw new RuntimeException("unknown function: " + name);
        }
    }

    // ============================================================
    //  Хелперы
    // ============================================================

    private static Object arg(List<Object> args, int idx) {
        return idx < args.size() ? args.get(idx) : null;
    }

    private static String str(List<Object> args, int idx) {
        return idx < args.size() ? MaredExpr.stringify(args.get(idx)) : "";
    }

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
    //  format — быстрый путь для 1–2 аргументов
    // ============================================================

    private static String formatImpl(List<Object> args) {
        if (args.isEmpty()) return "";
        String pattern = str(args, 0);
        int argc = args.size() - 1;

        if (argc == 0) return pattern;
        if (argc == 1 && isSimpleFormat1(pattern)) {
            return simpleFormat1(pattern, args.get(1));
        }
        if (argc == 2 && isSimpleFormat2(pattern)) {
            return simpleFormat2(pattern, args.get(1), args.get(2));
        }
        Object[] rest = args.subList(1, args.size()).toArray();
        try {
            return String.format(pattern, rest);
        } catch (Exception e) {
            return pattern;
        }
    }

    private static boolean isSimpleFormat1(String pattern) {
        int n = pattern.length();
        int count = 0;
        for (int i = 0; i < n; i++) {
            char c = pattern.charAt(i);
            if (c == '%') {
                if (i + 1 >= n) return false;
                char next = pattern.charAt(i + 1);
                if (next == '%') return false;
                if (next != 's' && next != 'd') return false;
                count++;
                i++;
            }
        }
        return count == 1;
    }

    private static boolean isSimpleFormat2(String pattern) {
        int n = pattern.length();
        int count = 0;
        for (int i = 0; i < n; i++) {
            char c = pattern.charAt(i);
            if (c == '%') {
                if (i + 1 >= n) return false;
                char next = pattern.charAt(i + 1);
                if (next == '%') return false;
                if (next != 's' && next != 'd') return false;
                count++;
                i++;
            }
        }
        return count == 2;
    }

    private static String simpleFormat1(String pattern, Object a) {
        int idx = pattern.indexOf('%');
        char spec = pattern.charAt(idx + 1);
        String value = spec == 'd'
            ? Long.toString(MaredExpr.toLong(a))
            : MaredExpr.stringify(a);
        return pattern.substring(0, idx) + value + pattern.substring(idx + 2);
    }

    private static String simpleFormat2(String pattern, Object a, Object b) {
        int idx1 = pattern.indexOf('%');
        int idx2 = pattern.indexOf('%', idx1 + 2);
        String v1 = specValue(pattern, idx1, a);
        String v2 = specValue(pattern, idx2, b);
        StringBuilder sb = new StringBuilder(pattern.length() + v1.length() + v2.length());
        sb.append(pattern, 0, idx1).append(v1);
        sb.append(pattern, idx1 + 2, idx2).append(v2);
        sb.append(pattern, idx2 + 2, pattern.length());
        return sb.toString();
    }

    private static String specValue(String pattern, int idx, Object v) {
        char spec = pattern.charAt(idx + 1);
        return spec == 'd' ? Long.toString(MaredExpr.toLong(v)) : MaredExpr.stringify(v);
    }
}