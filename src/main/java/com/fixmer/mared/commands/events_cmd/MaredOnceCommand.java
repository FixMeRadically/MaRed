package com.fixmer.mared.commands.events_cmd;

import java.util.List;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.storage.MaredGlobalStorage;

/**
 * once { ... } — выполнить тело один раз за сессию.
 *
 * Метка хранится в MaredGlobalStorage под ключом
 * "global.__once_<hash>", где hash — от describe() команд тела.
 */
public class MaredOnceCommand extends MaredScriptCommand {

    private final List<MaredScriptCommand> body;
    private final String key;

    public MaredOnceCommand(List<MaredScriptCommand> body) {
        this.body = body;
        this.key = "global.__once_" + hashBody(body);
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        if (MaredGlobalStorage.has(key)) {
            ctx.log("[mared] once: уже выполнялось, пропускаем");
            return;
        }
        MaredGlobalStorage.set(key, Boolean.TRUE);
        ctx.log("[mared] once: выполняем первый раз");
        exec.pushBody(body);
    }

    private static int hashBody(List<MaredScriptCommand> body) {
        int h = 0;
        for (MaredScriptCommand c : body) {
            h = h * 31 + c.describe().hashCode();
        }
        return h & 0x7FFFFFFF;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "once"; }
}