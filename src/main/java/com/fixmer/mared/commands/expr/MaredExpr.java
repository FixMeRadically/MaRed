package com.fixmer.mared.commands.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fixmer.mared.commands.engine.MaredScriptContext;

public final class MaredExpr {

    private MaredExpr() {}

    // ============================================================
    //  Токены
    // ============================================================

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

    private static final Token T_PLUS    = new Token(TokType.PLUS, "+", 0, 0);
    private static final Token T_MINUS   = new Token(TokType.MINUS, "-", 0, 0);
    private static final Token T_STAR    = new Token(TokType.STAR, "*", 0, 0);
    private static final Token T_SLASH   = new Token(TokType.SLASH, "/", 0, 0);
    private static final Token T_PERCENT = new Token(TokType.PERCENT, "%", 0, 0);
    private static final Token T_EQ      = new Token(TokType.EQ, "==", 0, 0);
    private static final Token T_NEQ     = new Token(TokType.NEQ, "!=", 0, 0);
    private static final Token T_LT      = new Token(TokType.LT, "<", 0, 0);
    private static final Token T_GT      = new Token(TokType.GT, ">", 0, 0);
    private static final Token T_LTE     = new Token(TokType.LTE, "<=", 0, 0);
    private static final Token T_GTE     = new Token(TokType.GTE, ">=", 0, 0);
    private static final Token T_AND     = new Token(TokType.AND, "&&", 0, 0);
    private static final Token T_OR      = new Token(TokType.OR, "||", 0, 0);
    private static final Token T_NOT     = new Token(TokType.NOT, "!", 0, 0);
    private static final Token T_LPAREN  = new Token(TokType.LPAREN, "(", 0, 0);
    private static final Token T_RPAREN  = new Token(TokType.RPAREN, ")", 0, 0);
    private static final Token T_LBRACKET= new Token(TokType.LBRACKET, "[", 0, 0);
    private static final Token T_RBRACKET= new Token(TokType.RBRACKET, "]", 0, 0);
    private static final Token T_DOT     = new Token(TokType.DOT, ".", 0, 0);
    private static final Token T_COMMA   = new Token(TokType.COMMA, ",", 0, 0);
    private static final Token T_EOF     = new Token(TokType.EOF, "", 0, 0);

    // ============================================================
    //  LRU-кэш токенов
    // ============================================================

    private static final int TOKEN_CACHE_MAX = 512;

