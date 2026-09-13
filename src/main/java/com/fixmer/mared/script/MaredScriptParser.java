package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.script.commands.MaredAssertCommand;
import com.fixmer.mared.script.commands.MaredBindCommand;
import com.fixmer.mared.script.commands.MaredBreakCommand;
import com.fixmer.mared.script.commands.MaredCallCommand;
import com.fixmer.mared.script.commands.MaredContinueCommand;
import com.fixmer.mared.script.commands.MaredDebugCommand;
import com.fixmer.mared.script.commands.MaredEvalCommand;
import com.fixmer.mared.script.commands.MaredForCommand;
import com.fixmer.mared.script.commands.MaredFuncCommand;
import com.fixmer.mared.script.commands.MaredGiveCommand;
import com.fixmer.mared.script.commands.MaredIfCommand;
import com.fixmer.mared.script.commands.MaredLogCommand;
import com.fixmer.mared.script.commands.MaredRepeatCommand;
import com.fixmer.mared.script.commands.MaredSayCommand;
import com.fixmer.mared.script.commands.MaredScriptCommand;
import com.fixmer.mared.script.commands.MaredSetCommand;
import com.fixmer.mared.script.commands.MaredWaitCommand;
import com.fixmer.mared.script.commands.MaredWhileCommand;

public class MaredScriptParser {

    public static class ParseException extends RuntimeException {
        public final int line;
        public ParseException(int line, String message) {
            super("Line " + line + ": " + message);
            this.line = line;
        }
    }

    public static List<MaredScriptCommand> parse(String text) {
        if (text == null) return new ArrayList<>();
        text = text.replace("\r", "");
        text = text.replace("\\\"", "\"").replace("\\'", "'");
        String trimmed = text.trim();

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        String[] lines = trimmed.split("\n");
        int[] pos = {0};
        List<MaredScriptCommand> result = parseBlock(lines, pos, 1, false);
        if (pos[0] < lines.length) {
            String rest = lines[pos[0]].trim();
            if (!rest.isEmpty() && !rest.equals("}")) {
                throw new ParseException(pos[0] + 1, "unexpected text after block close: " + rest);
            }
        }
        return result;
    }

    private static List<MaredScriptCommand> parseBlock(String[] lines, int[] pos, int baseLine, boolean insideBlock) {
        List<MaredScriptCommand> commands = new ArrayList<>();

        while (pos[0] < lines.length) {
            String rawLine = lines[pos[0]];
            int lineNumber = pos[0] + 1;
            String line = stripComment(rawLine).trim();

            if (line.isEmpty()) { pos[0]++; continue; }

            if (line.equals("}")) {
                if (!insideBlock) throw new ParseException(lineNumber, "'}' without opening '{'");
                pos[0]++;
                return commands;
            }

            String head = line;
            String tail = "";
            int braceIdx = findBraceAfter(line);
            if (braceIdx >= 0) {
                head = line.substring(0, braceIdx).trim();
                tail = line.substring(braceIdx + 1).trim();
            }

            List<String> tokens = tokenize(head);
            if (tokens.isEmpty()) { pos[0]++; continue; }
            String cmd = tokens.get(0);

            switch (cmd) {
                case "if" -> {
                    pos[0]++;
                    commands.add(parseIfChain(tokens, lines, pos, lineNumber));
                    if (!tail.isEmpty()) throw new ParseException(lineNumber, "'if' requires newline after '{'");
                }
                case "repeat" -> {
                    pos[0]++;
                    commands.add(parseRepeat(tokens, lines, pos, lineNumber));
                    if (!tail.isEmpty()) throw new ParseException(lineNumber, "'repeat' requires newline after '{'");
                }
                case "for" -> {
                    pos[0]++;
                    commands.add(parseFor(tokens, lines, pos, lineNumber));
                    if (!tail.isEmpty()) throw new ParseException(lineNumber, "'for' requires newline after '{'");
                }
                case "while" -> {
                    pos[0]++;
                    commands.add(parseWhile(tokens, lines, pos, lineNumber));
                    if (!tail.isEmpty()) throw new ParseException(lineNumber, "'while' requires newline after '{'");
                }
                case "func" -> {
                    pos[0]++;
                    commands.add(parseFunc(tokens, lines, pos, lineNumber));
                    if (!tail.isEmpty()) throw new ParseException(lineNumber, "'func' requires newline after '{'");
                }
                case "bind" -> {
                    pos[0]++;
                    commands.add(parseBind(tokens, lines, pos, lineNumber));
                    if (!tail.isEmpty()) throw new ParseException(lineNumber, "'bind' requires newline after '{'");
                }
                case "break" -> {
                    pos[0]++;
                    commands.add(new MaredBreakCommand());
                }
                case "continue" -> {
                    pos[0]++;
                    commands.add(new MaredContinueCommand());
                }
                default -> {
                    if (braceIdx >= 0) throw new ParseException(lineNumber, "command '" + cmd + "' does not accept { }");
                    commands.add(parseLine(tokens, lineNumber));
                    pos[0]++;
                }
            }
        }

        if (insideBlock) throw new ParseException(baseLine, "block '{' not closed");
        return commands;
    }

