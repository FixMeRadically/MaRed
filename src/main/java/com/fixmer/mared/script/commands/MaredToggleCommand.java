package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredKeyBlocker;
import com.fixmer.mared.script.MaredKeyNames;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;

/**
 * toggle <key> — переключает блокировку клавиши.
 *
 * FIX: различаем случаи:
 *   - был bind + block → unblock оставит bind, снимет только блокировку
 *   - был bind block   → unblock удалит и bind, и блокировку
 *   Логируем, что именно произошло.
 */
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
            int before = MaredBindRegistry.entries(keyRaw).size();
            MaredBindRegistry.unblock(keyRaw);
            int after = MaredBindRegistry.entries(keyRaw).size();

            if (after < before) {
                ctx.log(MaredLang.format("mared.log.toggle.off_removed", keyRaw, before - after));
            } else {
                ctx.log(MaredLang.format("mared.log.toggle.off_kept", keyRaw));
            }
        } else {
            MaredBindRegistry.block(keyRaw);
            ctx.log(MaredLang.format("mared.log.toggle.on", keyRaw));
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "toggle " + keyRaw; }
}