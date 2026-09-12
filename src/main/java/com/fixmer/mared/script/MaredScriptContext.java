package com.fixmer.mared.script;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MaredScriptContext {

    private final ServerPlayer initiator;
    private final MinecraftServer server;
    private final Map<String, Object> variables = new HashMap<>();
    private final Consumer<String> logger;

    public MaredScriptContext(ServerPlayer initiator, MinecraftServer server, Consumer<String> logger) {
        this.initiator = initiator;
        this.server = server;
        this.logger = logger;
    }

    public ServerPlayer getInitiator() {
        return initiator;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void log(String line) {
        if (logger != null) logger.accept(line);
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

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

    public String substitute(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '$') {
                int start = i + 1;
                int end = start;
                while (end < input.length() && isVarChar(input.charAt(end))) {
                    end++;
                }
                if (end > start) {
                    String varName = input.substring(start, end);
                    Object value = getVariable(varName);
                    result.append(value != null ? value.toString() : "$" + varName);
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
        return Character.isLetterOrDigit(c) || c == '_';
    }
}