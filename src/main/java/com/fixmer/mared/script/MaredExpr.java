package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.List;

public final class MaredExpr {

    private MaredExpr() {}

    private enum TokType {
        INT, FLOAT, STRING, IDENT, VAR,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, NEQ, LT, GT, LTE, GTE,
        AND, OR, NOT,
        LPAREN, RPAREN, LBRACKET, RBRACKET,
        DOT, COMMA,
        EOF
    }

    private static final class Token {
        final TokType type;
        final String text;
        final long intVal;
        final double floatVal;

        Token(TokType type, String text, long intVal, double floatVal) {
            this.type = type; this.text = text;
            this.intVal = intVal; this.floatVal = floatVal;
        }
    }

    public static Object eval(String expr, MaredScriptContext ctx) {
        if (expr == null || expr.trim().isEmpty()) return "";
        List<Token> tokens = tokenize(expr);
        Parser p = new Parser(tokens, ctx);
        Object result = p.parseExpression();
        p.expect(TokType.EOF);
        return result;
    }

    public static String evalString(String expr, MaredScriptContext ctx) {
        return stringify(eval(expr, ctx));
    }

    public static boolean evalBool(String expr, MaredScriptContext ctx) {
        return truthy(eval(expr, ctx));
    }

    public static double evalNumber(String expr, MaredScriptContext ctx) {
        return toNumber(eval(expr, ctx));
    }

    public static boolean isInt(Object v) {
        return v instanceof Long || v instanceof Integer
            || v instanceof Short || v instanceof Byte;
    }

    public static boolean isFloat(Object v) {
        return v instanceof Double || v instanceof Float;
    }

    public static boolean isNumber(Object v) {
        return v instanceof Number;
    }

    public static boolean isList(Object v) { return v instanceof List; }

