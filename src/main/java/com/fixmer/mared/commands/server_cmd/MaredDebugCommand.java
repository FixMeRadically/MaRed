package com.fixmer.mared.commands.server_cmd;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.expr.MaredExpr;

public class MaredDebugCommand extends MaredScriptCommand {

    private static final long COOLDOWN_MS = 200;
    private static final int MAX_CACHE = 256;

    private static final Map<String, Long> LAST_LOG =
        Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
                return size() > MAX_CACHE;
            }
        });

    private final String expr;

    public MaredDebugCommand(String expr) { this.expr = expr; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String trimmed = expr == null ? "" : expr.trim();

        String message;
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            String inner = unescapeQuotes(trimmed.substring(1, trimmed.length() - 1));
            message = "[debug] " + ctx.substitute(inner);
        } else {
            Object value;
            try { value = MaredExpr.eval(trimmed, ctx); }
            catch (Exception e) { value = ctx.substitute(trimmed); }
            message = "[debug] " + trimmed + " = " + MaredExpr.stringify(value);
        }

        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(message);
        if (last != null && now - last < COOLDOWN_MS) return true;
        LAST_LOG.put(message, now);

        ctx.log(message);
        return true;
    }

    public static String unescapeQuotes(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"'  -> { sb.append('"'); i++; }
                    case '\'' -> { sb.append('\''); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n'  -> { sb.append('\n'); i++; }
                    case 't'  -> { sb.append('\t'); i++; }
                    case 'r'  -> { sb.append('\r'); i++; }
                    default   -> sb.append(c);
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