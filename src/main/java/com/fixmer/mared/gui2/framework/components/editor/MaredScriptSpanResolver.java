package com.fixmer.mared.gui2.framework.components.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

/**
 * SpanResolver для Mared-скриптов.
 *
 * 0.3.2:
 *   Эвристический лексер по строке — без полного парсера. Разбивает
 *   строку на spans: keywords, control-keywords, literals, builtin
 *   functions, variables, strings, numbers, operators, comments.
 *
 *   Один проход, O(n) на строку. Работает во время render — EditorView
 *   вызывает resolver на каждой видимой строке. На 40 видимых строк
 *   по 100 символов — 4000 итераций, что заведомо не узкое место.
 *
 *   Цвета берутся из активной темы: light/dark. Live theme switching
 *   работает — resolver читает MaredThemeRegistry.active() при каждом
 *   вызове.
 *
 * Не делает:
 *   - multi-line state (не различает "{ }" контексты для vanilla/Mared);
 *   - подсветку отдельного ${expr} внутри строки — строка целиком
 *     одним цветом;
 *   - валидацию синтаксиса.
 */
public final class MaredScriptSpanResolver implements SpanResolver {

    public static final MaredScriptSpanResolver INSTANCE =
        new MaredScriptSpanResolver();

    public static final MaredScriptSpanResolver DARK_EDITOR=new MaredScriptSpanResolver(true);
    private final boolean darkOnly;
    private MaredScriptSpanResolver(){this(false);}
    private MaredScriptSpanResolver(boolean darkOnly){this.darkOnly=darkOnly;}

    // ============================================================
    //  Палитра
    // ============================================================

    private static final class Palette {
        final int comment;
        final int keyword;
        final int control;
        final int literal;
        final int builtin;
        final int variable;
        final int string;
        final int number;
        final int operator;
        final int punct;
        final int ident;

        Palette(int comment, int keyword, int control, int literal,
                int builtin, int variable, int string, int number,
                int operator, int punct, int ident) {
            this.comment = comment;
            this.keyword = keyword;
            this.control = control;
            this.literal = literal;
            this.builtin = builtin;
            this.variable = variable;
            this.string = string;
            this.number = number;
            this.operator = operator;
            this.punct = punct;
            this.ident = ident;
        }
    }

    /** One Dark–style. */
    private static final Palette DARK = new Palette(
        0xFF5C6370,   // comment — серо-зелёный
        0xFFC678DD,   // keyword — фиолетовый
        0xFFC678DD,   // control — фиолетовый (тот же)
        0xFFD19A66,   // literal — оранжевый
        0xFF61AFEF,   // builtin — голубой
        0xFFE06C75,   // variable — красный
        0xFF98C379,   // string — зелёный
        0xFFD19A66,   // number — оранжевый
        0xFF56B6C2,   // operator — бирюзовый
        0xFFABB2BF,   // punct — светло-серый
        0xFFABB2BF    // ident — светло-серый
    );

    /** One Light–style. */
    private static final Palette LIGHT = new Palette(
        0xFF62656B,   // comment
        0xFFA626A4,   // keyword
        0xFFA626A4,   // control
        0xFF986801,   // literal
        0xFF4078F2,   // builtin
        0xFFE45649,   // variable
        0xFF28712A,   // string
        0xFF986801,   // number
        0xFF0184BC,   // operator
        0xFF383A42,   // punct
        0xFF383A42    // ident
    );

    // ============================================================
    //  Keyword sets
    // ============================================================

    /**
     * Mared-команды: подсвечиваются как keyword, если стоят первым
     * словом строки.
     */
    private static final Set<String> COMMANDS = Set.of(
        "say", "print", "log", "debug", "assert", "exit",
        "set", "array", "give", "mc",
        "jump", "attack", "use", "drop", "swap_hands", "select_slot",
        "look", "look_at", "move", "stop",
        "block", "unblock", "toggle",
        "bind", "on", "off", "every", "after", "wait_until",
        "once", "first_join",
        "wait", "delay",
        "call", "return", "break", "continue",
        "if", "elif", "else", "repeat", "for", "while", "func"
    );

    /**
     * Control-flow слова. Подсвечиваются как keyword независимо от
     * позиции в строке (например, `else` в середине — редкость, но
     * пусть будет).
     */
    private static final Set<String> CONTROL = Set.of(
        "if", "elif", "else",
        "for", "while", "repeat",
        "func", "call", "return",
        "break", "continue", "exit",
        "in", "to"
    );

    /** Литералы. */
    private static final Set<String> LITERALS = Set.of(
        "true", "false", "null"
    );

    /**
     * Builtin-функции. Подсвечиваются только в контексте "функция"
     * (за ней идёт "(") или просто если совпало слово.
     */
    private static final Set<String> BUILTIN_FUNCTIONS = Set.of(
        "abs", "min", "max", "clamp", "sign",
        "sqrt", "cbrt", "pow", "floor", "ceil", "round",
        "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
        "log", "log10",
        "random", "uuid", "now", "timestamp",
        "len", "upper", "lower", "trim", "trimStart", "trimEnd",
        "sub", "find", "replace", "replaceAll", "matches",
        "split", "join", "format", "concat", "charAt",
        "startsWith", "endsWith", "startsWithAny",
        "contains", "indexOf",
        "repeat", "padStart", "padEnd",
        "sum", "minList", "maxList", "avg",
        "range", "list", "dist", "dist2d", "lerp",
        "toDeg", "toRad", "wrap",
        "int", "float", "str", "num", "bool",
        "type"
    );

