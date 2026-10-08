package com.fixmer.genesis.technology.editor;

import java.util.ArrayList;
import java.util.List;

/** Shared statement boundaries for the MR parser and tools; MC payload braces are data. */
public final class ScriptHeads {
    private ScriptHeads() {}
    public static boolean isMinecraft(String source, int start) {
        return start + 2 <= source.length() && source.startsWith("mc", start)
            && (start + 2 == source.length() || Character.isWhitespace(source.charAt(start + 2)));
    }
    public static int end(String source, int start) {
        boolean mc = isMinecraft(source, start);
        char quote = 0;
        int interpolation = 0, dataDepth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < source.length()) { i++; continue; }
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '$' && i + 1 < source.length() && source.charAt(i + 1) == '{') { interpolation++; i++; continue; }
            if (c == '}' && interpolation > 0) { interpolation--; continue; }
            if (interpolation > 0) continue;
            if (c == '"' || (mc && c == '\'')) { quote = c; continue; }
            if (mc) {
                if (c == '{' || c == '[') { dataDepth++; continue; }
                if ((c == '}' || c == ']') && dataDepth > 0) { dataDepth--; continue; }
            }
            if (dataDepth > 0) continue;
            if (c == '{' || c == '}' || c == ';' || c == '\n') return i;
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') return i;
        }
        return source.length();
    }
    public record Snippet(int start, int end, CommandInput input) {}
    public static List<Snippet> minecraft(String source, int limit) {
        var result = new ArrayList<Snippet>();
        int pos = 0;
        while (pos < source.length() && result.size() < limit) {
            char c = source.charAt(pos);
            if (Character.isWhitespace(c) || c == ';' || c == '{' || c == '}') { pos++; continue; }
            if (source.startsWith("//", pos)) {
                int newline = source.indexOf('\n', pos); pos = newline < 0 ? source.length() : newline + 1; continue;
            }
            int stop = end(source, pos);
            if (isMinecraft(source, pos)) {
                int payload = pos + 2;
                while (payload < stop && Character.isWhitespace(source.charAt(payload))) payload++;
                if (payload < stop && source.charAt(payload) == '/') payload++;
                result.add(new Snippet(payload, stop, new CommandInput(source.substring(payload, stop), payload)));
            }
            pos = Math.max(pos + 1, stop);
        }
        return List.copyOf(result);
    }
    public static Snippet at(String source, int cursor) {
        int pos = 0;
        while (pos < source.length() && pos <= cursor) {
            char c = source.charAt(pos);
            if (Character.isWhitespace(c) || c == ';' || c == '{' || c == '}') { pos++; continue; }
            if (source.startsWith("//", pos)) {
                int newline = source.indexOf('\n', pos); pos = newline < 0 ? source.length() : newline + 1; continue;
            }
            int stop = end(source, pos);
            if (isMinecraft(source, pos)) {
                int payload = pos + 2;
                if (payload == stop) { pos = Math.max(pos + 1, stop); continue; } // Bare mc needs a separator.
                while (payload < stop && Character.isWhitespace(source.charAt(payload))) payload++;
                if (payload < stop && source.charAt(payload) == '/') payload++;
                if (cursor >= payload && cursor <= stop)
                    return new Snippet(payload, stop, new CommandInput(source.substring(payload, stop), payload));
            }
            pos = Math.max(pos + 1, stop);
        }
        return null;
    }
}
