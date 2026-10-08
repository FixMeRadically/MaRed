package com.fixmer.mared.technology.runtime;

import java.util.ArrayList;
import java.util.List;

/** Pure, bounded split of the mixed command-file format. Only a leading { opens MR. */
public record CommandFilePlan(boolean persistent, List<Part> parts) {
    public record Part(int line, boolean script, String text) {}
    public CommandFilePlan { parts = List.copyOf(parts); }

    public static CommandFilePlan parse(String text) {
        if (text == null || text.length() > 1_048_576)
            throw new IllegalArgumentException("File is unavailable or exceeds 1 MiB");
        String[] lines = text.split("\n", -1);
        boolean persistent = lines.length > 0 && lines[0].strip().equals("#persistent");
        var parts = new ArrayList<Part>();
        StringBuilder block = null;
        int depth = 0, start = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = persistent ? 1 : 0; i < lines.length; i++) {
            String raw = lines[i].replace("\r", ""), trimmed = raw.strip();
            if (block == null) {
                if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;
                if (!trimmed.startsWith("{")) {
                    if (trimmed.startsWith("}")) throw new IllegalArgumentException("Line " + (i+1) + ": unexpected }");
                    parts.add(new Part(i+1, false, trimmed.startsWith("/") ? trimmed.substring(1) : trimmed));
                    if (parts.size() > 4096) throw new IllegalArgumentException("Too many file sections (4096)");
                    continue;
                }
                block = new StringBuilder(); start = i+1;
            }
            block.append(raw).append('\n');
            for (int j = 0; j < raw.length(); j++) {
                char c = raw.charAt(j);
                if (escaped) { escaped = false; continue; }
                if (quote != 0) {
                    if (c == '\\') escaped = true;
                    else if (c == quote) quote = 0;
                    continue;
                }
                if (c == '"' || c == '\'') { quote = c; continue; }
                if (c == '/' && j+1 < raw.length() && raw.charAt(j+1) == '/') break;
                if (c == '{') depth++;
                if (c == '}') {
                    if (--depth < 0) throw new IllegalArgumentException("Line " + (i+1) + ": unexpected }");
                    if (depth == 0) {
                        String tail = raw.substring(j+1).strip();
                        if (!tail.isEmpty() && !tail.startsWith("//"))
                            throw new IllegalArgumentException("Line " + (i+1) + ": text after MR block");
                        // Remove the trailing comment: the MR parser's outer-wrapper detection
                        // expects the final significant character to be the closing brace.
                        block.setLength(block.length() - raw.length() - 1);
                        block.append(raw, 0, j+1);
                        break;
                    }
                }
            }
            if (depth == 0 && quote == 0) {
                parts.add(new Part(start, true, block.toString())); block = null;
                if (parts.size() > 4096) throw new IllegalArgumentException("Too many file sections (4096)");
            }
        }
        if (block != null) throw new IllegalArgumentException("Line " + start + ": unclosed MR block or string");
        return new CommandFilePlan(persistent, parts);
    }
}