    public static double toNumber(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try {
                if (s.matches("-?\\d+")) return Long.parseLong(s);
                return Double.parseDouble(s.trim());
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    public static long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try {
                if (s.matches("-?\\d+")) return Long.parseLong(s);
                return (long) Double.parseDouble(s.trim());
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    public static Object num(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)
            && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
            return (long) d;
        }
        return d;
    }

    public static String stringify(Object v) {
        if (v == null) return "";
        if (v instanceof Long l) return l.toString();
        if (v instanceof Integer i) return i.toString();
        if (v instanceof Double d) return String.valueOf(d);
        if (v instanceof Float f) return String.valueOf(f);
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                Object item = list.get(i);
                if (item instanceof String) {
                    sb.append('"').append(item).append('"');
                } else {
                    sb.append(stringify(item));
                }
            }
            sb.append(']');
            return sb.toString();
        }
        return v.toString();
    }

    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) return !s.isEmpty() && !s.equals("0")
            && !s.equalsIgnoreCase("false");
        if (v instanceof List<?> list) return !list.isEmpty();
        return true;
    }

    private static List<Token> tokenize(String src) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = src.length();

        while (i < n) {
            char c = src.charAt(i);

            if (Character.isWhitespace(c)) { i++; continue; }

            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                int start = i;
                boolean isFloat = false;
                while (i < n) {
                    char ch = src.charAt(i);
                    if (Character.isDigit(ch)) { i++; }
                    else if (ch == '.' && !isFloat) { isFloat = true; i++; }
                    else break;
                }
                String text = src.substring(start, i);
                if (isFloat) {
                    double d;
                    try { d = Double.parseDouble(text); } catch (Exception e) { d = 0; }
                    out.add(new Token(TokType.FLOAT, text, 0, d));
                } else {
                    long l;
                    try { l = Long.parseLong(text); } catch (Exception e) { l = 0; }
                    out.add(new Token(TokType.INT, text, l, 0));
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < n && src.charAt(i) != quote) {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        i++;
                        char esc = src.charAt(i);
                        sb.append(switch (esc) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            case '\\' -> '\\';
                            case '"' -> '"';
                            case '\'' -> '\'';
                            default -> esc;
                        });
                    } else {
                        sb.append(src.charAt(i));
                    }
                    i++;
                }
                if (i < n) i++;
                out.add(new Token(TokType.STRING, sb.toString(), 0, 0));
                continue;
            }

            if (c == '$') {
                i++;
                int start = i;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i))
                    || src.charAt(i) == '_')) i++;

                while (i < n && src.charAt(i) == '.' && i + 1 < n) {
                    int j = i + 1;
                    while (j < n && (Character.isLetterOrDigit(src.charAt(j))
                        || src.charAt(j) == '_')) j++;

                    if (j == i + 1) break;

                    int k = j;
                    while (k < n && Character.isWhitespace(src.charAt(k))) k++;

                    if (k < n && src.charAt(k) == '(') {
                        break;
                    }

                    i = j;
                }

                out.add(new Token(TokType.VAR, src.substring(start, i), 0, 0));
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i))
                    || src.charAt(i) == '_')) i++;
                out.add(new Token(TokType.IDENT, src.substring(start, i), 0, 0));
                continue;
            }

            switch (c) {
                case '+' -> { out.add(new Token(TokType.PLUS, "+", 0, 0)); i++; }
                case '-' -> { out.add(new Token(TokType.MINUS, "-", 0, 0)); i++; }
                case '*' -> { out.add(new Token(TokType.STAR, "*", 0, 0)); i++; }
                case '/' -> { out.add(new Token(TokType.SLASH, "/", 0, 0)); i++; }
                case '%' -> { out.add(new Token(TokType.PERCENT, "%", 0, 0)); i++; }
                case '(' -> { out.add(new Token(TokType.LPAREN, "(", 0, 0)); i++; }
                case ')' -> { out.add(new Token(TokType.RPAREN, ")", 0, 0)); i++; }
                case '[' -> { out.add(new Token(TokType.LBRACKET, "[", 0, 0)); i++; }
                case ']' -> { out.add(new Token(TokType.RBRACKET, "]", 0, 0)); i++; }
                case ',' -> { out.add(new Token(TokType.COMMA, ",", 0, 0)); i++; }
                case '.' -> { out.add(new Token(TokType.DOT, ".", 0, 0)); i++; }
                case '=' -> {
                    if (i + 1 < n && src.charAt(i + 1) == '=') {
                        out.add(new Token(TokType.EQ, "==", 0, 0)); i += 2;
                    } else {
                        out.add(new Token(TokType.EQ, "=", 0, 0)); i++;
                    }
                }
                case '!' -> {
                    if (i + 1 < n && src.charAt(i + 1) == '=') {
                        out.add(new Token(TokType.NEQ, "!=", 0, 0)); i += 2;
                    } else {
                        out.add(new Token(TokType.NOT, "!", 0, 0)); i++;
                    }
                }
                case '<' -> {
                    if (i + 1 < n && src.charAt(i + 1) == '=') {
                        out.add(new Token(TokType.LTE, "<=", 0, 0)); i += 2;
                    } else {
                        out.add(new Token(TokType.LT, "<", 0, 0)); i++;
                    }
                }
                case '>' -> {
                    if (i + 1 < n && src.charAt(i + 1) == '=') {
                        out.add(new Token(TokType.GTE, ">=", 0, 0)); i += 2;
                    } else {
                        out.add(new Token(TokType.GT, ">", 0, 0)); i++;
                    }
                }
                case '&' -> {
                    if (i + 1 < n && src.charAt(i + 1) == '&') {
                        out.add(new Token(TokType.AND, "&&", 0, 0)); i += 2;
                    } else { i++; }
                }
                case '|' -> {
                    if (i + 1 < n && src.charAt(i + 1) == '|') {
                        out.add(new Token(TokType.OR, "||", 0, 0)); i += 2;
                    } else { i++; }
                }
                default -> i++;
            }
        }
        out.add(new Token(TokType.EOF, "", 0, 0));
        return out;
    }

    private static final class Parser {
        final List<Token> tokens;
        final MaredScriptContext ctx;
        int pos = 0;

        Parser(List<Token> tokens, MaredScriptContext ctx) {
            this.tokens = tokens;
            this.ctx = ctx;
        }

        Token peek() { return tokens.get(pos); }
        Token next() { return tokens.get(pos++); }

        void expect(TokType t) {
            if (peek().type != t) {
                throw new RuntimeException("expr: expected " + t + ", got " + peek().type);
            }
            pos++;
        }

        boolean match(TokType t) {
            if (peek().type == t) { pos++; return true; }
            return false;
        }

        Object parseExpression() { return parseOr(); }

        Object parseOr() {
            Object left = parseAnd();
            while (match(TokType.OR)) {
                Object right = parseAnd();
                left = truthy(left) || truthy(right);
            }
            return left;
        }

        Object parseAnd() {
            Object left = parseEquality();
            while (match(TokType.AND)) {
                Object right = parseEquality();
                left = truthy(left) && truthy(right);
            }
            return left;
        }

        Object parseEquality() {
            Object left = parseComparison();
            while (true) {
                if (match(TokType.EQ)) {
                    Object right = parseComparison();
                    left = equalsValue(left, right);
                } else if (match(TokType.NEQ)) {
                    Object right = parseComparison();
                    left = !equalsValue(left, right);
                } else break;
            }
            return left;
        }

        Object parseComparison() {
            Object left = parseAdd();
            while (true) {
                TokType t = peek().type;
                if (t == TokType.LT || t == TokType.GT || t == TokType.LTE || t == TokType.GTE) {
                    pos++;
                    Object right = parseAdd();
                    double ln = toNumber(left);
                    double rn = toNumber(right);
                    left = switch (t) {
                        case LT -> ln < rn;
                        case GT -> ln > rn;
                        case LTE -> ln <= rn;
                        case GTE -> ln >= rn;
                        default -> false;
                    };
                } else break;
            }
            return left;
        }

        Object parseAdd() {
            Object left = parseMul();
            while (true) {
                if (match(TokType.PLUS)) {
                    Object right = parseMul();
                    left = addValues(left, right);
                } else if (match(TokType.MINUS)) {
                    Object right = parseMul();
                    left = subValues(left, right);
                } else break;
            }
            return left;
        }

        Object parseMul() {
            Object left = parseUnary();
            while (true) {
                if (match(TokType.STAR)) {
                    Object right = parseUnary();
                    left = mulValues(left, right);
                } else if (match(TokType.SLASH)) {
                    Object right = parseUnary();
                    left = divValues(left, right);
                } else if (match(TokType.PERCENT)) {
                    Object right = parseUnary();
                    left = modValues(left, right);
                } else break;
            }
            return left;
        }

        Object parseUnary() {
            if (match(TokType.MINUS)) {
                Object v = parseUnary();
                if (v instanceof Long l) return -l;
                return -toNumber(v);
            }
            if (match(TokType.NOT)) {
                Object v = parseUnary();
                return !truthy(v);
            }
            return parsePostfix();
        }

        Object parsePostfix() {
            Object value = parsePrimary();
            while (true) {
                if (match(TokType.LBRACKET)) {
                    Object idx = parseExpression();
                    expect(TokType.RBRACKET);
                    value = indexValue(value, idx);
                } else if (match(TokType.DOT)) {
                    Token nameTok = next();
                    if (nameTok.type != TokType.IDENT) {
                        throw new RuntimeException("expr: expected method name after '.'");
                    }
                    String method = nameTok.text;
                    expect(TokType.LPAREN);
                    List<Object> args = new ArrayList<>();
                    if (peek().type != TokType.RPAREN) {
                        args.add(parseExpression());
                        while (match(TokType.COMMA)) {
                            args.add(parseExpression());
                        }
                    }
                    expect(TokType.RPAREN);
                    value = MaredMethods.call(value, method, args);
                } else {
                    break;
                }
            }
            return value;
        }

        Object parsePrimary() {
            Token t = next();
            switch (t.type) {
                case INT:    return t.intVal;
                case FLOAT:  return t.floatVal;
                case STRING: return t.text;
                case VAR:    return ctx.getVariable(t.text);
                case LPAREN: {
                    Object v = parseExpression();
                    expect(TokType.RPAREN);
                    return v;
                }
                case LBRACKET: {
                    List<Object> list = new ArrayList<>();
                    if (peek().type != TokType.RBRACKET) {
                        list.add(parseExpression());
                        while (match(TokType.COMMA)) {
                            list.add(parseExpression());
                        }
                    }
                    expect(TokType.RBRACKET);
                    return list;
                }
                case IDENT: {
                    // ← FIX 1: call func(args)
                    if ("call".equals(t.text)) {
                        Token nameTok = next();
                        if (nameTok.type != TokType.IDENT) {
                            throw new RuntimeException("expr: expected function name after 'call'");
                        }
                        expect(TokType.LPAREN);
                        List<Object> args = new ArrayList<>();
                        if (peek().type != TokType.RPAREN) {
                            args.add(parseExpression());
                            while (match(TokType.COMMA)) {
                                args.add(parseExpression());
                            }
                        }
                        expect(TokType.RPAREN);
                        return ctx.callFunction(nameTok.text, args);
                    }

                    if (peek().type == TokType.LPAREN) {
                        pos++;
                        List<Object> args = new ArrayList<>();
                        if (peek().type != TokType.RPAREN) {
                            args.add(parseExpression());
                            while (match(TokType.COMMA)) {
                                args.add(parseExpression());
                            }
                        }
                        expect(TokType.RPAREN);
                        return MaredBuiltins.call(t.text, args, ctx);
                    }
                    if ("true".equals(t.text)) return Boolean.TRUE;
                    if ("false".equals(t.text)) return Boolean.FALSE;
                    if ("null".equals(t.text)) return null;
                    return t.text;
                }
                default:
                    throw new RuntimeException("expr: unexpected token " + t.type);
            }
        }
    }

    private static Object indexValue(Object container, Object idx) {
        if (container == null) return null;
        if (container instanceof List<?> list) {
            int i = (int) toLong(idx);
            if (i < 0) i = list.size() + i;
            if (i < 0 || i >= list.size()) return null;
            return list.get(i);
        }
        if (container instanceof String s) {
            int i = (int) toLong(idx);
            if (i < 0) i = s.length() + i;
            if (i < 0 || i >= s.length()) return "";
            return String.valueOf(s.charAt(i));
        }
        return null;
    }

    private static Object addValues(Object a, Object b) {
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            List<Object> merged = new ArrayList<>(la);
            merged.addAll(lb);
            return merged;
        }
        if (a instanceof String || b instanceof String) {
            return stringify(a) + stringify(b);
        }
        if (a == null) return b;
        if (b == null) return a;
        if (a instanceof Long la && b instanceof Long lb) {
            return la + lb;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                return na.doubleValue() + nb.doubleValue();
            }
            return na.longValue() + nb.longValue();
        }
        return toNumber(a) + toNumber(b);
    }

    private static Object subValues(Object a, Object b) {
        if (a == null) a = 0L;
        if (b == null) b = 0L;
        if (a instanceof Long la && b instanceof Long lb) return la - lb;
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                return na.doubleValue() - nb.doubleValue();
            }
            return na.longValue() - nb.longValue();
        }
        return toNumber(a) - toNumber(b);
    }

    private static Object mulValues(Object a, Object b) {
        if (a == null) a = 0L;
        if (b == null) b = 0L;
        if (a instanceof Long la && b instanceof Long lb) return la * lb;
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                return na.doubleValue() * nb.doubleValue();
            }
            return na.longValue() * nb.longValue();
        }
        return toNumber(a) * toNumber(b);
    }

    private static Object divValues(Object a, Object b) {
        if (a == null) a = 0L;
        if (b == null) b = 0L;
        if (a instanceof Long la && b instanceof Long lb) {
            if (lb == 0) return 0L;
            return la / lb;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                double d = nb.doubleValue();
                if (d == 0) return 0.0;
                return na.doubleValue() / d;
            }
            long lb2 = nb.longValue();
            if (lb2 == 0) return 0L;
            return na.longValue() / lb2;
        }
        double d = toNumber(b);
        if (d == 0) return 0.0;
        return toNumber(a) / d;
    }

    private static Object modValues(Object a, Object b) {
        if (a == null) a = 0L;
        if (b == null) b = 0L;
        if (a instanceof Long la && b instanceof Long lb) {
            if (lb == 0) return 0L;
            return la % lb;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                double d = nb.doubleValue();
                if (d == 0) return 0.0;
                return na.doubleValue() % d;
            }
            long lb2 = nb.longValue();
            if (lb2 == 0) return 0L;
            return na.longValue() % lb2;
        }
        double d = toNumber(b);
        if (d == 0) return 0.0;
        return toNumber(a) % d;
    }

    private static boolean equalsValue(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (isNumber(a) && isNumber(b)) {
            if (a instanceof Long la && b instanceof Long lb) {
                return la.longValue() == lb.longValue();
            }
            return toNumber(a) == toNumber(b);
        }
        if (a instanceof Boolean ba && b instanceof Boolean bb) return ba == bb;
        return stringify(a).equals(stringify(b));
    }
}