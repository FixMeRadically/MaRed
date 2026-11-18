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
 * "global.__once_<hash>": SHA-256 полного исходного блока, включая вложенные тела.
 */
public class MaredOnceCommand extends MaredScriptCommand {

    private final List<MaredScriptCommand> body;
    private final String key;

    public MaredOnceCommand(List<MaredScriptCommand> body){this(body,java.util.UUID.randomUUID().toString());}
    public MaredOnceCommand(List<MaredScriptCommand> body,String sourceIdentity){
        this.body=List.copyOf(body);
        try{this.key="global.__once_"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(sourceIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
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

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "once"; }
}