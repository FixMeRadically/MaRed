package com.fixmer.genesis.technology.editor;

import com.fixmer.genesis.technology.storage.TextRepository;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Bounded literal search over saved documents. IO belongs on a host worker. */
public final class DocumentSearch {
    public enum Mode { PATH, CONTENT }
    public record Hit(String path, int line, int column, String matchedText, String preview) {}
    public record Result(List<Hit> hits, Map<String, String> errors, int scanned, boolean limited) {
        public Result { hits = List.copyOf(hits); errors = Map.copyOf(errors); }
    }
    public static final int MAX_RESULTS = 200;
    public static final int MAX_FILES = 4096;
    public static final int MAX_CHARACTERS = 8 * 1024 * 1024;
    public static final int MAX_QUERY = 128;
    private DocumentSearch() {}

    private static void check(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException();
    }
    private static char fold(char c, boolean matchCase) {
        return matchCase ? c : Character.toLowerCase(Character.toUpperCase(c));
    }
    private static int[] prefix(String query, boolean matchCase) {
        int[] table = new int[query.length()];
        for (int i = 1, j = 0; i < query.length(); i++) {
            while (j > 0 && fold(query.charAt(i), matchCase) != fold(query.charAt(j), matchCase)) j = table[j - 1];
            if (fold(query.charAt(i), matchCase) == fold(query.charAt(j), matchCase)) j++;
            table[i] = j;
        }
        return table;
    }
    public static Result search(TextRepository repository, String query, Mode mode, boolean matchCase,
                                BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(repository); Objects.requireNonNull(query);
        Objects.requireNonNull(mode);Objects.requireNonNull(cancelled);
        if(query.length()>MAX_QUERY||query.indexOf('\n')>=0||query.indexOf('\r')>=0)
            throw new IllegalArgumentException("Search query must be one line, up to 128 characters");
        if(query.isEmpty())return new Result(List.of(),Map.of(),0,false);
        check(cancelled);
        return search(repository.list(),repository::read,query,mode,matchCase,cancelled);
    }
    @FunctionalInterface public interface Reader {String read(String path)throws IOException;}
    /** Search immutable host snapshots using the same bounded literal engine as saved repositories. */
    public static Result search(List<String> paths,Reader reader,String query,Mode mode,boolean matchCase,
                                BooleanSupplier cancelled)throws IOException {
        Objects.requireNonNull(paths);Objects.requireNonNull(reader);Objects.requireNonNull(query);
        Objects.requireNonNull(mode); Objects.requireNonNull(cancelled);
        if (query.isEmpty()) return new Result(List.of(), Map.of(), 0, false);
        if (query.length() > MAX_QUERY || query.indexOf('\n') >= 0 || query.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Search query must be one line, up to 128 characters");
        check(cancelled);
        List<Hit> hits = new ArrayList<>();
        Map<String, String> errors = new LinkedHashMap<>();
        int scanned = 0, characters = 0;
        int[] prefix = prefix(query, matchCase);
        TextSearch pathSearch=new TextSearch(query,matchCase);
        for (String path : paths) {
            check(cancelled);
            if (scanned >= MAX_FILES) return new Result(hits, errors, scanned, true);
            scanned++;
            if (mode == Mode.PATH) {
                if (pathSearch.find(path, 0, false) != null)
                    hits.add(new Hit(path, -1, 0, "", ""));
            } else {
                String text;
                try { text = reader.read(path).replace("\r", ""); }
                catch (IOException error) { errors.put(path, String.valueOf(error.getMessage())); continue; }
                check(cancelled);
                if (text.length() > MAX_CHARACTERS - characters) return new Result(hits, errors, scanned, true);
                characters += text.length();
                int line = 0, lineStart = 0, lineEnd = text.indexOf('\n');
                if (lineEnd < 0) lineEnd = text.length();
                for (int i = 0, j = 0; i < text.length(); i++) {
                    if ((i & 511) == 0) check(cancelled);
                    char c = fold(text.charAt(i), matchCase);
                    while (j > 0 && c != fold(query.charAt(j), matchCase)) j = prefix[j - 1];
                    if (c == fold(query.charAt(j), matchCase)) j++;
                    if (j == query.length()) {
                        int start = i + 1 - j;
                        int previewStart = Math.max(lineStart, start - 40);
                        int previewEnd = Math.min(lineEnd, start + 120);
                        String preview = (previewStart > lineStart ? "…" : "") + text.substring(previewStart, previewEnd)
                            + (previewEnd < lineEnd ? "…" : "");
                        hits.add(new Hit(path, line, start - lineStart, text.substring(start, i + 1), preview));
                        if (hits.size() >= MAX_RESULTS) return new Result(hits, errors, scanned, true);
                        j = prefix[j - 1];
                    }
                    if (text.charAt(i) == '\n') {
                        line++; lineStart = i + 1; lineEnd = text.indexOf('\n', lineStart);
                        if (lineEnd < 0) lineEnd = text.length();
                    }
                }
            }
            if (hits.size() >= MAX_RESULTS) return new Result(hits, errors, scanned, true);
        }
        check(cancelled);
        return new Result(hits, errors, scanned, false);
    }
}
