package com.fixmer.mared.commands.engine;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.commands.actions.MaredAttackCommand;
import com.fixmer.mared.commands.actions.MaredDropCommand;
import com.fixmer.mared.commands.actions.MaredJumpCommand;
import com.fixmer.mared.commands.actions.MaredLookAtCommand;
import com.fixmer.mared.commands.actions.MaredLookCommand;
import com.fixmer.mared.commands.actions.MaredMoveCommand;
import com.fixmer.mared.commands.actions.MaredSelectSlotCommand;
import com.fixmer.mared.commands.actions.MaredStopCommand;
import com.fixmer.mared.commands.actions.MaredSwapCommand;
import com.fixmer.mared.commands.actions.MaredUseCommand;
import com.fixmer.mared.commands.control.MaredBreakCommand;
import com.fixmer.mared.commands.control.MaredCallCommand;
import com.fixmer.mared.commands.control.MaredContinueCommand;
import com.fixmer.mared.commands.control.MaredExitCommand;
import com.fixmer.mared.commands.control.MaredForCommand;
import com.fixmer.mared.commands.control.MaredForInCommand;
import com.fixmer.mared.commands.control.MaredFuncCommand;
import com.fixmer.mared.commands.control.MaredIfCommand;
import com.fixmer.mared.commands.control.MaredRepeatCommand;
import com.fixmer.mared.commands.control.MaredReturnCommand;
import com.fixmer.mared.commands.control.MaredSetFromCallCommand;
import com.fixmer.mared.commands.control.MaredWhileCommand;
import com.fixmer.mared.commands.data.MaredSetCommand;
import com.fixmer.mared.commands.events_cmd.MaredAfterCommand;
import com.fixmer.mared.commands.events_cmd.MaredEveryCommand;
import com.fixmer.mared.commands.events_cmd.MaredFirstJoinCommand;
import com.fixmer.mared.commands.events_cmd.MaredOffCommand;
import com.fixmer.mared.commands.events_cmd.MaredOnceCommand;
import com.fixmer.mared.commands.events_cmd.MaredOnCommand;
import com.fixmer.mared.commands.events_cmd.MaredWaitUntilCommand;
import com.fixmer.mared.commands.input.MaredKeyNames;
import com.fixmer.mared.commands.input_cmd.MaredBindCommand;
import com.fixmer.mared.commands.input_cmd.MaredBlockCommand;
import com.fixmer.mared.commands.input_cmd.MaredToggleCommand;
import com.fixmer.mared.commands.input_cmd.MaredUnblockCommand;
import com.fixmer.mared.commands.server_cmd.MaredAssertCommand;
import com.fixmer.mared.commands.server_cmd.MaredDebugCommand;
import com.fixmer.mared.commands.server_cmd.MaredEvalCommand;
import com.fixmer.mared.commands.server_cmd.MaredGiveCommand;
import com.fixmer.mared.commands.server_cmd.MaredLogCommand;
import com.fixmer.mared.commands.server_cmd.MaredMcCommand;
import com.fixmer.mared.commands.server_cmd.MaredPrintCommand;
import com.fixmer.mared.commands.server_cmd.MaredSayCommand;
import com.fixmer.mared.commands.server_cmd.MaredWaitCommand;

public final class MaredScriptParser {

    private MaredScriptParser() {}

    private static final int MAX_STUCK_ITERATIONS = 100;

    public static class ParseException extends RuntimeException {
        public final int line;
        public ParseException(int line, String message) {
            super("Line " + line + ": " + message);
            this.line = line;
        }
    }

    // ============================================================
    //  Публичный вход
    // ============================================================

    public static List<MaredScriptCommand> parse(String text) {
        if (text == null) return new ArrayList<>(0);
        text = text.replace("\r", "");

        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && coversWhole(trimmed)) {
            int opening = text.indexOf('{'), closing = text.lastIndexOf('}');
            text = text.substring(0, opening) + " " + text.substring(opening + 1, closing)
                + " " + text.substring(closing + 1);
        }

