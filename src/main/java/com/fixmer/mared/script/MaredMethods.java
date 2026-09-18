package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class MaredMethods {

    private MaredMethods() {}

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

    private static Object listMethod(List<Object> list, String method, List<Object> args) {
        switch (method) {
            case "push":
            case "add": {
                list.add(arg(args, 0));
                return list;
            }
            case "pop": {
                if (list.isEmpty()) return null;
                return list.remove(list.size() - 1);
            }
            case "insert": {
                int idx = (int) MaredExpr.toLong(arg(args, 0));
                Object val = arg(args, 1);
                if (idx < 0) idx = 0;
                if (idx > list.size()) idx = list.size();
                list.add(idx, val);
                return list;
            }
            case "remove": {
                Object a = arg(args, 0);
                if (a instanceof Long || a instanceof Double) {
                    int idx = (int) MaredExpr.toLong(a);
                    if (idx < 0) idx = list.size() + idx;
                    if (idx < 0 || idx >= list.size()) return null;
                    return list.remove(idx);
                }
                boolean removed = list.remove(a);
                return removed;
            }
            case "set": {
                int idx = (int) MaredExpr.toLong(arg(args, 0));
                Object val = arg(args, 1);
                if (idx < 0) idx = list.size() + idx;
                if (idx < 0 || idx >= list.size()) return list;
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
                int idx = (int) MaredExpr.toLong(arg(args, 0));
                if (idx < 0) idx = list.size() + idx;
                if (idx < 0 || idx >= list.size()) return null;
                return list.get(idx);
            }
            case "size":
            case "len":
                return (long) list.size();
            case "isEmpty":
                return list.isEmpty();
            case "contains":
                return list.contains(arg(args, 0));
            case "indexOf": {
                int idx = list.indexOf(arg(args, 0));
                return (long) idx;
            }
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
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(sep);
                    Object item = list.get(i);
                    if (item instanceof String) sb.append(item);
                    else sb.append(MaredExpr.stringify(item));
                }
                return sb.toString();
            }
            case "slice": {
                int from = (int) MaredExpr.toLong(arg(args, 0));
                int to = args.size() >= 2 ? (int) MaredExpr.toLong(arg(args, 1)) : list.size();
                if (from < 0) from = list.size() + from;
                if (to < 0) to = list.size() + to;
                if (from < 0) from = 0;
                if (to > list.size()) to = list.size();
                if (from > to) { int t = from; from = to; to = t; }
                return new ArrayList<>(list.subList(from, to));
            }
            case "first":
                return list.isEmpty() ? null : list.get(0);
            case "last":
                return list.isEmpty() ? null : list.get(list.size() - 1);

            // ---- NEW: агрегаты ----
            case "sum": {
                double total = 0;
                boolean allInt = true;
                for (Object v : list) {
                    if (!(v instanceof Long)) allInt = false;
                    total += MaredExpr.toNumber(v);
                }
                return allInt ? (Object) (long) total : MaredExpr.num(total);
            }
            case "avg":
            case "average": {
                if (list.isEmpty()) return 0.0;
                double total = 0;
                for (Object v : list) total += MaredExpr.toNumber(v);
                return total / list.size();
            }
            case "min": {
                if (list.isEmpty()) return null;
                double m = MaredExpr.toNumber(list.get(0));
                boolean allInt = list.get(0) instanceof Long;
                for (Object v : list) {
                    double d = MaredExpr.toNumber(v);
                    if (d < m) m = d;
                    if (!(v instanceof Long)) allInt = false;
                }
                return allInt ? (Object) (long) m : MaredExpr.num(m);
            }
            case "max": {
                if (list.isEmpty()) return null;
                double m = MaredExpr.toNumber(list.get(0));
                boolean allInt = list.get(0) instanceof Long;
                for (Object v : list) {
                    double d = MaredExpr.toNumber(v);
                    if (d > m) m = d;
                    if (!(v instanceof Long)) allInt = false;
                }
                return allInt ? (Object) (long) m : MaredExpr.num(m);
            }

            // ---- NEW: take / drop / distinct / flatten ----
            case "take": {
                int n = (int) MaredExpr.toLong(arg(args, 0));
                if (n < 0) n = 0;
                if (n > list.size()) n = list.size();
                return new ArrayList<>(list.subList(0, n));
            }
            case "drop": {
                int n = (int) MaredExpr.toLong(arg(args, 0));
                if (n < 0) n = 0;
                if (n > list.size()) n = list.size();
                return new ArrayList<>(list.subList(n, list.size()));
            }
            case "distinct":
            case "unique": {
                List<Object> out = new ArrayList<>();
                for (Object v : list) {
                    if (!out.contains(v)) out.add(v);
                }
                return out;
            }
            case "flatten": {
                List<Object> out = new ArrayList<>();
                for (Object v : list) {
                    if (v instanceof List<?> sub) out.addAll(sub);
                    else out.add(v);
                }
                return out;
            }

            // ---- map / filter ----
            case "map": {
                if (args.isEmpty()) return new ArrayList<>(list);
                String expr = MaredExpr.stringify(args.get(0));
                return applyMapFilter(list, expr, true);
            }
            case "filter": {
                if (args.isEmpty()) return new ArrayList<>(list);
                String expr = MaredExpr.stringify(args.get(0));
                return applyMapFilter(list, expr, false);
            }
            case "type":
                return "list";
            default:
                throw new RuntimeException("list method '" + method + "' not found");
        }
    }

    private static Object applyMapFilter(List<Object> list, String expr, boolean map) {
        MaredScriptContext tempCtx = new MaredScriptContext(null, null, msg -> {});
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object v = list.get(i);
            tempCtx.setVariable("v", v);
            tempCtx.setVariable("i", (long) i);
            try {
                Object r = MaredExpr.eval(expr, tempCtx);
                if (map) result.add(r);
                else if (MaredExpr.truthy(r)) result.add(v);
            } catch (Exception e) {
                if (map) result.add(null);
                else result.add(v);
            }
        }
        return result;
    }

    private static Object stringMethod(String s, String method, List<Object> args) {
        switch (method) {
            case "len":
            case "size":
                return (long) s.length();
            case "upper":
                return s.toUpperCase();
            case "lower":
                return s.toLowerCase();
            case "trim":
                return s.trim();
            case "trimStart":
                return s.stripLeading();
            case "trimEnd":
                return s.stripTrailing();
            case "sub":
            case "substring": {
                int from = (int) MaredExpr.toLong(arg(args, 0));
                int to = args.size() >= 2 ? (int) MaredExpr.toLong(arg(args, 1)) : s.length();
                if (from < 0) from = s.length() + from;
                if (to < 0) to = s.length() + to;
                if (from < 0) from = 0;
                if (to > s.length()) to = s.length();
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
                int n = (int) MaredExpr.toLong(arg(args, 0));
                return s.repeat(Math.max(0, n));
            }
            case "split": {
                String sep = args.isEmpty() ? " " : MaredExpr.stringify(arg(args, 0));
                String[] parts = s.split(java.util.regex.Pattern.quote(sep), -1);
                List<Object> list = new ArrayList<>();
                for (String p : parts) list.add(p);
                return list;
            }
            case "charAt": {
                int i = (int) MaredExpr.toLong(arg(args, 0));
                if (i < 0) i = s.length() + i;
                if (i < 0 || i >= s.length()) return "";
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

    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (MaredExpr.isNumber(a) && MaredExpr.isNumber(b)) {
            return Double.compare(MaredExpr.toNumber(a), MaredExpr.toNumber(b));
        }
        return MaredExpr.stringify(a).compareTo(MaredExpr.stringify(b));
    }

    private static Object arg(List<Object> args, int idx) {
        if (idx >= args.size()) return null;
        return args.get(idx);
    }

    private static String typeName(Object v) {
        if (v == null) return "null";
        if (v instanceof Long) return "int";
        if (v instanceof Double) return "float";
        if (v instanceof String) return "string";
        if (v instanceof Boolean) return "bool";
        if (v instanceof List) return "list";
        return v.getClass().getSimpleName().toLowerCase();
    }
}