    private static final Map<String, Token[]> TOKEN_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Token[]> eldest) {
                return size() > TOKEN_CACHE_MAX;
            }
        });

    private static Token[] tokenizeCached(String src) {
        Token[] cached = TOKEN_CACHE.get(src);
        if (cached != null) return cached;
        Token[] tokens = tokenize(src);
        TOKEN_CACHE.put(src, tokens);
        return tokens;
    }

    public static void clearTokenCache() { TOKEN_CACHE.clear(); }
    public static int tokenCacheSize() { return TOKEN_CACHE.size(); }

    // ============================================================
    //  Public API
    // ============================================================

    /**
     * FIX 0.2.5b: fast-path для простых выражений через MaredExprFast.
     * Если выражение не поддерживается — fallback к обычному парсингу.
     */
    public static Object eval(String expr, MaredScriptContext ctx) {
        if (expr == null || expr.isEmpty()) return "";

        // Fast-path: простые выражения (число, $var, $a op $b, ...)
        MaredExprFast.Evaluator fast = MaredExprFast.get(expr);
        if (fast != null) {
            try {
                return fast.eval(ctx);
            } catch (Exception ignored) {
                // откатываемся к полному парсингу
            }
        }

        Token[] tokens = tokenizeCached(expr);
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

    // ============================================================
    //  Type checks
    // ============================================================

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

    // ============================================================
    //  Быстрые toNumber / toLong
    // ============================================================

    private static boolean isIntegerString(String s) {
        int n = s.length();
        if (n == 0) return false;
        int i = 0;
        if (s.charAt(0) == '-') {
            if (n == 1) return false;
            i = 1;
        }
        for (; i < n; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    public static double toNumber(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return 0;
            if (isIntegerString(t)) {
                try { return Long.parseLong(t); } catch (Exception ignored) {}
            }
            try { return Double.parseDouble(t); } catch (Exception ignored) {}
            return 0;
        }
        return 0;
    }

    public static long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return 0;
            if (isIntegerString(t)) {
                try { return Long.parseLong(t); } catch (Exception ignored) {}
            }
            try { return (long) Double.parseDouble(t); } catch (Exception ignored) {}
            return 0;
        }
        return 0;
    }

    public static Object num(double d) {
        long l = (long) d;
        if ((double) l == d && !Double.isInfinite(d)) {
            return l;
        }
        return d;
    }

    // ============================================================
    //  stringify
    // ============================================================

    public static String stringify(Object v) {
        if (v == null) return "";
        if (v instanceof Long l) return l.toString();
        if (v instanceof Integer i) return i.toString();
        if (v instanceof Double d) return String.valueOf(d);
        if (v instanceof Float f) return String.valueOf(f);
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof String s) return s;
        if (v instanceof List<?> list) {
            int n = list.size();
            if (n == 0) return "[]";
            StringBuilder sb = new StringBuilder(n * 8 + 2);
            sb.append('[');
            for (int i = 0; i < n; i++) {
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
        if (v instanceof String s) {
            int len = s.length();
            if (len == 0) return false;
            if (len == 1 && s.charAt(0) == '0') return false;
            if (len == 5 && s.equalsIgnoreCase("false")) return false;
            return true;
        }
        if (v instanceof List<?> list) return !list.isEmpty();
        return true;
    }

    // ============================================================
    //  Токенизация
    // ============================================================

    private static Token[] tokenize(String src) {
        int n = src.length();
        ArrayList<Token> out = new ArrayList<>(Math.max(8, n / 3));

        int i = 0;
        while (i < n) {
            char c = src.charAt(i);

            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { i++; continue; }

            if (c >= '0' && c <= '9'
                || (c == '.' && i + 1 < n && src.charAt(i + 1) >= '0' && src.charAt(i + 1) <= '9')) {
                int start = i;
                boolean isFloat = false;
                while (i < n) {
                    char ch = src.charAt(i);
                    if (ch >= '0' && ch <= '9') { i++; }
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
                    char ch = src.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        i++;
                        char esc = src.charAt(i);
                        switch (esc) {
                            case 'n':  sb.append('\n'); break;
                            case 't':  sb.append('\t'); break;
                            case '\\': sb.append('\\'); break;
                            case '"':  sb.append('"');  break;
                            case '\'': sb.append('\''); break;
                            default:   sb.append(esc);  break;
                        }
                    } else {
                        sb.append(ch);
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
                while (i < n && isIdentChar(src.charAt(i))) i++;

                while (i < n && src.charAt(i) == '.' && i + 1 < n) {
                    int j = i + 1;
                    while (j < n && isIdentChar(src.charAt(j))) j++;
                    if (j == i + 1) break;
                    int k = j;
                    while (k < n && isWhitespace(src.charAt(k))) k++;
                    if (k < n && src.charAt(k) == '(') break;
                    i = j;
                }

                out.add(new Token(TokType.VAR, src.substring(start, i), 0, 0));
                continue;
            }

            if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentChar(src.charAt(i))) i++;
                out.add(new Token(TokType.IDENT, src.substring(start, i), 0, 0));
                continue;
            }

            switch (c) {
                case '+': out.add(T_PLUS); i++; break;
                case '-': out.add(T_MINUS); i++; break;
                case '*': out.add(T_STAR); i++; break;
                case '/': out.add(T_SLASH); i++; break;
                case '%': out.add(T_PERCENT); i++; break;
                case '(': out.add(T_LPAREN); i++; break;
                case ')': out.add(T_RPAREN); i++; break;
                case '[': out.add(T_LBRACKET); i++; break;
                case ']': out.add(T_RBRACKET); i++; break;
                case ',': out.add(T_COMMA); i++; break;
                case '.': out.add(T_DOT); i++; break;
                case '=':
                    if (i + 1 < n && src.charAt(i + 1) == '=') { out.add(T_EQ); i += 2; }
                    else { out.add(T_EQ); i++; }
                    break;
                case '!':
                    if (i + 1 < n && src.charAt(i + 1) == '=') { out.add(T_NEQ); i += 2; }
                    else { out.add(T_NOT); i++; }
                    break;
                case '<':
                    if (i + 1 < n && src.charAt(i + 1) == '=') { out.add(T_LTE); i += 2; }
                    else { out.add(T_LT); i++; }
                    break;
                case '>':
                    if (i + 1 < n && src.charAt(i + 1) == '=') { out.add(T_GTE); i += 2; }
                    else { out.add(T_GT); i++; }
                    break;
                case '&':
                    if (i + 1 < n && src.charAt(i + 1) == '&') { out.add(T_AND); i += 2; }
                    else { i++; }
                    break;
                case '|':
                    if (i + 1 < n && src.charAt(i + 1) == '|') { out.add(T_OR); i += 2; }
                    else { i++; }
                    break;
                default: i++;
            }
        }
        out.add(T_EOF);
        return out.toArray(new Token[0]);
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    // ============================================================
    //  Parser
    // ============================================================

    private static final class Parser {
        final Token[] tokens;
        final MaredScriptContext ctx;
        int pos = 0;

        Parser(Token[] tokens, MaredScriptContext ctx) {
            this.tokens = tokens;
            this.ctx = ctx;
        }

        Token peek() { return tokens[pos]; }
        Token next() { return tokens[pos++]; }

        void expect(TokType t) {
            if (tokens[pos].type != t) {
                throw new RuntimeException("expr: expected " + t + ", got " + tokens[pos].type);
            }
            pos++;
        }

        boolean match(TokType t) {
            if (tokens[pos].type == t) { pos++; return true; }
            return false;
        }

        Object parseExpression() { return parseOr(); }

        Object parseOr() {
            Object left = parseAnd();
            while (tokens[pos].type == TokType.OR) {
                pos++;
                Object right = parseAnd();
                left = truthy(left) || truthy(right);
            }
            return left;
        }

        Object parseAnd() {
            Object left = parseEquality();
            while (tokens[pos].type == TokType.AND) {
                pos++;
                Object right = parseEquality();
                left = truthy(left) && truthy(right);
            }
            return left;
        }

        Object parseEquality() {
            Object left = parseComparison();
            while (true) {
                TokType t = tokens[pos].type;
                if (t == TokType.EQ) {
                    pos++;
                    Object right = parseComparison();
                    left = equalsValue(left, right);
                } else if (t == TokType.NEQ) {
                    pos++;
                    Object right = parseComparison();
                    left = !equalsValue(left, right);
                } else break;
            }
            return left;
        }

        Object parseComparison() {
            Object left = parseAdd();
            while (true) {
                TokType t = tokens[pos].type;
                if (t == TokType.LT || t == TokType.GT
                    || t == TokType.LTE || t == TokType.GTE) {
                    pos++;
                    Object right = parseAdd();
                    double ln = toNumber(left);
                    double rn = toNumber(right);
                    switch (t) {
                        case LT  -> left = ln <  rn;
                        case GT  -> left = ln >  rn;
                        case LTE -> left = ln <= rn;
                        case GTE -> left = ln >= rn;
                    }
                } else break;
            }
            return left;
        }

        Object parseAdd() {
            Object left = parseMul();
            while (true) {
                TokType t = tokens[pos].type;
                if (t == TokType.PLUS) {
                    pos++;
                    left = addValues(left, parseMul());
                } else if (t == TokType.MINUS) {
                    pos++;
                    left = subValues(left, parseMul());
                } else break;
            }
            return left;
        }

        Object parseMul() {
            Object left = parseUnary();
            while (true) {
                TokType t = tokens[pos].type;
                if (t == TokType.STAR) {
                    pos++;
                    left = mulValues(left, parseUnary());
                } else if (t == TokType.SLASH) {
                    pos++;
                    left = divValues(left, parseUnary());
                } else if (t == TokType.PERCENT) {
                    pos++;
                    left = modValues(left, parseUnary());
                } else break;
            }
            return left;
        }

        Object parseUnary() {
            TokType t = tokens[pos].type;
            if (t == TokType.MINUS) {
                pos++;
                Object v = parseUnary();
                if (v instanceof Long l) return -l;
                return -toNumber(v);
            }
            if (t == TokType.NOT) {
                pos++;
                return !truthy(parseUnary());
            }
            return parsePostfix();
        }

        Object parsePostfix() {
            Object value = parsePrimary();
            while (true) {
                TokType t = tokens[pos].type;
                if (t == TokType.LBRACKET) {
                    pos++;
                    Object idx = parseExpression();
                    expect(TokType.RBRACKET);
                    value = indexValue(value, idx);
                } else if (t == TokType.LPAREN) {
                    pos++;
                    List<Object> args = parseArgs();
                    if (value instanceof String fnName && !fnName.isEmpty()) {
                        if (ctx.hasFunction(fnName)) {
                            value = ctx.callFunction(fnName, args);
                        } else {
                            value = MaredBuiltins.call(fnName, args, ctx);
                        }
                    } else {
                        throw new RuntimeException("expr: cannot call non-function value");
                    }
                } else if (t == TokType.DOT) {
                    pos++;
                    Token nameTok = next();
                    if (nameTok.type != TokType.IDENT) {
                        throw new RuntimeException("expr: expected method name after '.'");
                    }
                    String method = nameTok.text;
                    expect(TokType.LPAREN);
                    List<Object> args = parseArgs();
                    value = MaredMethods.call(value, method, args);
                } else {
                    break;
                }
            }
            return value;
        }

        private List<Object> parseArgs() {
            if (tokens[pos].type == TokType.RPAREN) {
                pos++;
                return Collections.emptyList();
            }
            List<Object> args = new ArrayList<>(4);
            args.add(parseExpression());
            while (tokens[pos].type == TokType.COMMA) {
                pos++;
                args.add(parseExpression());
            }
            expect(TokType.RPAREN);
            return args;
        }

        Object parsePrimary() {
            Token t = next();
            switch (t.type) {
                case INT:    return t.intVal;
                case FLOAT:  return t.floatVal;
                case STRING: return t.text;

                case VAR: {
                    String varName = t.text;
                    if ("true".equals(varName))  return Boolean.TRUE;
                    if ("false".equals(varName)) return Boolean.FALSE;
                    if ("null".equals(varName))  return null;
                    if (tokens[pos].type == TokType.LPAREN) {
                        return varName;
                    }
                    return ctx.getVariable(varName);
                }

                case LPAREN: {
                    Object v = parseExpression();
                    expect(TokType.RPAREN);
                    return v;
                }

                case LBRACKET: {
                    List<Object> list = new ArrayList<>(4);
                    if (tokens[pos].type != TokType.RBRACKET) {
                        list.add(parseExpression());
                        while (tokens[pos].type == TokType.COMMA) {
                            pos++;
                            list.add(parseExpression());
                        }
                    }
                    expect(TokType.RBRACKET);
                    return list;
                }

                case IDENT: {
                    String text = t.text;

                    if ("call".equals(text)) {
                        Token nameTok = next();
                        if (nameTok.type != TokType.IDENT) {
                            throw new RuntimeException("expr: expected function name after 'call'");
                        }
                        expect(TokType.LPAREN);
                        List<Object> args = parseArgs();
                        if (ctx.hasFunction(nameTok.text)) {
                            return ctx.callFunction(nameTok.text, args);
                        }
                        return MaredBuiltins.call(nameTok.text, args, ctx);
                    }

                    if (tokens[pos].type == TokType.LPAREN) {
                        pos++;
                        List<Object> args = parseArgs();
                        if (ctx.hasFunction(text)) {
                            return ctx.callFunction(text, args);
                        }
                        return MaredBuiltins.call(text, args, ctx);
                    }

                    if ("true".equals(text))  return Boolean.TRUE;
                    if ("false".equals(text)) return Boolean.FALSE;
                    if ("null".equals(text))  return null;

                    Object v = ctx.getVariable(text);
                    if (v != null) return v;
                    if (ctx.hasVariable(text)) return null;

                    return text;
                }

                default:
                    throw new RuntimeException("expr: unexpected token " + t.type);
            }
        }
    }

    // ============================================================
    //  Арифметика (публичные — используются MaredExprFast)
    // ============================================================

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

    public static Object addValues(Object a, Object b) {
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            List<Object> merged = new ArrayList<>(la.size() + lb.size());
            merged.addAll(la);
            merged.addAll(lb);
            return merged;
        }
        if (a instanceof String || b instanceof String) {
            return stringify(a) + stringify(b);
        }
        if (a == null) return b;
        if (b == null) return a;
        if (a instanceof Long la && b instanceof Long lb) return la + lb;
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                return na.doubleValue() + nb.doubleValue();
            }
            return na.longValue() + nb.longValue();
        }
        return toNumber(a) + toNumber(b);
    }

    public static Object subValues(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) return la - lb;
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                return na.doubleValue() - nb.doubleValue();
            }
            return na.longValue() - nb.longValue();
        }
        return toNumber(a) - toNumber(b);
    }

    public static Object mulValues(Object a, Object b) {
        if (a instanceof Long la && b instanceof Long lb) return la * lb;
        if (a instanceof Number na && b instanceof Number nb) {
            if (a instanceof Double || b instanceof Double) {
                return na.doubleValue() * nb.doubleValue();
            }
            return na.longValue() * nb.longValue();
        }
        return toNumber(a) * toNumber(b);
    }

    public static Object divValues(Object a, Object b) {
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

    public static Object modValues(Object a, Object b) {
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

    public static boolean equalsValue(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Long la && b instanceof Long lb) return la.longValue() == lb.longValue();
        if (a instanceof Number na && b instanceof Number nb) {
            return na.doubleValue() == nb.doubleValue();
        }
        if (a instanceof Boolean ba && b instanceof Boolean bb) return ba == bb;
        if (a instanceof String sa && b instanceof String sb) return sa.equals(sb);
        return stringify(a).equals(stringify(b));
    }
}