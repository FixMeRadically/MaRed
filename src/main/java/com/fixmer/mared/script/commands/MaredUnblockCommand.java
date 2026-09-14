package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredKeyNames;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;

public class MaredUnblockCommand extends MaredScriptCommand {

    private final String keyRaw;

    public MaredUnblockCommand(String keyRaw) {
        this.keyRaw = keyRaw;
    }

    public String getKeyRaw() { return keyRaw; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyRaw);
        if (pk == null) {
            ctx.log(MaredLang.format("mared.log.unblock.unknown_key", keyRaw));
            return true;
        }
        MaredBindRegistry.unblock(keyRaw);
        ctx.log(MaredLang.format("mared.log.unblock.done", keyRaw));
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "unblock " + keyRaw; }
}