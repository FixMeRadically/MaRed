package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.script.commands.MaredAssertCommand;
import com.fixmer.mared.script.commands.MaredBindCommand;
import com.fixmer.mared.script.commands.MaredBlockCommand;
import com.fixmer.mared.script.commands.MaredBreakCommand;
import com.fixmer.mared.script.commands.MaredCallCommand;
import com.fixmer.mared.script.commands.MaredContinueCommand;
import com.fixmer.mared.script.commands.MaredDebugCommand;
import com.fixmer.mared.script.commands.MaredEvalCommand;
import com.fixmer.mared.script.commands.MaredForCommand;
import com.fixmer.mared.script.commands.MaredForInCommand;
import com.fixmer.mared.script.commands.MaredFuncCommand;
import com.fixmer.mared.script.commands.MaredGiveCommand;
import com.fixmer.mared.script.commands.MaredIfCommand;
import com.fixmer.mared.script.commands.MaredLogCommand;
import com.fixmer.mared.script.commands.MaredMcCommand;
import com.fixmer.mared.script.commands.MaredOnCommand;
import com.fixmer.mared.script.commands.MaredRepeatCommand;
import com.fixmer.mared.script.commands.MaredReturnCommand;
import com.fixmer.mared.script.commands.MaredSayCommand;
import com.fixmer.mared.script.commands.MaredScriptCommand;
import com.fixmer.mared.script.commands.MaredSetCommand;
import com.fixmer.mared.script.commands.MaredSetFromCallCommand;
import com.fixmer.mared.script.commands.MaredToggleCommand;
import com.fixmer.mared.script.commands.MaredUnblockCommand;
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

        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && coversWhole(trimmed)) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        Cursor cur = new Cursor(trimmed);
        List<MaredScriptCommand> result = parseStatements(cur, 1);
        cur.skipWhitespaceAndNewlines();
        if (!cur.eof()) {
            throw new ParseException(cur.line(), "unexpected text: " + cur.peekChar());
        }
        return result;
    }

    private static boolean coversWhole(String s) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString && c == '\\' && i + 1 < s.length()) { i++; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0 && i < s.length() - 1) {
                    String rest = s.substring(i + 1).trim();
                    if (!rest.isEmpty()) return false;
                }
            }
        }
        return depth == 0;
    }

    private static List<MaredScriptCommand> parseStatements(Cursor cur, int line) {
        List<MaredScriptCommand> commands = new ArrayList<>();
        while (true) {
            cur.skipSeparators();
            if (cur.eof()) break;
            if (cur.peekChar() == '}') break;
            MaredScriptCommand cmd = parseStatement(cur);
            if (cmd != null) commands.add(cmd);
        }
        return commands;
    }

    private static MaredScriptCommand parseStatement(Cursor cur) {
        int startLine = cur.line();
        String head = cur.readHead();
        String headTrim = head.trim();
        if (headTrim.isEmpty()) return null;

        List<String> tokens = tokenize(headTrim);
        if (tokens.isEmpty()) return null;

        String cmd = tokens.get(0);
        switch (cmd) {
            case "if":     return parseIfChain(cur, tokens, startLine);
            case "repeat": return parseRepeat(cur, tokens, startLine);
            case "for":    return parseFor(cur, tokens, startLine);
            case "while":  return parseWhile(cur, tokens, startLine);
            case "func":   return parseFunc(cur, tokens, startLine);
            case "bind":   return parseBind(cur, tokens, startLine);
            case "on":     return parseOn(cur, tokens, startLine);
            case "break":  return new MaredBreakCommand();
            case "continue": return new MaredContinueCommand();
            case "return": return parseReturn(tokens, startLine);
            default: return parseLine(tokens, startLine);
        }
    }

    private static MaredScriptCommand parseIfChain(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "if: missing condition");
        String condition = join(tokens.subList(1, tokens.size()));
        List<MaredScriptCommand> thenBody = parseBody(cur, line);

        List<String> elifConds = new ArrayList<>();
        List<List<MaredScriptCommand>> elifBodies = new ArrayList<>();
        List<MaredScriptCommand> elseBody = null;

        while (true) {
            cur.skipSeparators();
            if (cur.eof()) break;
            if (cur.peekChar() == '}') break;

            int saveLine = cur.line();
            String saveHead = cur.readHead();
            String saveTrim = saveHead.trim();
            if (saveTrim.isEmpty()) break;

            List<String> saveTokens = tokenize(saveTrim);
            if (saveTokens.isEmpty()) break;

            String kw = saveTokens.get(0);
            if ("elif".equals(kw)) {
                if (saveTokens.size() < 2) throw new ParseException(saveLine, "elif: missing condition");
                String cond = join(saveTokens.subList(1, saveTokens.size()));
                List<MaredScriptCommand> body = parseBody(cur, saveLine);
                elifConds.add(cond);
                elifBodies.add(body);
                continue;
            }
            if ("else".equals(kw)) {
                elseBody = parseBody(cur, saveLine);
                break;
            }
            cur.rewindHead(saveHead);
            break;
        }
        return new MaredIfCommand(condition, thenBody, elifConds, elifBodies, elseBody);
    }

    private static MaredScriptCommand parseRepeat(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "repeat: missing count");
        String countStr = tokens.get(1);
        if (tokens.size() >= 3 && !"times".equalsIgnoreCase(tokens.get(2)))
            throw new ParseException(line, "repeat: expected 'times' or end of line");
        int count;
        try { count = Integer.parseInt(countStr); }
        catch (NumberFormatException e) { throw new ParseException(line, "repeat: '" + countStr + "' is not a number"); }
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredRepeatCommand(count, body);
    }

    private static MaredScriptCommand parseFor(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 4) throw new ParseException(line, "for: expected 'for $i = a to b' or 'for $item in $items'");
        String var = tokens.get(1);
        if (var.startsWith("$")) var = var.substring(1);

        if (tokens.get(2).equalsIgnoreCase("in")) {
            if (tokens.size() < 4) throw new ParseException(line, "for-in: missing array name");
            String arrName = tokens.get(3);
            if (arrName.startsWith("$")) arrName = arrName.substring(1);
            List<MaredScriptCommand> body = parseBody(cur, line);
            return new MaredForInCommand(var, arrName, body);
        }

        if (tokens.size() < 6) throw new ParseException(line, "for: expected 'for $i = a to b {'");
        if (!tokens.get(2).equals("=")) throw new ParseException(line, "for: expected '=' after name");
        if (!tokens.get(4).equalsIgnoreCase("to")) throw new ParseException(line, "for: expected 'to'");

        String from = tokens.get(3);
        String to = tokens.get(5);
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredForCommand(var, from, to, body);
    }

    private static MaredScriptCommand parseWhile(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "while: missing condition");
        String condition = join(tokens.subList(1, tokens.size()));
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredWhileCommand(condition, body);
    }

    private static MaredScriptCommand parseFunc(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "func: missing name");
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
        } else name = nameToken;
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredFuncCommand(name, params, body);
    }

    private static MaredScriptCommand parseBind(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "bind: missing key");

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
                case "hold"    -> mode = MaredBindCommand.Mode.HOLD;
                case "release" -> mode = MaredBindCommand.Mode.RELEASE;
                default -> {
                    if (keyRaw == null) keyRaw = t;
                    else throw new ParseException(line, "bind: extra argument: " + t);
                }
            }
        }
        if (keyRaw == null) throw new ParseException(line, "bind: missing key");

        MaredKeyNames.ParsedKey key = MaredKeyNames.parseAny(keyRaw);
        if (key == null) throw new ParseException(line, "bind: unknown key: " + keyRaw);

        cur.skipWhitespaceAndNewlines();
        boolean hasBrace = !cur.eof() && cur.peekChar() == '{';

        List<MaredScriptCommand> body;
        if (!hasBrace) {
            if (mode == MaredBindCommand.Mode.CLEAR || blockVanilla) body = new ArrayList<>();
            else throw new ParseException(line, "bind: expected '{' after arguments (or use 'block'/'clear')");
        } else body = parseBody(cur, line);

        if (mode == MaredBindCommand.Mode.CLEAR && !body.isEmpty())
            throw new ParseException(line, "bind clear: body must be empty");
        return new MaredBindCommand(keyRaw, key, mode, blockVanilla, body);
    }

    private static MaredScriptCommand parseOn(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "on: missing event type");
        String eventType = tokens.get(1).toLowerCase();
        if (!isKnownEvent(eventType)) throw new ParseException(line, "on: unknown event: " + eventType);

        boolean replace = true;
        if (tokens.size() >= 3) {
            String mode = tokens.get(2).toLowerCase();
            if ("add".equals(mode)) replace = false;
            else if ("replace".equals(mode)) replace = true;
            else throw new ParseException(line, "on: unknown mode: " + mode + " (use 'add' or 'replace')");
        }
        if (tokens.size() > 3) throw new ParseException(line, "on: extra arguments after event type");

        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredOnCommand(eventType, body, replace);
    }

    private static boolean isKnownEvent(String type) {
        switch (type) {
            case "right_click": case "left_click": case "middle_click":
            case "scroll_up": case "scroll_down":
            case "key_press": case "key_release":
            case "chat": case "tick_client": case "join": case "leave":
                return true;
            default: return false;
        }
    }

    private static MaredScriptCommand parseReturn(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) return new MaredReturnCommand("");
        StringBuilder expr = new StringBuilder();
        for (int i = 1; i < tokens.size(); i++) {
            if (i > 1) expr.append(' ');
            expr.append(tokens.get(i));
        }
        return new MaredReturnCommand(expr.toString());
    }

    private static List<MaredScriptCommand> parseBody(Cursor cur, int line) {
        cur.skipWhitespaceAndNewlines();
        if (cur.eof() || cur.peekChar() != '{') return new ArrayList<>();
        cur.next();
        List<MaredScriptCommand> body = parseStatements(cur, line);
        cur.skipSeparators();
        if (cur.eof() || cur.peekChar() != '}') throw new ParseException(line, "block '{' not closed");
        cur.next();
        return body;
    }

    private static MaredScriptCommand parseLine(List<String> tokens, int lineNumber) {
        String command = tokens.get(0);
        switch (command) {
            case "say":     return parseSay(tokens, lineNumber);
            case "wait":    return parseWait(tokens, lineNumber);
            case "delay":   return parseWait(tokens, lineNumber);
            case "give":    return parseGive(tokens, lineNumber);
            case "set":     return parseSet(tokens, lineNumber);
            case "array":   return parseArray(tokens, lineNumber);
            case "call":    return parseCall(tokens, lineNumber);
            case "log":     return parseLog(tokens, lineNumber);
            case "debug":   return parseDebug(tokens, lineNumber);
            case "assert":  return parseAssert(tokens, lineNumber);
            case "unblock": return parseUnblock(tokens, lineNumber);
            case "block":   return parseBlockCmd(tokens, lineNumber);
            case "toggle":  return parseToggle(tokens, lineNumber);
            case "mc":      return parseMc(tokens, lineNumber);
            default: return new MaredEvalCommand(join(tokens));
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
        if (!tokens.get(2).equals("=")) throw new ParseException(lineNumber, "set: expected '=' after name");

        if (tokens.size() >= 5 && "call".equalsIgnoreCase(tokens.get(3))) {
            String callPart = join(tokens.subList(4, tokens.size()));
            int paren = callPart.indexOf('(');
            if (paren < 0 || !callPart.endsWith(")")) throw new ParseException(lineNumber, "set: expected 'call func(args)'");
            String funcName = callPart.substring(0, paren).trim();
            String inside = callPart.substring(paren + 1, callPart.length() - 1).trim();
            List<String> callArgs = new ArrayList<>();
            if (!inside.isEmpty()) for (String a : inside.split(",")) callArgs.add(a.trim());
            return new MaredSetFromCallCommand(name, funcName, callArgs);
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
        if (!tokens.get(2).equals("=")) throw new ParseException(lineNumber, "array: expected '=' after name");
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
            if (!inside.isEmpty()) for (String a : inside.split(",")) args.add(a.trim());
        } else name = full.trim();
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

        if (condTokens.size() >= 2) {
            String last = condTokens.get(condTokens.size() - 1);
            String beforeLast = condTokens.get(condTokens.size() - 2);
            boolean lastIsString = last.startsWith("\"") && last.endsWith("\"");
            boolean beforeIsOperator = isComparisonOrLogicalOperator(beforeLast);
            if (lastIsString && !beforeIsOperator) {
                message = stripQuotes(last);
                condTokens = condTokens.subList(0, condTokens.size() - 1);
            }
        }
        String cond = join(condTokens);
        return new MaredAssertCommand(cond, message);
    }

    private static boolean isComparisonOrLogicalOperator(String s) {
        switch (s) {
            case "==": case "!=": case "<": case ">": case "<=": case ">=":
            case "&&": case "||": case "contains":
                return true;
            default: return false;
        }
    }

    private static MaredScriptCommand parseUnblock(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "unblock: missing key");
        String keyRaw = tokens.get(1);
        if (MaredKeyNames.parseAny(keyRaw) == null) throw new ParseException(lineNumber, "unblock: unknown key: " + keyRaw);
        return new MaredUnblockCommand(keyRaw);
    }

    private static MaredScriptCommand parseBlockCmd(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "block: missing key");
        String keyRaw = tokens.get(1);
        if (MaredKeyNames.parseAny(keyRaw) == null) throw new ParseException(lineNumber, "block: unknown key: " + keyRaw);
        return new MaredBlockCommand(keyRaw);
    }

    private static MaredScriptCommand parseToggle(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "toggle: missing key");
        String keyRaw = tokens.get(1);
        if (MaredKeyNames.parseAny(keyRaw) == null) throw new ParseException(lineNumber, "toggle: unknown key: " + keyRaw);
        return new MaredToggleCommand(keyRaw);
    }

    private static MaredScriptCommand parseMc(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "mc: missing command");
        String cmd = join(tokens.subList(1, tokens.size()));
        return new MaredMcCommand(cmd);
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
                braceDepth++; current.append(c); current.append('{'); i++; continue;
            }
            if (!inString && c == '}' && braceDepth > 0) { braceDepth--; current.append(c); continue; }
            if (c == '\\' && inString && i + 1 < line.length()) {
                current.append(c); current.append(line.charAt(i + 1)); i++; continue;
            }
            if (c == '"' && braceDepth == 0) { inString = !inString; current.append(c); continue; }
            if (c == '(' && !inString && braceDepth == 0) { paren++; current.append(c); continue; }
            if (c == ')' && !inString && braceDepth == 0) { paren--; current.append(c); continue; }
            if ((c == ' ' || c == '\t') && !inString && paren == 0 && braceDepth == 0) {
                if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
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

    private static final class Cursor {
        private final String text;
        private int pos;
        private int line;
        private int lastHeadStart;

        Cursor(String text) { this.text = text; this.pos = 0; this.line = 1; this.lastHeadStart = 0; }

        boolean eof() { return pos >= text.length(); }
        int line() { return line; }
        char peekChar() { return text.charAt(pos); }

        char next() {
            char c = text.charAt(pos++);
            if (c == '\n') line++;
            return c;
        }

        void skipWhitespaceAndNewlines() {
            while (!eof()) {
                char c = peekChar();
                if (c == ' ' || c == '\t' || c == '\n') next();
                else break;
            }
        }

        void skipSeparators() {
            while (!eof()) {
                char c = peekChar();
                if (c == ' ' || c == '\t' || c == '\n' || c == ';') { next(); continue; }
                if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                    while (!eof() && peekChar() != '\n') next();
                    continue;
                }
                break;
            }
        }

        String readHead() {
            lastHeadStart = pos;
            StringBuilder sb = new StringBuilder();
            boolean inString = false;
            int braceDepth = 0;

            while (!eof()) {
                char c = peekChar();
                if (!inString && c == '$' && pos + 1 < text.length() && text.charAt(pos + 1) == '{') {
                    braceDepth++; sb.append(next()); sb.append(next()); continue;
                }
                if (!inString && c == '}' && braceDepth > 0) { braceDepth--; sb.append(next()); continue; }
                if (c == '\\' && inString && pos + 1 < text.length()) {
                    sb.append(next()); sb.append(next()); continue;
                }
                if (c == '"' && braceDepth == 0) { inString = !inString; sb.append(next()); continue; }
                if (!inString && braceDepth == 0) {
                    if (c == '{' || c == '}' || c == ';' || c == '\n') break;
                    if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') break;
                }
                sb.append(next());
            }
            return sb.toString();
        }

        void rewindHead(String head) {
            for (int i = 0; i < head.length(); i++) {
                if (pos > 0) {
                    pos--;
                    if (text.charAt(pos) == '\n') line--;
                }
            }
        }
    }
}