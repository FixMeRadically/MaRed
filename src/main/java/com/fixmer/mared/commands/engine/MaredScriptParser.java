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

public class MaredScriptParser {

    /** FIX: максимальное количество итераций без продвижения курсора. */
    private static final int MAX_STUCK_ITERATIONS = 100;

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

    /**
     * FIX: защита от застревания + обработка блоков { } на верхнем уровне.
     *
     * Блок { ... } на верхнем уровне — это ГРУППИРОВКА команд, не отдельная команда.
     * Содержимое блока добавляется в родительский список (плоская вставка).
     * Пустой блок { } — OK, ничего не добавляется.
     */
    private static List<MaredScriptCommand> parseStatements(Cursor cur, int line) {
        List<MaredScriptCommand> commands = new ArrayList<>();
        int lastPos = -1;
        int stuckCount = 0;

        while (true) {
            cur.skipSeparators();
            if (cur.eof()) break;
            if (cur.peekChar() == '}') break;

            // FIX: проверка на застревание
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

            // FIX: { ... } на верхнем уровне — рекурсивно парсим как группу
            if (cur.peekChar() == '{') {
                int blockLine = cur.line();
                cur.next();  // съедаем {

                List<MaredScriptCommand> blockBody = parseStatements(cur, blockLine);

                cur.skipSeparators();
                if (cur.eof() || cur.peekChar() != '}') {
                    throw new ParseException(blockLine, "block '{' not closed");
                }
                cur.next();  // съедаем }

                // Плоская вставка: содержимое блока в родительский список
                if (!blockBody.isEmpty()) {
                    commands.addAll(blockBody);
                }
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
            case "if":         return parseIfChain(cur, tokens, startLine);
            case "repeat":     return parseRepeat(cur, tokens, startLine);
            case "for":        return parseFor(cur, tokens, startLine);
            case "while":      return parseWhile(cur, tokens, startLine);
            case "func":       return parseFunc(cur, tokens, startLine);
            case "bind":       return parseBind(cur, tokens, startLine);
            case "on":         return parseOn(cur, tokens, startLine);
            case "every":      return parseEvery(cur, tokens, startLine);
            case "after":      return parseAfter(cur, tokens, startLine);
            case "wait_until": return parseWaitUntil(cur, tokens, startLine);
            case "once":       return parseOnce(cur, tokens, startLine);
            case "first_join": return parseFirstJoin(cur, tokens, startLine);
            case "break":      return new MaredBreakCommand();
            case "continue":   return new MaredContinueCommand();
            case "return":     return parseReturn(tokens, startLine);
            case "exit":       return new MaredExitCommand();

            // ---- Action API (0.2.5+) ----
            case "look_at":     return parseLookAt(tokens, startLine);
            case "look":        return parseLook(tokens, startLine);
            case "move":        return parseMove(tokens, startLine);
            case "stop":        return new MaredStopCommand();
            case "jump":        return new MaredJumpCommand();
            case "attack":      return new MaredAttackCommand();
            case "use":         return new MaredUseCommand();
            case "drop":        return new MaredDropCommand();
            case "swap_hands":  return new MaredSwapCommand();
            case "select_slot": return parseSelectSlot(tokens, startLine);

            // ---- FIX 0.2.5+: снятие слушателей ----
            case "off":         return parseOff(tokens, startLine);

            default: return parseLine(tokens, startLine);
        }
    }

    // ============================================================
    //  Action API
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
        String dir = tokens.get(1);
        String mode = tokens.size() >= 3 ? tokens.get(2) : "on";
        return new MaredMoveCommand(dir, mode);
    }