    private static MaredScriptCommand parseIfChain(List<String> tokens, String[] lines, int[] pos, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "if: missing condition");
        if (findBraceAfter(lines[pos[0] - 1]) < 0) {
            throw new ParseException(lineNumber, "if: expected '{' after condition");
        }
        String condition = join(tokens.subList(1, tokens.size()));
        List<MaredScriptCommand> thenBody = parseBlock(lines, pos, lineNumber, true);

        List<String> elifConds = new ArrayList<>();
        List<List<MaredScriptCommand>> elifBodies = new ArrayList<>();
        List<MaredScriptCommand> elseBody = null;

        while (pos[0] < lines.length) {
            String nextRaw = lines[pos[0]].trim();
            String next = stripComment(nextRaw).trim();
            if (next.isEmpty()) { pos[0]++; continue; }

            String head = next;
            int braceIdx = findBraceAfter(next);
            if (braceIdx >= 0) head = next.substring(0, braceIdx).trim();
            List<String> t = tokenize(head);
            if (t.isEmpty()) break;
            String kw = t.get(0);

            if ("elif".equals(kw)) {
                if (t.size() < 2) throw new ParseException(pos[0] + 1, "elif: missing condition");
                if (braceIdx < 0) throw new ParseException(pos[0] + 1, "elif: expected '{' after condition");
                String cond = join(t.subList(1, t.size()));
                pos[0]++;
                List<MaredScriptCommand> body = parseBlock(lines, pos, pos[0], true);
                elifConds.add(cond);
                elifBodies.add(body);
                continue;
            }
            if ("else".equals(kw)) {
                if (braceIdx < 0) throw new ParseException(pos[0] + 1, "else: expected '{'");
                pos[0]++;
                elseBody = parseBlock(lines, pos, pos[0], true);
                break;
            }
            break;
        }

