package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

public class MaredSetCommand extends MaredScriptCommand {

    private final String name;
    private final String value;

    public MaredSetCommand(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        Object parsed = parseValue(value, ctx);
        ctx.setVariable(name, parsed);
        return true;
    }

    private Object parseValue(String raw, MaredScriptContext ctx) {
        String substituted = ctx.substitute(raw);
        try {
            if (substituted.contains(".")) {
                return Double.parseDouble(substituted);
            }
            return Long.parseLong(substituted);
        } catch (NumberFormatException ignored) {
            // not a number
        }
        return substituted;
    }

    @Override
    public String describe() {
        return "set " + name + " = " + value;
    }
}