package com.fixmer.mared;

import java.util.ArrayList;
import java.util.List;

/**
 * Парсер скриптов Mared.
 * Разбирает текст в список MaredScriptCommand.
 */
public class MaredScriptParser {

    public static class ParseException extends RuntimeException {
        public final int line;
        public ParseException(int line, String message) {
            super("Line " + line + ": " + message);
            this.line = line;
        }
    }

    public static List<MaredScriptCommand> parse(String text) {
        List<MaredScriptCommand> commands = new ArrayList<>();
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNumber = i + 1;

            // Пустая строка или комментарий.
            if (line.isEmpty() || line.startsWith("//")) continue;

            // Удаляем комментарий в конце строки.
            int commentIdx = findCommentStart(line);
            if (commentIdx >= 0) {
                line = line.substring(0, commentIdx).trim();
                if (line.isEmpty()) continue;
            }

            try {
                MaredScriptCommand cmd = parseLine(line);
                if (cmd != null) commands.add(cmd);
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException(lineNumber, e.getMessage());
            }
        }
        return commands;
    }

    /** Ищет // вне кавычек. */
    private static int findCommentStart(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '"') inString = !inString;
            if (!inString && c == '/' && line.charAt(i + 1) == '/') {
                return i;
            }
        }
        return -1;
    }

    private static MaredScriptCommand parseLine(String line) {
        // Разбиваем на токены, учитывая кавычки.
        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) return null;

        String command = tokens.get(0);
        switch (command) {
            case "say":  return parseSay(tokens);
            case "wait": return parseWait(tokens);
            case "give": return parseGive(tokens);
            case "set":  return parseSet(tokens);
            default:
                throw new ParseException(0, "unknown command: " + command);
        }
    }

    /** Разбивает строку на токены, сохраняя кавычки как часть токена. */
    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inString = !inString;
                current.append(c);
            } else if (c == ' ' && !inString) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    // ---- Парсеры отдельных команд ----

    private static MaredScriptCommand parseSay(List<String> tokens) {
        if (tokens.size() < 2) throw new ParseException(0, "say: missing text");
        String text = stripQuotes(tokens.get(1));
        String scope = "all";
        // Ищем scope=...
        for (int i = 2; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("scope=")) {
                scope = t.substring("scope=".length());
            }
        }
        return new MaredSayCommand(text, scope);
    }

    private static MaredScriptCommand parseWait(List<String> tokens) {
        if (tokens.size() < 2) throw new ParseException(0, "wait: missing amount");
        double amount;
        try {
            amount = Double.parseDouble(tokens.get(1));
        } catch (NumberFormatException e) {
            throw new ParseException(0, "wait: invalid number: " + tokens.get(1));
        }
        String unit = tokens.size() >= 3 ? tokens.get(2) : "seconds";
        return new MaredWaitCommand(amount, unit);
    }

    private static MaredScriptCommand parseGive(List<String> tokens) {
        if (tokens.size() < 3) throw new ParseException(0, "give: missing target or item");
        String target = tokens.get(1);
        String item = tokens.get(2);
        int count = 1;
        if (tokens.size() >= 4) {
            try {
                count = Integer.parseInt(tokens.get(3));
            } catch (NumberFormatException e) {
                throw new ParseException(0, "give: invalid count: " + tokens.get(3));
            }
        }
        return new MaredGiveCommand(target, item, count);
    }

    private static MaredScriptCommand parseSet(List<String> tokens) {
        // set name = value
        if (tokens.size() < 4) throw new ParseException(0, "set: expected 'set name = value'");
        String name = tokens.get(1);
        if (!tokens.get(2).equals("=")) {
            throw new ParseException(0, "set: expected '=' after name");
        }
        // Собираем всё после = в одну строку (могут быть пробелы).
        StringBuilder value = new StringBuilder();
        for (int i = 3; i < tokens.size(); i++) {
            if (i > 3) value.append(' ');
            value.append(tokens.get(i));
        }
        return new MaredSetCommand(name, stripQuotes(value.toString()));
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}