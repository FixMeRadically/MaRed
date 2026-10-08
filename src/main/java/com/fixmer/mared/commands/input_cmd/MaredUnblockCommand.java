package com.fixmer.mared.commands.input_cmd;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredBindRegistry;
import com.fixmer.mared.commands.input.MaredKeyNames;
import com.fixmer.mared.MaredLang;

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
        MaredBindRegistry.unblock(keyRaw, ctx);
        ctx.log(MaredLang.format("mared.log.unblock.done", keyRaw));
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "unblock " + keyRaw; }
}