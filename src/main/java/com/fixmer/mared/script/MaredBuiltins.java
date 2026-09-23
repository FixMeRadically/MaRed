package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.Collections;
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
            case "pow":   return MaredExpr.num(Math.pow(
                                MaredExpr.toNumber(arg(args, 0)),
                                MaredExpr.toNumber(arg(args, 1))));
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
            case "int":   return MaredExpr.toLong(arg(args, 0));
            case "float": return MaredExpr.toNumber(arg(args, 0));
            case "str":   return MaredExpr.stringify(args.isEmpty() ? null : args.get(0));
            case "num":   return args.isEmpty() ? 0L : MaredExpr.num(MaredExpr.toNumber(args.get(0)));
            case "bool":  return !args.isEmpty() && MaredExpr.truthy(args.get(0));

            // ---- строки ----
            case "len":       return (long) str(args, 0).length();
            case "upper":     return str(args, 0).toUpperCase();
            case "lower":     return str(args, 0).toLowerCase();
            case "trim":      return str(args, 0).trim();
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
            case "find":       return (long) str(args, 0).indexOf(str(args, 1));
            case "contains":   return str(args, 0).contains(str(args, 1));
            case "startsWith": return str(args, 0).startsWith(str(args, 1));
            case "endsWith":   return str(args, 0).endsWith(str(args, 1));
            case "replace":    return str(args, 0).replace(str(args, 1), str(args, 2));
            case "replaceAll": return str(args, 0).replaceAll(str(args, 1), str(args, 2));
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
                    if (item instanceof String) sb.append((String) item);
                    else sb.append(MaredExpr.stringify(item));
                }
                return sb.toString();
            }
            case "format": {
                if (args.isEmpty()) return "";
                String pattern = str(args, 0);
                // Быстрый путь: только %s/%d и нет %n/%% сложных
                if (args.size() == 2 && isSimpleFormat(pattern)) {
                    return simpleFormat2(pattern, args.get(1));
                }
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

            // ---- коллекции ----
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
                    if (v instanceof Long lv) {
                        lsum += lv;
                    } else {
                        allInt = false;
                        dsum += MaredExpr.toNumber(v);
                    }
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
                if (count > 1_000_000) count = 1_000_000; // защита от OOM
                List<Object> list = new ArrayList<>((int) count);
                if (a < b) {
                    for (long i = a; i < b; i++) list.add(i);
                } else {
                    for (long i = a; i > b; i--) list.add(i);
                }
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

    /**
     * Быстрый split по подстроке (без regex).
     * Поведение как у String.split(Pattern.quote(sep), -1):
     *   - пустой sep → split по символам
     *   - сохраняет пустые куски в конце
     */
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

    /**
     * Быстрый поиск min/max в списке.
     * Возвращает Long, если все элементы Long; иначе Double.
     */
    private static Object reduceList(List<?> list, boolean findMin) {
        int n = list.size();
        Object first = list.get(0);
        boolean allInt = first instanceof Long;
        if (allInt) {
            long best = (Long) first;
            for (int i = 1; i < n; i++) {
                Object v = list.get(i);
                if (v instanceof Long lv) {
                    if (findMin ? lv < best : lv > best) best = lv;
                } else {
                    allInt = false;
                    double d = MaredExpr.toNumber(v);
                    double dBest = best;
                    if (findMin ? d < dBest : d > dBest) best = (long) d;
                    // дальше уже double-путь — прерываем
                    return reduceListDouble(list, findMin, best);
                }
            }
            return best;
        }
        return reduceListDouble(list, findMin, MaredExpr.toNumber(first));
    }

    private static Object reduceListDouble(List<?> list, boolean findMin, double initial) {
        int n = list.size();
        double best = initial;
        for (int i = 0; i < n; i++) {
            double d = MaredExpr.toNumber(list.get(i));
            if (findMin ? d < best : d > best) best = d;
        }
        return MaredExpr.num(best);
    }

    // ============================================================
    //  format — быстрый путь
    // ============================================================

    /** Проверяет, что format-строка содержит только %s/%d и не содержит %%. */
    private static boolean isSimpleFormat(String pattern) {
        int n = pattern.length();
        int count = 0;
        for (int i = 0; i < n; i++) {
            char c = pattern.charAt(i);
            if (c == '%') {
                if (i + 1 >= n) return false;
                char next = pattern.charAt(i + 1);
                if (next == '%') return false; // %% — не поддерживаем быстрый путь
                if (next != 's' && next != 'd') return false;
                count++;
                i++;
            }
        }
        return count == 1;
    }

    private static String simpleFormat2(String pattern, Object arg) {
        int idx = pattern.indexOf('%');
        if (idx < 0) return pattern;
        char spec = pattern.charAt(idx + 1);
        String before = pattern.substring(0, idx);
        String after = pattern.substring(idx + 2);
        String value = spec == 'd'
            ? Long.toString(MaredExpr.toLong(arg))
            : MaredExpr.stringify(arg);
        return before + value + after;
    }

    // ============================================================
    //  Управление кэшем (для отладки / очистки)
    // ============================================================

    public static void clearCaches() {
        // Пока пусто — здесь будут кэши, если появятся.
    }
}