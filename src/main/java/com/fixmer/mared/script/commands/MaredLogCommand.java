package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

/** log "текст" — записать в лог редактора. */
public class MaredLogCommand extends MaredScriptCommand {

    private final String text;

    public MaredLogCommand(String text) {
        this.text = text;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        ctx.log("[mared log] " + ctx.substitute(text));
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "log \"" + text + "\""; }
}