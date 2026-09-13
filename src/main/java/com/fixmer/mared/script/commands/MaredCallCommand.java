package com.fixmer.mared.script.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;

/**
 * call name(arg1, arg2) — вызов функции.
 * Толкает тело функции кадром. Параметры заменяют переменные в контексте.
 *
 * Когда тело заканчивается — переменные восстанавливаются (см. FuncFrameOwner).
 */
public class MaredCallCommand extends MaredScriptCommand {

    private final String name;
    private final List<String> args;

    public MaredCallCommand(String name, List<String> args) {
        this.name = name;
        this.args = args;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        MaredScriptContext.Func fn = ctx.getFunction(name);
        if (fn == null) {
            ctx.log("[error] функция не найдена: " + name);
            return;
        }

        // Сохраняем текущие значения параметров (могут перезаписаться)
        Map<String, Object> backup = new HashMap<>();
        for (int i = 0; i < fn.params.size(); i++) {
            String p = fn.params.get(i);
            backup.put(p, ctx.getVariable(p));
        }

        // Устанавливаем новые значения
        for (int i = 0; i < fn.params.size(); i++) {
            String p = fn.params.get(i);
            String v = i < args.size() ? ctx.substitute(args.get(i)) : "";
            ctx.setVariable(p, v);
        }

        // Толкаем тело функции; после завершения — восстановим переменные
        List<MaredScriptCommand> body = fn.body;
        final Map<String, Object> backupFinal = backup;
        final MaredScriptContext ctxFinal = ctx;

        // Оборачиваем через пустой кадр-родитель, чтобы поймать конец тела.
        // Проще: добавим на дно нового кадра спец-команду "restore".
        // Но у нас нет такой команды — сделаем через LoopOwner с одним прогоном.
        MaredScriptExecutor.LoopOwner owner = new MaredScriptExecutor.LoopOwner() {
            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor e, Frame bodyFrame) {
                for (Map.Entry<String, Object> en : backupFinal.entrySet()) {
                    c.setVariable(en.getKey(), en.getValue());
                }
                return false;
            }

            @Override
            public String describe() { return "call " + name; }
        };

        exec.pushLoopBody(body, owner);
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "call " + name + "(" + String.join(", ", args) + ")"; }
}