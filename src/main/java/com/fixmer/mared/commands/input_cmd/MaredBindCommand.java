package com.fixmer.mared.commands.input_cmd;

import java.util.List;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredBindRegistry;
import com.fixmer.mared.commands.input.MaredKeyNames;
import com.fixmer.mared.MaredLang;

/**
 * bind <key> [add|replace|clear|block|hold|release] [{ ... }]
 */
public class MaredBindCommand extends MaredScriptCommand {

    public enum Mode { DEFAULT, ADD, REPLACE, CLEAR, HOLD, RELEASE }

    private final String keyRaw;
    private final MaredKeyNames.ParsedKey key;
    private final Mode mode;
    private final boolean blockVanilla;
    private final List<MaredScriptCommand> body;

    public MaredBindCommand(String keyRaw, MaredKeyNames.ParsedKey key,
                            Mode mode, boolean blockVanilla,
                            List<MaredScriptCommand> body) {
        this.keyRaw = keyRaw;
        this.key = key;
        this.mode = mode;
        this.blockVanilla = blockVanilla;
        this.body = body;
    }

    public String getKeyRaw() { return keyRaw; }
    public MaredKeyNames.ParsedKey getKey() { return key; }
    public Mode getMode() { return mode; }
    public boolean isBlockVanilla() { return blockVanilla; }
    public List<MaredScriptCommand> getBody() { return body; }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String display = key != null ? MaredKeyNames.display(key) : keyRaw;

        switch (mode) {
            case CLEAR -> {
                MaredBindRegistry.clear(display, ctx);
                ctx.log(MaredLang.format("mared.log.bind.cleared", display));
            }
            case ADD -> {
                MaredBindRegistry.add(display, body, ctx, blockVanilla, MaredBindRegistry.BindMode.PRESS);
                ctx.log(MaredLang.format("mared.log.bind.added", display, body.size()));
            }
            case HOLD -> {
                MaredBindRegistry.add(display, body, ctx, blockVanilla, MaredBindRegistry.BindMode.HOLD);
                ctx.log(MaredLang.format("mared.log.bind.hold", display, body.size()));
            }
            case RELEASE -> {
                MaredBindRegistry.add(display, body, ctx, blockVanilla, MaredBindRegistry.BindMode.RELEASE);
                ctx.log(MaredLang.format("mared.log.bind.release", display, body.size()));
            }
            case REPLACE -> {
                MaredBindRegistry.replace(display, body, ctx, blockVanilla, MaredBindRegistry.BindMode.PRESS);
                ctx.log(MaredLang.format("mared.log.bind.replaced", display, body.size()));
            }
            default -> {
                MaredBindRegistry.replace(display, body, ctx, blockVanilla, MaredBindRegistry.BindMode.PRESS);
                ctx.log(MaredLang.format("mared.log.bind.bound", display, body.size()));
            }
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() {
        return "bind " + (key != null ? MaredKeyNames.display(key) : keyRaw);
    }
}