    private static MaredScriptCommand parseSelectSlot(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "select_slot: expected number");
        return new MaredSelectSlotCommand(tokens.get(1));
    }

    private static MaredScriptCommand parseOff(List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "off: expected event name or 'all'");
        return new MaredOffCommand(tokens.get(1));
    }

    // ============================================================
    //  Триггеры времени
    // ============================================================

    private static MaredScriptCommand parseEvery(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "every: missing period");
        String periodStr = tokens.get(1);
        int period;
        try {
            period = Integer.parseInt(periodStr);
        } catch (NumberFormatException e) {
            throw new ParseException(line, "every: invalid period: " + periodStr);
        }
        if (period < 1) throw new ParseException(line, "every: period must be >= 1");
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredEveryCommand(period, body);
    }

    private static MaredScriptCommand parseAfter(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "after: missing delay");
        String delayStr = tokens.get(1);
        int delay;
        try {
            delay = Integer.parseInt(delayStr);
        } catch (NumberFormatException e) {
            throw new ParseException(line, "after: invalid delay: " + delayStr);
        }
        if (delay < 1) throw new ParseException(line, "after: delay must be >= 1");
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredAfterCommand(delay, body);
    }

    private static MaredScriptCommand parseWaitUntil(Cursor cur, List<String> tokens, int line) {
        if (tokens.size() < 2) throw new ParseException(line, "wait_until: missing condition");
        String condition = join(tokens.subList(1, tokens.size()));
        return new MaredWaitUntilCommand(condition);
    }

    private static MaredScriptCommand parseOnce(Cursor cur, List<String> tokens, int line) {
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredOnceCommand(body);
    }

    private static MaredScriptCommand parseFirstJoin(Cursor cur, List<String> tokens, int line) {
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredFirstJoinCommand(body);
    }

    // ============================================================
    //  Существующие парсеры
    // ============================================================

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

        int end = tokens.size();
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i).equalsIgnoreCase("times")) {
                end = i;
                break;
            }
        }

        StringBuilder countSb = new StringBuilder();
        for (int i = 1; i < end; i++) {
            if (countSb.length() > 0) countSb.append(' ');
            countSb.append(tokens.get(i));
        }

        if (countSb.length() == 0)
            throw new ParseException(line, "repeat: empty count expression");

        String countExpr = countSb.toString();
        List<MaredScriptCommand> body = parseBody(cur, line);
        return new MaredRepeatCommand(countExpr, body);
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
            List<MaredScriptCommand> body = parseBody(cur, line);
            return new MaredForInCommand(var, arrName, body);
        }

        if (!tokens.get(2).equals("="))
            throw new ParseException(line, "for: expected '=' after name");

        int toIdx = -1;
        for (int i = 3; i < tokens.size(); i++) {
            if (tokens.get(i).equalsIgnoreCase("to")) { toIdx = i; break; }
        }
        if (toIdx < 0)
            throw new ParseException(line, "for: missing 'to'");

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

        String from = fromSb.toString();
        String to = toSb.toString();
        if (from.isEmpty() || to.isEmpty())
            throw new ParseException(line, "for: empty from/to expression");

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
            case "block_break": case "block_place": case "block_interact":
            case "entity_kill": case "entity_hurt":
            case "player_death": case "respawn":
            case "item_pickup": case "item_crafted":
            case "dimension_change":
            case "hotbar_switch":
            case "sneak_start": case "sneak_end":
            case "sprint_start": case "sprint_end":
            case "jump": case "use_item": case "attack":
            case "first_join":
            case "player_move":
            case "health_change":
            case "hunger_change":
            case "xp_change":
            case "item_drop":
            case "gamemode_change":
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
            case "print":   return parsePrint(tokens, lineNumber);
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

    private static MaredScriptCommand parsePrint(List<String> tokens, int lineNumber) {
        if (tokens.size() < 2) throw new ParseException(lineNumber, "print: missing text");
        StringBuilder raw = new StringBuilder();
        for (int i = 1; i < tokens.size(); i++) {
            if (i > 1) raw.append(' ');
            raw.append(tokens.get(i));
        }
        String textWithArgs = raw.toString();
        String scope = "";
        int scopeIdx = textWithArgs.lastIndexOf(" scope=");
        if (scopeIdx >= 0) {
            scope = textWithArgs.substring(scopeIdx + " scope=".length()).trim();
            textWithArgs = textWithArgs.substring(0, scopeIdx).trim();
        }
        return new MaredPrintCommand(textWithArgs, scope);
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

        if (tokens.size() == 5 && "call".equalsIgnoreCase(tokens.get(3))) {
            String callPart = tokens.get(4);
            int paren = callPart.indexOf('(');
            if (paren < 0 || !callPart.endsWith(")")) {
                throw new ParseException(lineNumber, "set: expected 'call func(args)'");
            }
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

        /** FIX: текущая позиция — для диагностики застревания. */
        int getPos() { return pos; }

        /** FIX: остаток текста (ограниченной длины) — для диагностики. */
        String getRemaining(int maxLen) {
            if (pos >= text.length()) return "";
            int end = Math.min(text.length(), pos + maxLen);
            String s = text.substring(pos, end);
            s = s.replace("\n", "\\n").replace("\r", "\\r");
            return s;
        }

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