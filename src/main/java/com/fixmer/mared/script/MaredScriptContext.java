package com.fixmer.mared.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fixmer.mared.script.commands.MaredScriptCommand;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MaredScriptContext {

    public static final class Func {
        public final List<String> params;
        public final List<MaredScriptCommand> body;
        public Func(List<String> params, List<MaredScriptCommand> body) {
            this.params = params;
            this.body = body;
        }
    }

    private final ServerPlayer initiator;
    private final MinecraftServer server;
    private final Map<String, Object> variables = new HashMap<>();
    private final Consumer<String> logger;

    private final Map<String, Func> functions = new HashMap<>();

    private boolean breakRequested = false;
    private boolean continueRequested = false;

    public MaredScriptContext(ServerPlayer initiator, MinecraftServer server, Consumer<String> logger) {
        this.initiator = initiator;
        this.server = server;
        this.logger = logger;
    }

    public ServerPlayer getInitiator() { return initiator; }
    public MinecraftServer getServer() { return server; }

    public void log(String line) { if (logger != null) logger.accept(line); }

    public void setVariable(String name, Object value) { variables.put(name, value); }

    public Object getVariable(String name) {
        if ("self".equals(name)) {
            return initiator != null ? initiator.getName().getString() : "console";
        }
        if ("world".equals(name)) {
            if (initiator != null && initiator.level() != null) {
                return initiator.level().dimension().location().toString();
            }
            return "unknown";
        }
        return variables.get(name);
    }

    public Map<String, Object> getAllVariables() { return variables; }

    /**
     * Подстановка в строку:
     *   $name          — значение переменной (строка)
     *   ${выражение}   — вычислить выражение и вставить результат
     *
     * Внутри ${...} кавычки не экранируются — они просто кавычки.
     * То есть если внутри ${} есть "..." — это строковый литерал.
     */
    public String substitute(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder result = new StringBuilder();
        int i = 0;
        int n = input.length();

        while (i < n) {
            char c = input.charAt(i);

            if (c == '$' && i + 1 < n && input.charAt(i + 1) == '{') {
                int start = i + 2;
                int end = start;
                int depth = 1;
                boolean inString = false;
                char quote = 0;

                while (end < n && depth > 0) {
                    char ch = input.charAt(end);
                    if (inString) {
                        if (ch == quote) inString = false;
                    } else {
                        if (ch == '"' || ch == '\'') { inString = true; quote = ch; }
                        else if (ch == '{') depth++;
                        else if (ch == '}') depth--;
                    }
                    if (depth == 0) break;
                    end++;
                }

                if (depth != 0) {
                    result.append("<UNCLOSED:").append(input.substring(i)).append(">");
                    break;
                }

                String expr = input.substring(start, end);
                String value;
                try {
                    Object v = MaredExpr.eval(expr, this);
                    value = MaredExpr.stringify(v);
                } catch (Exception e) {
                    value = "<ERR:" + e.getMessage() + ">";
                }
                result.append(value);
                i = end + 1;
                continue;
            }

            if (c == '$') {
                int start = i + 1;
                int end = start;
                while (end < n && isVarChar(input.charAt(end))) end++;
                if (end > start) {
                    String varName = input.substring(start, end);
                    Object value = getVariable(varName);
                    result.append(value != null ? MaredExpr.stringify(value) : "$" + varName);
                    i = end;
                    continue;
                }
            }

            result.append(c);
            i++;
        }
        return result.toString();
    }

    private boolean isVarChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    // ---- break / continue ----

    public void requestBreak() { breakRequested = true; }
    public void requestContinue() { continueRequested = true; }
    public boolean isBreakRequested() { return breakRequested; }
    public boolean isContinueRequested() { return continueRequested; }
    public void clearBreakContinue() { breakRequested = false; continueRequested = false; }

    // ---- функции ----

    public void registerFunction(String name, List<String> params, List<MaredScriptCommand> body) {
        functions.put(name, new Func(params, body));
    }

    public boolean hasFunction(String name) { return functions.containsKey(name); }

    public Func getFunction(String name) { return functions.get(name); }
}