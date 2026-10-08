package com.fixmer.genesis.technology.editor;

/** Literal search over UTF-16 offsets; no game or rendering dependencies. */
public final class TextSearch {
    public record Match(int start, int end) {}
    public record Position(int line, int column) {}
    private final String query;
    private final boolean matchCase;
    private final int[] prefix;

    public TextSearch(String query, boolean matchCase) {
        this.query = java.util.Objects.requireNonNull(query);
        this.matchCase = matchCase;
        prefix = new int[query.length()];
        for (int i = 1, j = 0; i < query.length(); i++) {
            while (j > 0 && fold(query.charAt(i)) != fold(query.charAt(j))) j = prefix[j - 1];
            if (fold(query.charAt(i)) == fold(query.charAt(j))) j++;
            prefix[i] = j;
        }
    }
    private char fold(char c) {
        return matchCase ? c : Character.toLowerCase(Character.toUpperCase(c));
    }
    /** Forward/backward navigation wraps once, including overlapping matches. */
    public Match find(String text, int from, boolean backwards) {
        if (query.isEmpty() || query.length() > text.length()) return null;
        from = Math.max(0, Math.min(text.length(), from));
        Match first = null, last = null, previous = null;
        for (int i = 0, j = 0; i < text.length(); i++) {
            char c = fold(text.charAt(i));
            while (j > 0 && c != fold(query.charAt(j))) j = prefix[j - 1];
            if (c == fold(query.charAt(j))) j++;
            if (j == query.length()) {
                Match found = new Match(i + 1 - j, i + 1);
                if (first == null) first = found;
                last = found;
                if (!backwards && found.start() >= from) return found;
                if (backwards && found.start() < from) previous = found;
                j = prefix[j - 1];
            }
        }
        return backwards ? (previous == null ? last : previous) : first;
    }
    public static Position position(String text, int offset) {
        offset = Math.max(0, Math.min(text.length(), offset));
        int line = 0, start = 0;
        for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') { line++; start = i + 1; }
        return new Position(line, offset - start);
    }
}
