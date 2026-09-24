package com.fixmer.mared.script.commands;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

public class MaredDebugCommand extends MaredScriptCommand {

    /** Cooldown для одинаковых сообщений (ms). */
    private static final long COOLDOWN_MS = 200;

    /** LRU-кэш: сообщение → последний раз, когда писалось. */
    private static final Map<String, Long> LAST_LOG = new HashMap<>();
    private static final int MAX_CACHE = 256;

    private final String expr;

    public MaredDebugCommand(String expr) {
        this.expr = expr;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String trimmed = expr == null ? "" : expr.trim();

        String message;
        if (trimmed.length() >= 2
            && trimmed.startsWith("\"")
            && trimmed.endsWith("\"")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            inner = unescapeQuotes(inner);
            message = "[debug] " + ctx.substitute(inner);
        } else {
            Object value;
            try {
                value = MaredExpr.eval(trimmed, ctx);
            } catch (Exception e) {
                value = ctx.substitute(trimmed);
            }
            message = "[debug] " + trimmed + " = " + MaredExpr.stringify(value);
        }

        // Cooldown — не пишем одно и то же сообщение чаще 1 раза в 200ms
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(message);
        if (last != null && now - last < COOLDOWN_MS) {
            return true;
        }
        LAST_LOG.put(message, now);

        // Ограничение размера кэша
        if (LAST_LOG.size() > MAX_CACHE) {
            LAST_LOG.clear();
        }

        ctx.log(message);
        return true;
    }

    /**
     * Разэкранирует \" \' \\ \n \t \r.
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