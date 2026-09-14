package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredKeyNames;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;

public class MaredBlockCommand extends MaredScriptCommand {

    private final String keyRaw;

    public MaredBlockCommand(String keyRaw) {
        this.keyRaw = keyRaw;
    }

    public String getKeyRaw() { return keyRaw; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyRaw);
        if (pk == null) {
            ctx.log(MaredLang.format("mared.log.block.unknown_key", keyRaw));
            return true;
        }
        MaredBindRegistry.block(keyRaw);
        ctx.log(MaredLang.format("mared.log.block.done", keyRaw));
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "block " + keyRaw; }
}