        Cursor cur = new Cursor(text);
        List<MaredScriptCommand> result = parseStatements(cur, 1);
        cur.skipSeparators();
        if (!cur.eof()) throw new ParseException(cur.line(), "unexpected text: " + cur.peekChar());
        return result;
    }

    private static boolean coversWhole(String s) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') { i++; continue; }
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '/' && i+1 < s.length() && s.charAt(i+1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0 && i != s.length()-1) return false;
        }
        return depth == 0 && quote == 0;
    }

    // ============================================================
    //  Statements
    // ============================================================

    private static List<MaredScriptCommand> parseStatements(Cursor cur, int line) {
        if (++cur.depth > 128) {
            --cur.depth;
            throw new ParseException(cur.line(), "maximum block nesting exceeded (128)");
        }
        try { return parseStatementsBody(cur, line); }
        finally { --cur.depth; }
    }

    private static List<MaredScriptCommand> parseStatementsBody(Cursor cur, int line) {
        List<MaredScriptCommand> commands = new ArrayList<>(8);
        int lastPos = -1;
        int stuckCount = 0;

        while (true) {
            cur.skipSeparators();
            if (cur.eof()) break;
            if (cur.peekChar() == '}') break;

            int currentPos = cur.getPos();
            if (currentPos == lastPos) {
                stuckCount++;
                if (stuckCount >= MAX_STUCK_ITERATIONS) {
                    String remaining = cur.getRemaining(200);
                    throw new ParseException(cur.line(),
                        "parser stuck at pos " + currentPos + " — remaining: [" + remaining + "]");
                }
            } else {
                stuckCount = 0;
                lastPos = currentPos;
            }

            // { ... } на верхнем уровне — группировка
            if (cur.peekChar() == '{') {
                int blockLine = cur.line();
                cur.next();
                List<MaredScriptCommand> blockBody = parseStatements(cur, blockLine);
                cur.skipSeparators();
                if (cur.eof() || cur.peekChar() != '}') {
                    throw new ParseException(blockLine, "block '{' not closed");
                }
                cur.next();
                if (!blockBody.isEmpty()) commands.addAll(blockBody);
                continue;
            }

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
            // Управление
            case "if":         return parseIfChain(cur, tokens, startLine);
            case "repeat":     return parseRepeat(cur, tokens, startLine);
            case "for":        return parseFor(cur, tokens, startLine);
            case "while":      return parseWhile(cur, tokens, startLine);
            case "func":       return parseFunc(cur, tokens, startLine);
            case "break":      return new MaredBreakCommand();
            case "continue":   return new MaredContinueCommand();
            case "return":     return parseReturn(tokens, startLine);
            case "exit":       return new MaredExitCommand();

            // Ввод
            case "bind":       return parseBind(cur, tokens, startLine);
            case "on":         return parseOn(cur, tokens, startLine);
            case "off":        return parseOff(tokens, startLine);

            // Триггеры времени
            case "every":      return parseEvery(cur, tokens, startLine);
            case "after":      return parseAfter(cur, tokens, startLine);
            case "wait_until": return parseWaitUntil(cur, tokens, startLine);
            case "once":       return parseOnce(cur, tokens, startLine);
            case "first_join": return parseFirstJoin(cur, tokens, startLine);

            // Actions
            case "look_at":    return parseLookAt(tokens, startLine);
            case "look":       return parseLook(tokens, startLine);
            case "move":       return parseMove(tokens, startLine);
            case "stop":       return new MaredStopCommand();
            case "jump":       return new MaredJumpCommand();
            case "attack":     return new MaredAttackCommand();
            case "use":        return new MaredUseCommand();
            case "drop":       return new MaredDropCommand();
            case "swap_hands": return new MaredSwapCommand();
            case "select_slot":return parseSelectSlot(tokens, startLine);

            // Строки/сервер
            case "say":        return parseSay(tokens, startLine);
            case "print":      return parsePrint(tokens, startLine);
            case "wait": case "delay": return parseWait(tokens, startLine);
            case "give":       return parseGive(tokens, startLine);
            case "set":        return parseSet(tokens, startLine);
            case "array":      return parseArray(tokens, startLine);
            case "call":       return parseCall(tokens, startLine);
            case "log":        return parseLog(tokens, startLine);
            case "debug":      return parseDebug(tokens, startLine);
            case "assert":     return parseAssert(tokens, startLine);
            case "unblock":    return parseUnblock(tokens, startLine);
            case "block":      return parseBlockCmd(tokens, startLine);
            case "toggle":     return parseToggle(tokens, startLine);
            case "mc":         return parseMc(headTrim, startLine);

            default: return new MaredEvalCommand(join(tokens));
        }
    }

    // ============================================================
    //  Control / chains
    // ============================================================

    private static MaredScriptCommand parseIfChain(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "if: missing condition");
        String condition = join(tokens.subList(1, tokens.size()));
        List<MaredScriptCommand> thenBody = parseBody(cur, line);

        List<String> elifConds = new ArrayList<>(2);
        List<List<MaredScriptCommand>> elifBodies = new ArrayList<>(2);
        List<MaredScriptCommand> elseBody = null;

        while (true) {
            cur.skipSeparators();
            if (cur.eof() || cur.peekChar() == '}') break;

            int saveLine = cur.line();
            int savePos = cur.getPos();
            String saveHead = cur.readHead();
            String saveTrim = saveHead.trim();
            if (saveTrim.isEmpty()) break;

            List<String> saveTokens = tokenize(saveTrim);
            if (saveTokens.isEmpty()) break;

            String kw = saveTokens.get(0);
            if ("elif".equals(kw)) {
                if (saveTokens.size() < 2) throw new ParseException(saveLine, "elif: missing condition");
                elifConds.add(join(saveTokens.subList(1, saveTokens.size())));
                elifBodies.add(parseBody(cur, saveLine));
                continue;
            }
            if ("else".equals(kw)) {
                elseBody = parseBody(cur, saveLine);
                break;
            }
            cur.seekTo(savePos);
            break;
        }
        return new MaredIfCommand(condition, thenBody, elifConds, elifBodies, elseBody);
    }

    private static MaredScriptCommand parseRepeat(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "repeat: missing count");

        int end = tokens.size();
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i).equalsIgnoreCase("times")) { end = i; break; }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < end; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        if (sb.length() == 0) throw new ParseException(line, "repeat: empty count expression");

        return new MaredRepeatCommand(sb.toString(), parseBody(cur, line));
    }

    private static MaredScriptCommand parseFor(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 4)
            throw new ParseException(line, "for: expected 'for $i = a to b' or 'for $item in $items'");

        String var = tokens.get(1);
        if (var.startsWith("$")) var = var.substring(1);

        if (tokens.get(2).equalsIgnoreCase("in")) {
            if (tokens.size() < 4) throw new ParseException(line, "for-in: missing array name");
            String arrName = tokens.get(3);
            if (arrName.startsWith("$")) arrName = arrName.substring(1);
            return new MaredForInCommand(var, arrName, parseBody(cur, line));
        }

        if (!tokens.get(2).equals("="))
            throw new ParseException(line, "for: expected '=' after name");

        int toIdx = -1;
        for (int i = 3; i < tokens.size(); i++) {
            if (tokens.get(i).equalsIgnoreCase("to")) { toIdx = i; break; }
        }
        if (toIdx < 0) throw new ParseException(line, "for: missing 'to'");

        StringBuilder fromSb = new StringBuilder();
        for (int i = 3; i < toIdx; i++) {
            if (fromSb.length() > 0) fromSb.append(' ');
            fromSb.append(tokens.get(i));
        }
        StringBuilder toSb = new StringBuilder();
        for (int i = toIdx + 1; i < tokens.size(); i++) {
            if (toSb.length() > 0) toSb.append(' ');
            toSb.append(tokens.get(i));
        }

        if (fromSb.length() == 0 || toSb.length() == 0)
            throw new ParseException(line, "for: empty from/to expression");

        return new MaredForCommand(var, fromSb.toString(), toSb.toString(), parseBody(cur, line));
    }

    private static MaredScriptCommand parseWhile(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "while: missing condition");
        return new MaredWhileCommand(join(tokens.subList(1, tokens.size())), parseBody(cur, line));
    }

    private static MaredScriptCommand parseFunc(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "func: missing name");
        String nameToken = tokens.get(1);
        String name;
        List<String> params = new ArrayList<>(2);
        int paren = nameToken.indexOf('(');
        if (paren >= 0 && nameToken.endsWith(")")) {
            name = nameToken.substring(0, paren);
            String inside = nameToken.substring(paren + 1, nameToken.length() - 1).trim();
            if (!inside.isEmpty()) {
                for (String p : inside.split(",")) {
                    String pn = p.trim();
                    if (pn.startsWith("$")) pn = pn.substring(1);
                    if (!pn.isEmpty()) params.add(pn);
                }
            }
        } else name = nameToken;
        return new MaredFuncCommand(name, params, parseBody(cur, line));
    }

    private static MaredScriptCommand parseReturn(List<String> tokens, int line) {
        if (tokens.size() < 2) return new MaredReturnCommand("");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < tokens.size(); i++) {
            if (i > 1) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return new MaredReturnCommand(sb.toString());
    }

    // ============================================================
    //  Events
    // ============================================================

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

        return new MaredOnCommand(eventType, parseBody(cur, line), replace);
    }

    private static boolean isKnownEvent(String type) {
        return switch (type) {
            case "right_click", "left_click", "middle_click",
                 "scroll_up", "scroll_down",
                 "key_press", "key_release",
                 "chat", "tick_client", "join", "leave",
                 "block_break", "block_place", "block_interact",
                 "entity_kill", "entity_hurt",
                 "player_death", "respawn",
                 "item_pickup", "item_crafted",
                 "dimension_change", "hotbar_switch",
                 "sneak_start", "sneak_end",
                 "sprint_start", "sprint_end",
                 "jump", "use_item", "attack",
                 "first_join", "player_move",
                 "health_change", "hunger_change", "xp_change",
                 "item_drop", "gamemode_change" -> true;
            default -> false;
        };
    }

    private static MaredScriptCommand parseOff(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "off: expected event name or 'all'");
        return new MaredOffCommand(tokens.get(1));
    }

    private static MaredScriptCommand parseEvery(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "every: missing period");
        int period;
        try { period = Integer.parseInt(tokens.get(1)); }
        catch (NumberFormatException e) { throw new ParseException(line, "every: invalid period: " + tokens.get(1)); }
        if (period < 1) throw new ParseException(line, "every: period must be >= 1");
        return new MaredEveryCommand(period, parseBody(cur, line));
    }

    private static MaredScriptCommand parseAfter(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "after: missing delay");
        int delay;
        try { delay = Integer.parseInt(tokens.get(1)); }
        catch (NumberFormatException e) { throw new ParseException(line, "after: invalid delay: " + tokens.get(1)); }
        if (delay < 1) throw new ParseException(line, "after: delay must be >= 1");
        return new MaredAfterCommand(delay, parseBody(cur, line));
    }

    private static MaredScriptCommand parseWaitUntil(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "wait_until: missing condition");
        return new MaredWaitUntilCommand(join(tokens.subList(1, tokens.size())));
    }

    private static MaredScriptCommand parseOnce(Cursor cur, List<String> tokens, int line) {
        return new MaredOnceCommand(parseBody(cur, line));
    }

    private static MaredScriptCommand parseFirstJoin(Cursor cur, List<String> tokens, int line) {
        return new MaredFirstJoinCommand(parseBody(cur, line));
    }

    // ============================================================
    //  Actions
    // ============================================================

    private static MaredScriptCommand parseLookAt(List<String> tokens, int line) {
        if (tokens.size() < 4) throw new ParseException(line, "look_at: expected x y z");
        return new MaredLookAtCommand(tokens.get(1), tokens.get(2), tokens.get(3));
    }

    private static MaredScriptCommand parseLook(List<String> tokens, int line) {
        if (tokens.size() < 3) throw new ParseException(line, "look: expected yaw pitch");
        return new MaredLookCommand(tokens.get(1), tokens.get(2));
    }

    private static MaredScriptCommand parseMove(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "move: expected direction");
        return new MaredMoveCommand(tokens.get(1), tokens.size() >= 3 ? tokens.get(2) : "on");
    }

    private static MaredScriptCommand parseSelectSlot(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "select_slot: expected number");
        return new MaredSelectSlotCommand(tokens.get(1));
    }

    // ============================================================
    //  Строки / сервер
    // ============================================================

    private static MaredScriptCommand parseSay(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "say: missing text");
        String raw = join(tokens.subList(1, tokens.size()));
        String scope = "all";
        int scopeIdx = raw.lastIndexOf(" scope=");
        if (scopeIdx >= 0) {
            scope = raw.substring(scopeIdx + " scope=".length()).trim();
            raw = raw.substring(0, scopeIdx).trim();
        }
        return new MaredSayCommand(raw, scope);
    }

    private static MaredScriptCommand parsePrint(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "print: missing text");
        String raw = join(tokens.subList(1, tokens.size()));
        String scope = "";
        int scopeIdx = raw.lastIndexOf(" scope=");
        if (scopeIdx >= 0) {
            scope = raw.substring(scopeIdx + " scope=".length()).trim();
            raw = raw.substring(0, scopeIdx).trim();
        }
        return new MaredPrintCommand(raw, scope);
    }

    private static MaredScriptCommand parseWait(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "wait: missing amount");
        double amount;
        try { amount = Double.parseDouble(tokens.get(1)); }
        catch (NumberFormatException e) { throw new ParseException(line, "wait: invalid number: " + tokens.get(1)); }
        return new MaredWaitCommand(amount, tokens.size() >= 3 ? tokens.get(2) : "seconds");
    }

    private static MaredScriptCommand parseGive(List<String> tokens, int line) {
        if (tokens.size() < 3) throw new ParseException(line, "give: missing target or item");
        int count = 1;
        if (tokens.size() >= 4) {
            try { count = Integer.parseInt(tokens.get(3)); }
            catch (NumberFormatException e) { throw new ParseException(line, "give: invalid count: " + tokens.get(3)); }
        }
        return new MaredGiveCommand(tokens.get(1), tokens.get(2), count);
    }

    private static MaredScriptCommand parseSet(List<String> tokens, int line) {
        if (tokens.size() < 4) throw new ParseException(line, "set: expected 'set name = value'");
        String name = tokens.get(1);
        if (name.startsWith("$")) name = name.substring(1);
        if (!tokens.get(2).equals("=")) throw new ParseException(line, "set: expected '=' after name");

        if (tokens.size() == 5 && "call".equalsIgnoreCase(tokens.get(3))) {
            String callPart = tokens.get(4);
            int paren = callPart.indexOf('(');
            if (paren < 0 || !callPart.endsWith(")")) {
                throw new ParseException(line, "set: expected 'call func(args)'");
            }
            String funcName = callPart.substring(0, paren).trim();
            String inside = callPart.substring(paren + 1, callPart.length() - 1).trim();
            List<String> callArgs = new ArrayList<>(2);
            if (!inside.isEmpty()) {
                for (String a : inside.split(",")) {
                    String s = a.trim();
                    if (!s.isEmpty()) callArgs.add(s);
                }
            }
            return new MaredSetFromCallCommand(name, funcName, callArgs);
        }
        return new MaredSetCommand(name, join(tokens.subList(3, tokens.size())));
    }

    private static MaredScriptCommand parseArray(List<String> tokens, int line) {
        if (tokens.size() < 4) throw new ParseException(line, "array: expected 'array name = [values]'");
        String name = tokens.get(1);
        if (name.startsWith("$")) name = name.substring(1);
        if (!tokens.get(2).equals("=")) throw new ParseException(line, "array: expected '=' after name");
        return new MaredSetCommand(name, join(tokens.subList(3, tokens.size())));
    }

    private static MaredScriptCommand parseCall(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "call: missing name");
        String full = join(tokens.subList(1, tokens.size()));
        String name;
        List<String> args = new ArrayList<>(2);
        int paren = full.indexOf('(');
        if (paren >= 0 && full.endsWith(")")) {
            name = full.substring(0, paren).trim();
            String inside = full.substring(paren + 1, full.length() - 1).trim();
            if (!inside.isEmpty()) {
                for (String a : inside.split(",")) {
                    String s = a.trim();
                    if (!s.isEmpty()) args.add(s);
                }
            }
        } else name = full.trim();
        return new MaredCallCommand(name, args);
    }

    private static MaredScriptCommand parseLog(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "log: missing text");
        return new MaredLogCommand(join(tokens.subList(1, tokens.size())));
    }

    private static MaredScriptCommand parseDebug(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "debug: missing expression");
        return new MaredDebugCommand(join(tokens.subList(1, tokens.size())));
    }

    private static MaredScriptCommand parseAssert(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "assert: missing condition");
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
        return new MaredAssertCommand(join(condTokens), message);
    }

    private static boolean isComparisonOrLogicalOperator(String s) {
        return switch (s) {
            case "==", "!=", "<", ">", "<=", ">=", "&&", "||", "contains" -> true;
            default -> false;
        };
    }

    private static MaredScriptCommand parseUnblock(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "unblock: missing key");
        String keyRaw = tokens.get(1);
        if (MaredKeyNames.parseAny(keyRaw) == null)
            throw new ParseException(line, "unblock: unknown key: " + keyRaw);
        return new MaredUnblockCommand(keyRaw);
    }

    private static MaredScriptCommand parseBlockCmd(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "block: missing key");
        String keyRaw = tokens.get(1);
        if (MaredKeyNames.parseAny(keyRaw) == null)
            throw new ParseException(line, "block: unknown key: " + keyRaw);
        return new MaredBlockCommand(keyRaw);
    }

    private static MaredScriptCommand parseToggle(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "toggle: missing key");
        String keyRaw = tokens.get(1);
        if (MaredKeyNames.parseAny(keyRaw) == null)
            throw new ParseException(line, "toggle: unknown key: " + keyRaw);
        return new MaredToggleCommand(keyRaw);
    }

    private static MaredScriptCommand parseMc(String head, int line) {
        String payload = head.substring(2).stripLeading();
        if (payload.startsWith("/")) payload = payload.substring(1);
        String command = new com.fixmer.genesis.technology.editor.CommandInput(payload, 0).text().stripTrailing();
        if (command.isEmpty()) throw new ParseException(line, "mc: missing command");
        return new MaredMcCommand(command);
    }

    // ============================================================
    //  Bind
    // ============================================================

    private static MaredScriptCommand parseBind(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "bind: missing key");

        String keyRaw = null;
        MaredBindCommand.Mode mode = MaredBindCommand.Mode.DEFAULT;
        boolean blockVanilla = false;

        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            switch (t.toLowerCase()) {
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
            if (mode == MaredBindCommand.Mode.CLEAR || blockVanilla) body = new ArrayList<>(0);
            else throw new ParseException(line, "bind: expected '{' after arguments");
        } else body = parseBody(cur, line);

        if (mode == MaredBindCommand.Mode.CLEAR && !body.isEmpty())
            throw new ParseException(line, "bind clear: body must be empty");

        return new MaredBindCommand(keyRaw, key, mode, blockVanilla, body);
    }

    // ============================================================
    //  Body
    // ============================================================

    private static List<MaredScriptCommand> parseBody(Cursor cur, int line) {
        cur.skipWhitespaceAndNewlines();
        if (cur.eof() || cur.peekChar() != '{') return new ArrayList<>(0);
        cur.next();
        List<MaredScriptCommand> body = parseStatements(cur, line);
        cur.skipSeparators();
        if (cur.eof() || cur.peekChar() != '}') throw new ParseException(line, "block '{' not closed");
        cur.next();
        return body;
    }

    // ============================================================
    //  Tokenize
    // ============================================================

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>(8);
        StringBuilder current = new StringBuilder(24);
        boolean inString = false;
        int paren = 0;
        int braceDepth = 0;
        int n = line.length();

        for (int i = 0; i < n; i++) {
            char c = line.charAt(i);

            if (!inString && c == '$' && i + 1 < n && line.charAt(i + 1) == '{') {
                braceDepth++;
                current.append(c).append('{');
                i++;
                continue;
            }
            if (!inString && c == '}' && braceDepth > 0) {
                braceDepth--;
                current.append(c);
                continue;
            }
            if (c == '\\' && inString && i + 1 < n) {
                current.append(c).append(line.charAt(i + 1));
                i++;
                continue;
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
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
            return s.substring(1, s.length() - 1);
        return s;
    }

    private static String join(List<String> tokens) {
        if (tokens.isEmpty()) return "";
        if (tokens.size() == 1) return tokens.get(0);
        StringBuilder sb = new StringBuilder(tokens.size() * 8);
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    // ============================================================
    //  Cursor
    // ============================================================

    private static final class Cursor {
        private int depth;
        private final String text;
        private int pos;
        private int line;
        private int lastHeadStart;

        Cursor(String text) {
            this.text = text;
            this.pos = 0;
            this.line = 1;
            this.lastHeadStart = 0;
        }

        boolean eof() { return pos >= text.length(); }
        int line() { return line; }
        int getPos() { return pos; }
        char peekChar() { return text.charAt(pos); }

        String getRemaining(int maxLen) {
            if (pos >= text.length()) return "";
            int end = Math.min(text.length(), pos + maxLen);
            return text.substring(pos, end).replace("\n", "\\n").replace("\r", "\\r");
        }

        char next() {
            char c = text.charAt(pos++);
            if (c == '\n') line++;
            return c;
        }

        void seekTo(int newPos) {
            if (newPos == pos) return;
            if (newPos < pos) {
                for (int i = pos - 1; i >= newPos; i--) {
                    if (text.charAt(i) == '\n') line--;
                }
            } else {
                for (int i = pos; i < newPos; i++) {
                    if (text.charAt(i) == '\n') line++;
                }
            }
            pos = newPos;
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
            int end = com.fixmer.genesis.technology.editor.ScriptHeads.end(text, pos);
            String head = text.substring(pos, end);
            seekTo(end);
            return head;
        }
    }
}
