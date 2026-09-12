package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

public abstract class MaredScriptCommand {

    public abstract boolean execute(MaredScriptContext ctx);

    public int getDelayTicks() {
        return 0;
    }

    public abstract String describe();
}