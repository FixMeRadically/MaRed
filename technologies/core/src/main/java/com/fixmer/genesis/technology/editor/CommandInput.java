package com.fixmer.genesis.technology.editor;

/** Normalized Minecraft payload and its original UTF-16 offsets. Never interpolates variables. */
public final class CommandInput {
    private final String text;
    private final int[] offsets;
    private final boolean dynamic;
    public CommandInput(String raw, int sourceStart) {
        var out = new StringBuilder();
        var positions = new int[raw.length() + 1];
        char quote = 0;
        boolean whitespace = false, skipped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '$' || c == '\\') skipped = true;
            if (quote == 0 && Character.isWhitespace(c)) {
                if (!whitespace && !out.isEmpty()) { positions[out.length()] = sourceStart + i; out.append(' '); }
                whitespace = true; continue;
            }
            whitespace = false;
            positions[out.length()] = sourceStart + i; out.append(c);
            if (quote != 0 && c == '\\' && i + 1 < raw.length()) {
                positions[out.length()] = sourceStart + ++i; out.append(raw.charAt(i));
            } else if (quote != 0 && c == quote) quote = 0;
            else if (quote == 0 && (c == '"' || c == '\'')) quote = c;
        }
        positions[out.length()] = sourceStart + raw.length();
        text = out.toString();
        offsets = java.util.Arrays.copyOf(positions, out.length() + 1);
        dynamic = skipped;
    }
    /** Avoid duplicating the suffix when completion is requested in the middle of a plain token. */
    public int replacementEnd(int start, int end, String suggestion, int cursor) {
        if (end != cursor || start < 0 || end < start || end > text.length()
                || suggestion.isEmpty() || !suggestion.chars().allMatch(CommandInput::word)
                || !text.substring(start, end).chars().allMatch(CommandInput::word)) return end;
        while (end < text.length() && word(text.charAt(end))) end++;
        return end;
    }
    private static boolean word(int c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.' || c == '/';
    }
    public String text() { return text; }
    public boolean dynamic() { return dynamic; }
    public int sourceOffset(int offset) {
        if (offset < 0 || offset >= offsets.length) throw new IllegalArgumentException("Invalid command offset");
        return offsets[offset];
    }
    public int commandOffset(int sourceOffset) {
        int pos = java.util.Arrays.binarySearch(offsets, sourceOffset);
        return pos >= 0 ? pos : Math.max(0, Math.min(text.length(), -pos - 1));
    }
}
