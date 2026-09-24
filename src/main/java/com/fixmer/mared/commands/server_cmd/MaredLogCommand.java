package com.fixmer.mared.commands.server_cmd;

import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;

public class MaredLogCommand extends MaredScriptCommand {

    private final String text;

    public MaredLogCommand(String text) { this.text = text; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String t = text == null ? "" : text.trim();
        int n = t.length();
        if (n >= 2 && t.charAt(0) == '"' && t.charAt(n - 1) == '"') {
            t = t.substring(1, n - 1);
        }
        t = MaredDebugCommand.unescapeQuotes(t);
        t = ctx.substitute(t);
        t = t.replace("\t", "    ");
        ctx.log("[log] " + t);
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "log \"" + text + "\""; }
}