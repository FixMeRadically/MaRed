package com.fixmer.genesis.technology.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** Finds bracket pairs outside strings and comments using the syntax lexer. */
public final class CodeBrackets {
    private static final int MAX_LINES = 20_000;
    private static final int MAX_LINE_LENGTH = 32_768;
    private static final int MAX_TEXT_LENGTH = 1024 * 1024;
    private static final int MAX_BRACKETS = 100_000;

    private CodeBrackets() {}

    /** Offsets count UTF-16 units and one newline between logical lines. */
    public record Pair(int open, int close) {}

    private record Open(char value, int offset) {}

    /** The line provider supplies zero-based logical lines without newline delimiters. */
    public static List<Pair> pairs(String path, int lineCount, IntFunction<String> lines) {
        Objects.requireNonNull(lines);
        if (lineCount < 0 || lineCount > MAX_LINES) {
            throw new IllegalArgumentException("Unsupported line count");
        }
        var language = CodeSyntaxLexer.languageFor(path);
        var state = CodeSyntaxLexer.State.NORMAL;
        var open = new ArrayDeque<Open>();
        var pairs = new ArrayList<Pair>();
        int offset = 0;
        int brackets = 0;

        for (int row = 0; row < lineCount; row++) {
            String line = Objects.requireNonNull(lines.apply(row));
            if (line.length() > MAX_LINE_LENGTH || offset + line.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("Bracket scan text limit exceeded");
            }
            var lexed = CodeSyntaxLexer.lex(language, line, state);
            state = lexed.next();
            if (lexed.truncated()) {
                throw new IllegalArgumentException("Bracket scan token limit exceeded");
            }

            var tokens = lexed.tokens();
            int token = 0;
            for (int column = 0; column < line.length(); column++) {
                while (token < tokens.size() && tokens.get(token).end() <= column) {
                    token++;
                }
                if (token < tokens.size()) {
                    var span = tokens.get(token);
                    if (span.start() <= column &&
                        (span.kind() == CodeSyntaxLexer.Kind.STRING || span.kind() == CodeSyntaxLexer.Kind.COMMENT)) {
                        column = span.end() - 1;
                        continue;
                    }
                }
                char ch = line.charAt(column);
                if ("([{)]}".indexOf(ch) < 0) continue;
                if (++brackets > MAX_BRACKETS) {
                    throw new IllegalArgumentException("Bracket scan nesting/input limit exceeded");
                }
                if (ch == '(' || ch == '[' || ch == '{') {
                    open.push(new Open(ch, offset + column));
                } else if (!open.isEmpty()) {
                    Open previous = open.peek();
                    char expected = switch (previous.value()) {
                        case '(' -> ')';
                        case '[' -> ']';
                        default -> '}';
                    };
                    if (ch == expected) {
                        open.pop();
                        pairs.add(new Pair(previous.offset(), offset + column));
                    } else {
                        open.clear();
                    }
                }
            }
            offset += line.length() + 1;
        }
        return List.copyOf(pairs);
    }
}