    // ============================================================
    //  Operators
    // ============================================================

    private static final Set<String> TWO_CHAR_OPS = Set.of(
        "==", "!=", "<=", ">=", "&&", "||",
        "+=", "-=", "*=", "/=", "%=",
        "++", "--"
    );

    private static boolean isOperatorChar(char c) {
        return c == '=' || c == '!' || c == '<' || c == '>'
            || c == '+' || c == '-' || c == '*' || c == '/'
            || c == '%' || c == '&' || c == '|';
    }

    private static boolean isPunctChar(char c) {
        return c == '(' || c == ')' || c == '{' || c == '}'
            || c == '[' || c == ']' || c == ',' || c == '.'
            || c == ';' || c == ':';
    }

    // ============================================================
    //  Resolver
    // ============================================================

    @Override
    public int baseColor() {
        return MaredThemeRegistry.active().text;
    }

    @Override
    public List<TextSpan> resolveSpans(int lineIndex, String lineText,
                                       EditorDocument doc) {
        if (lineText == null || lineText.isEmpty()) {
            return Collections.emptyList();
        }

        Palette p = palette();
        int n = lineText.length();
        List<TextSpan> spans = new ArrayList<>(16);

        int i = 0;
        boolean firstNonWsSeen = false;

        while (i < n) {
            char c = lineText.charAt(i);

            // Пробел/таб — не создаём span (baseColor заполнит).
            if (c == ' ' || c == '\t') { i++; continue; }

            // Комментарий до конца строки.
            if (c == '/' && i + 1 < n && lineText.charAt(i + 1) == '/') {
                spans.add(new TextSpan(i, n, p.comment));
                break;
            }

            // Строка.
            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i;
                i++;
                while (i < n) {
                    char cc = lineText.charAt(i);
                    if (cc == '\\' && i + 1 < n) { i += 2; continue; }
                    if (cc == quote) { i++; break; }
                    i++;
                }
                spans.add(new TextSpan(start, i, p.string));
                firstNonWsSeen = true;
                continue;
            }

            // Переменная: $name или ${expr}.
            if (c == '$') {
                int start = i;
                i++;
                if (i < n && lineText.charAt(i) == '{') {
                    int depth = 1;
                    i++;
                    while (i < n && depth > 0) {
                        char cc = lineText.charAt(i);
                        if (cc == '{') depth++;
                        else if (cc == '}') depth--;
                        i++;
                    }
                    spans.add(new TextSpan(start, i, p.variable));
                } else {
                    while (i < n && isIdentChar(lineText.charAt(i))) i++;
                    if (i == start + 1) {
                        // просто "$" без имени — оператор
                        spans.add(new TextSpan(start, i, p.operator));
                    } else {
                        spans.add(new TextSpan(start, i, p.variable));
                    }
                }
                firstNonWsSeen = true;
                continue;
            }

            // Число.
            if (Character.isDigit(c)) {
                int start = i;
                boolean dotSeen = false;
                while (i < n) {
                    char cc = lineText.charAt(i);
                    if (Character.isDigit(cc)) { i++; continue; }
                    if (cc == '.' && !dotSeen) { dotSeen = true; i++; continue; }
                    break;
                }
                spans.add(new TextSpan(start, i, p.number));
                firstNonWsSeen = true;
                continue;
            }

            // Идентификатор / keyword / literal / builtin.
            if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentChar(lineText.charAt(i))) i++;
                String word = lineText.substring(start, i);

                int color;
                if (!firstNonWsSeen && COMMANDS.contains(word)) {
                    color = p.keyword;
                } else if (CONTROL.contains(word)) {
                    color = p.control;
                } else if (LITERALS.contains(word)) {
                    color = p.literal;
                } else if (BUILTIN_FUNCTIONS.contains(word)
                        && i < n && lineText.charAt(i) == '(') {
                    color = p.builtin;
                } else {
                    color = p.ident;
                }
                spans.add(new TextSpan(start, i, color));
                firstNonWsSeen = true;
                continue;
            }

            // Оператор.
            if (isOperatorChar(c)) {
                int start = i;
                if (i + 1 < n) {
                    String two = lineText.substring(i, i + 2);
                    if (TWO_CHAR_OPS.contains(two)) {
                        i += 2;
                        spans.add(new TextSpan(start, i, p.operator));
                        firstNonWsSeen = true;
                        continue;
                    }
                }
                i++;
                spans.add(new TextSpan(start, i, p.operator));
                firstNonWsSeen = true;
                continue;
            }

            // Пунктуация.
            if (isPunctChar(c)) {
                spans.add(new TextSpan(i, i + 1, p.punct));
                i++;
                firstNonWsSeen = true;
                continue;
            }

            // Неизвестный символ — просто текст.
            i++;
        }

        return spans;
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private Palette palette() {
        if(darkOnly)return DARK;
        MaredTheme t = MaredThemeRegistry.active();
        return (t != null && t.light) ? LIGHT : DARK;
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentChar(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }
}