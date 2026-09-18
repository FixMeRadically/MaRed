package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredExpr;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * print "..." — как say scope=self, но без префикса [say].
 *
 * FIX F: разэкранирование кавычек перед substitute/eval.
 */
public class MaredPrintCommand extends MaredScriptCommand {

    private final String text;

    public MaredPrintCommand(String text) { this.text = text; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        raw = MaredDebugCommand.unescapeQuotes(raw);

        if (looksLikeConcat(raw)) {
            try {
                Object v = MaredExpr.eval(raw, ctx);
                raw = MaredExpr.stringify(v);
            } catch (Exception ignored) {}
        } else {
            raw = ctx.substitute(raw);
        }
        raw = raw.replace("\t", "    ");
        for (String line : raw.split("\n", -1)) {
            ctx.log("[print] " + line);
        }
        return true;
    }

    private static boolean looksLikeConcat(String s) {
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString && c == '\\' && i + 1 < s.length()) { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString && c == '+') return true;
        }
        return false;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        String t = text == null ? "" : text;
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return "print \"" + t + "\"";
    }
}