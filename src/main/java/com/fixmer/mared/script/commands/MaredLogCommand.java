package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

public class MaredLogCommand extends MaredScriptCommand {

    private final String text;

    public MaredLogCommand(String text) {
        this.text = text;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String t = text == null ? "" : text.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        ctx.log("[log] " + ctx.substitute(t));
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "log \"" + text + "\""; }
}