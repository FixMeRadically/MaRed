package com.fixmer.mared.commands.input_cmd;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredBindRegistry;
import com.fixmer.mared.commands.input.MaredKeyNames;
import com.fixmer.mared.MaredLang;

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