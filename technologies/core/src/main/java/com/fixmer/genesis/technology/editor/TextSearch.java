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
    public record Replacement(String text, int count) {}

    /** True only for the exact current range; replacement never follows an obsolete selection. */
    public boolean matches(String text, int start, int end) {
        if (query.isEmpty() || start < 0 || end > text.length() || end - start != query.length()) return false;
        for (int i = 0; i < query.length(); i++) if (fold(text.charAt(start + i)) != fold(query.charAt(i))) return false;
        return true;
    }

    /** One linear pass, left-to-right, non-overlapping, without interpreting $ or backslashes. */
    public Replacement replaceAll(String text, String replacement, int maxOutputChars) {
        java.util.Objects.requireNonNull(text);
        java.util.Objects.requireNonNull(replacement);
        if (maxOutputChars < 0 || text.length() > maxOutputChars) throw new IllegalArgumentException("replacement size limit");
        if (query.isEmpty()) return new Replacement(text, 0);
        StringBuilder result = new StringBuilder(Math.min(text.length(), 8192));
        int copied = 0, count = 0;
        for (int i = 0, j = 0; i < text.length(); i++) {
            char c = fold(text.charAt(i));
            while (j > 0 && c != fold(query.charAt(j))) j = prefix[j - 1];
            if (c == fold(query.charAt(j))) j++;
            if (j == query.length()) {
                int start = i + 1 - j;
                if ((long) result.length() + start - copied + replacement.length() > maxOutputChars)
                    throw new IllegalArgumentException("replacement size limit");
                result.append(text, copied, start).append(replacement);
                copied = i + 1;
                count++;
                j = 0;
            }
        }
        if (count == 0) return new Replacement(text, 0);
        if ((long) result.length() + text.length() - copied > maxOutputChars)
            throw new IllegalArgumentException("replacement size limit");
        result.append(text, copied, text.length());
        return new Replacement(result.toString(), count);
    }
    public static Position position(String text, int offset) {
        offset = Math.max(0, Math.min(text.length(), offset));
        int line = 0, start = 0;
        for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') { line++; start = i + 1; }
        return new Position(line, offset - start);
    }
}
