package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * debug <выражение или "строка">
 *
 * FIX:
 *   - если аргумент в кавычках — это строка, разэкранируем кавычки и
 *     подставляем переменные (через substitute)
 *   - иначе — вычисляем выражение и выводим "expr = value"
 */
public class MaredDebugCommand extends MaredScriptCommand {

    private final String expr;

    public MaredDebugCommand(String expr) {
        this.expr = expr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String trimmed = expr == null ? "" : expr.trim();

        // "строка в кавычках" — разэкранируем и substitute
        if (trimmed.length() >= 2
            && trimmed.startsWith("\"")
            && trimmed.endsWith("\"")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            inner = unescapeQuotes(inner);
            String resolved = ctx.substitute(inner);
            ctx.log("[debug] " + resolved);
            return true;
        }

        // иначе — выражение
        Object value;
        try {
            value = MaredExpr.eval(trimmed, ctx);
        } catch (Exception e) {
            value = ctx.substitute(trimmed);
        }
        ctx.log("[debug] " + trimmed + " = " + MaredExpr.stringify(value));
        return true;
    }

    /**
     * Разэкранирует \" \' \\ \n \t.
     * Нужно, потому что парсер скрипта оставляет бэкслеши в токене.
     */
    static String unescapeQuotes(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\'' -> { sb.append('\''); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "debug " + expr; }
}