        return new MaredIfCommand(condition, thenBody, elifConds, elifBodies, elseBody);
    }

    private static MaredScriptCommand parseRepeat(List<String> tokens, String[] lines, int[] pos, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "repeat: missing count");
        if (findBraceAfter(lines[pos[0] - 1]) < 0) {
            throw new ParseException(lineNumber, "repeat: expected '{' after count");
        }
        String countStr = tokens.get(1);
        if (tokens.size() >= 3 && !"times".equalsIgnoreCase(tokens.get(2))) {
            throw new ParseException(lineNumber, "repeat: expected 'times' or end of line");
        }
        int count;
        try { count = Integer.parseInt(countStr); }
        catch (NumberFormatException e) { throw new ParseException(lineNumber, "repeat: '" + countStr + "' is not a number"); }

        List<MaredScriptCommand> body = parseBlock(lines, pos, lineNumber, true);
        return new MaredRepeatCommand(count, body);
    }

    private static MaredScriptCommand parseFor(List<String> tokens, String[] lines, int[] pos, int lineNumber) {
        if (tokens.size() < 6) throw new ParseException(lineNumber, "for: expected 'for $i = a to b {'");
        if (!tokens.get(2).equals("=")) throw new ParseException(lineNumber, "for: expected '=' after name");
        if (!tokens.get(4).equalsIgnoreCase("to")) throw new ParseException(lineNumber, "for: expected 'to'");
        if (findBraceAfter(lines[pos[0] - 1]) < 0) throw new ParseException(lineNumber, "for: expected '{'");

        String var = tokens.get(1);
        if (var.startsWith("$")) var = var.substring(1);
        String from = tokens.get(3);
        String to = tokens.get(5);

        List<MaredScriptCommand> body = parseBlock(lines, pos, lineNumber, true);
        return new MaredForCommand(var, from, to, body);
    }

    private static MaredScriptCommand parseWhile(List<String> tokens, String[] lines, int[] pos, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "while: missing condition");
        if (findBraceAfter(lines[pos[0] - 1]) < 0) throw new ParseException(lineNumber, "while: expected '{'");
        String condition = join(tokens.subList(1, tokens.size()));
        List<MaredScriptCommand> body = parseBlock(lines, pos, lineNumber, true);
        return new MaredWhileCommand(condition, body);
    }

    private static MaredScriptCommand parseFunc(List<String> tokens, String[] lines, int[] pos, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "func: missing name");
        String nameToken = tokens.get(1);
        String name;
        List<String> params = new ArrayList<>();
        int paren = nameToken.indexOf('(');
        if (paren >= 0 && nameToken.endsWith(")")) {
            name = nameToken.substring(0, paren);
            String inside = nameToken.substring(paren + 1, nameToken.length() - 1).trim();
            if (!inside.isEmpty()) {
                for (String p : inside.split(",")) {
                    String pn = p.trim();
                    if (pn.startsWith("$")) pn = pn.substring(1);
                    params.add(pn);
                }
            }
        } else {
            name = nameToken;
        }
        if (findBraceAfter(lines[pos[0] - 1]) < 0) throw new ParseException(lineNumber, "func: expected '{'");
        List<MaredScriptCommand> body = parseBlock(lines, pos, lineNumber, true);
        return new MaredFuncCommand(name, params, body);
    }

    private static MaredScriptCommand parseBind(List<String> tokens, String[] lines, int[] pos, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "bind: missing key");

        String keyRaw = null;
        MaredBindCommand.Mode mode = MaredBindCommand.Mode.DEFAULT;
        boolean blockVanilla = false;

        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            String low = t.toLowerCase();
            switch (low) {
                case "add"     -> mode = MaredBindCommand.Mode.ADD;
                case "replace" -> mode = MaredBindCommand.Mode.REPLACE;
                case "clear"   -> mode = MaredBindCommand.Mode.CLEAR;
                case "block"   -> blockVanilla = true;
                default -> {
                    if (keyRaw == null) keyRaw = t;
                    else throw new ParseException(lineNumber, "bind: extra argument: " + t);
                }
            }
        }
        if (keyRaw == null) throw new ParseException(lineNumber, "bind: missing key");

        MaredKeyNames.ParsedKey key = MaredKeyNames.parse(keyRaw);
        if (key == null) throw new ParseException(lineNumber, "bind: unknown key: " + keyRaw);

        if (findBraceAfter(lines[pos[0] - 1]) < 0) {
            throw new ParseException(lineNumber, "bind: expected '{' after arguments");
        }

        List<MaredScriptCommand> body = parseBlock(lines, pos, lineNumber, true);
        if (mode == MaredBindCommand.Mode.CLEAR && !body.isEmpty()) {
            throw new ParseException(lineNumber, "bind clear: body must be empty");
        }
        return new MaredBindCommand(keyRaw, key, mode, blockVanilla, body);
    }

    private static MaredScriptCommand parseLine(List<String> tokens, int lineNumber) {
        String command = tokens.get(0);
        switch (command) {
            case "say":    return parseSay(tokens, lineNumber);
            case "wait":   return parseWait(tokens, lineNumber);
            case "delay":  return parseWait(tokens, lineNumber);
            case "give":   return parseGive(tokens, lineNumber);
            case "set":    return parseSet(tokens, lineNumber);
            case "array":  return parseArray(tokens, lineNumber);
            case "call":   return parseCall(tokens, lineNumber);
            case "log":    return parseLog(tokens, lineNumber);
            case "debug":  return parseDebug(tokens, lineNumber);
            case "assert": return parseAssert(tokens, lineNumber);
            default:
                String fullLine = join(tokens);
                return new MaredEvalCommand(fullLine);
        }
    }

    private static MaredScriptCommand parseSay(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "say: missing text");
        StringBuilder raw = new StringBuilder();
        for (int i = 1; i < tokens.size(); i++) {
            if (i > 1) raw.append(' ');
            raw.append(tokens.get(i));
        }
        String textWithArgs = raw.toString();

        String scope = "all";
        int scopeIdx = textWithArgs.lastIndexOf(" scope=");
        if (scopeIdx >= 0) {
            scope = textWithArgs.substring(scopeIdx + " scope=".length()).trim();
            textWithArgs = textWithArgs.substring(0, scopeIdx).trim();
        }
        return new MaredSayCommand(textWithArgs, scope);
    }

    private static MaredScriptCommand parseWait(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "wait: missing amount");
        double amount;
        try { amount = Double.parseDouble(tokens.get(1)); }
        catch (NumberFormatException e) { throw new ParseException(lineNumber, "wait: invalid number: " + tokens.get(1)); }
        String unit = tokens.size() >= 3 ? tokens.get(2) : "seconds";
        return new MaredWaitCommand(amount, unit);
    }

    private static MaredScriptCommand parseGive(List<String> tokens, int lineNumber) {
        if (tokens.size() < 3) throw new ParseException(lineNumber, "give: missing target or item");
        String target = tokens.get(1);
        String item = tokens.get(2);
        int count = 1;
        if (tokens.size() >= 4) {
            try { count = Integer.parseInt(tokens.get(3)); }
            catch (NumberFormatException e) { throw new ParseException(lineNumber, "give: invalid count: " + tokens.get(3)); }
        }
        return new MaredGiveCommand(target, item, count);
    }

    private static MaredScriptCommand parseSet(List<String> tokens, int lineNumber) {
        if (tokens.size() < 4) throw new ParseException(lineNumber, "set: expected 'set name = value'");
        String name = tokens.get(1);
        if (name.startsWith("$")) name = name.substring(1);
        if (!tokens.get(2).equals("=")) {
            throw new ParseException(lineNumber, "set: expected '=' after name");
        }
        StringBuilder value = new StringBuilder();
        for (int i = 3; i < tokens.size(); i++) {
            if (i > 3) value.append(' ');
            value.append(tokens.get(i));
        }
        return new MaredSetCommand(name, value.toString());
    }

    private static MaredScriptCommand parseArray(List<String> tokens, int lineNumber) {
        if (tokens.size() < 4) throw new ParseException(lineNumber, "array: expected 'array name = [values]'");
        String name = tokens.get(1);
        if (name.startsWith("$")) name = name.substring(1);
        if (!tokens.get(2).equals("=")) {
            throw new ParseException(lineNumber, "array: expected '=' after name");
        }
        StringBuilder value = new StringBuilder();
        for (int i = 3; i < tokens.size(); i++) {
            if (i > 3) value.append(' ');
            value.append(tokens.get(i));
        }
        return new MaredSetCommand(name, value.toString());
    }

    private static MaredScriptCommand parseCall(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "call: missing name");
        String full = join(tokens.subList(1, tokens.size()));
        String name;
        List<String> args = new ArrayList<>();
        int paren = full.indexOf('(');
        if (paren >= 0 && full.endsWith(")")) {
            name = full.substring(0, paren).trim();
            String inside = full.substring(paren + 1, full.length() - 1).trim();
            if (!inside.isEmpty()) {
                for (String a : inside.split(",")) args.add(a.trim());
            }
        } else {
            name = full.trim();
        }
        return new MaredCallCommand(name, args);
    }

    private static MaredScriptCommand parseLog(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "log: missing text");
        String text = join(tokens.subList(1, tokens.size()));
        return new MaredLogCommand(text);
    }

    private static MaredScriptCommand parseDebug(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "debug: missing expression");
        String expr = join(tokens.subList(1, tokens.size()));
        return new MaredDebugCommand(expr);
    }

    private static MaredScriptCommand parseAssert(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "assert: missing condition");
        List<String> condTokens = tokens.subList(1, tokens.size());
        String message = null;

        if (!condTokens.isEmpty()) {
            String last = condTokens.get(condTokens.size() - 1);
            if (last.startsWith("\"") && last.endsWith("\"")) {
                message = stripQuotes(last);
                condTokens = condTokens.subList(0, condTokens.size() - 1);
            }
        }
        String cond = join(condTokens);
        return new MaredAssertCommand(cond, message);
    }

    private static int findBraceAfter(String line) {
        boolean inString = false;
        int braceDepth = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inString && c == '$' && i + 1 < line.length() && line.charAt(i + 1) == '{') {
                braceDepth++;
                i++;
                continue;
            }
            if (!inString && c == '}' && braceDepth > 0) { braceDepth--; continue; }
            if (c == '"' && braceDepth == 0) inString = !inString;
            if (!inString && braceDepth == 0 && c == '{') return i;
        }
        return -1;
    }

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int paren = 0;
        int braceDepth = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inString && c == '$' && i + 1 < line.length() && line.charAt(i + 1) == '{') {
                braceDepth++;
                current.append(c);
                current.append('{');
                i++;
                continue;
            }
            if (!inString && c == '}' && braceDepth > 0) {
                braceDepth--;
                current.append(c);
                continue;
            }
            if (c == '"' && braceDepth == 0) {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (c == '(' && !inString && braceDepth == 0) { paren++; current.append(c); continue; }
            if (c == ')' && !inString && braceDepth == 0) { paren--; current.append(c); continue; }
            if (c == ' ' && !inString && paren == 0 && braceDepth == 0) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private static String stripComment(String line) {
        boolean inString = false;
        int braceDepth = 0;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (!inString && c == '$' && i + 1 < line.length() && line.charAt(i + 1) == '{') {
                braceDepth++;
                i++;
                continue;
            }
            if (!inString && c == '}' && braceDepth > 0) { braceDepth--; continue; }
            if (c == '"' && braceDepth == 0) inString = !inString;
            if (!inString && braceDepth == 0 && c == '/' && line.charAt(i + 1) == '/') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String join(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }
}