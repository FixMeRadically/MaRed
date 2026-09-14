package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredKeyBlocker;
import com.fixmer.mared.script.MaredKeyNames;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;

public class MaredToggleCommand extends MaredScriptCommand {

    private final String keyRaw;

    public MaredToggleCommand(String keyRaw) {
        this.keyRaw = keyRaw;
    }

    public String getKeyRaw() { return keyRaw; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyRaw);
        if (pk == null) {
            ctx.log(MaredLang.format("mared.log.toggle.unknown_key", keyRaw));
            return true;
        }

        boolean blocked = MaredKeyBlocker.isBlocked(pk.keyCode);
        if (blocked) {
            MaredBindRegistry.unblock(keyRaw);
            ctx.log(MaredLang.format("mared.log.toggle.off", keyRaw));
        } else {
            MaredBindRegistry.block(keyRaw);
            ctx.log(MaredLang.format("mared.log.toggle.on", keyRaw));
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "toggle " + keyRaw; }
}