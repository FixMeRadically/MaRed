package com.fixmer.mared.commands.server_cmd;
import com.fixmer.mared.commands.control.MaredIfCommand;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.MaredLang;

/**
 * assert <условие> ["сообщение"] — проверка.
 * Если условие ложно — вывести сообщение и остановить скрипт.
 *
 * Не бросает исключений — при провале вызывает exec.stopAll(),
 * чтобы не было [error] null в логе.
 */
public class MaredAssertCommand extends MaredScriptCommand {

    private final String condition;
    private final String message;

    public MaredAssertCommand(String condition, String message) {
        this.condition = condition;
        this.message = message;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        boolean ok = MaredIfCommand.evaluateStatic(condition, ctx);
        if (!ok) {
            String m = (message != null) ? ctx.substitute(message) : "(no message)";
            ctx.log(MaredLang.format("mared.log.assert.fail", condition, m));
            exec.stopAll();
        } else {
            ctx.log(MaredLang.format("mared.log.assert.ok", condition));
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "assert " + condition; }
}