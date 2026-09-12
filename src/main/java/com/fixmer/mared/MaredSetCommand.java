package com.fixmer.mared;

public class MaredSetCommand extends MaredScriptCommand {

    private final String name;
    private final String value;

    public MaredSetCommand(String name, String value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        // Пытаемся распарсить как число.
        Object parsed = parseValue(value, ctx);
        ctx.setVariable(name, parsed);
        return true;
    }

    private Object parseValue(String raw, MaredScriptContext ctx) {
        String substituted = ctx.substitute(raw);
        // Число?
        try {
            if (substituted.contains(".")) {
                return Double.parseDouble(substituted);
            }
            return Long.parseLong(substituted);
        } catch (NumberFormatException ignored) {
            // Не число — значит строка.
        }
        return substituted;
    }

    @Override
    public String describe() {
        return "set " + name + " = " + value;
    }
}