package com.fixmer.mared.commands.control;

import java.util.ArrayList;
import java.util.List;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.expr.MaredExpr;

/**
 * if <условие> { ... }
 * elif <условие> { ... }*
 * else { ... }?
 *
 * Условие вычисляется через MaredExpr — поддерживает арифметику,
 * сравнения, логические операторы, функции.
 */
public class MaredIfCommand extends MaredScriptCommand {

    private final String condition;
    private final List<MaredScriptCommand> thenBody;
    private final List<String> elifConds;
    private final List<List<MaredScriptCommand>> elifBodies;
    private final List<MaredScriptCommand> elseBody;

    public MaredIfCommand(String condition, List<MaredScriptCommand> thenBody) {
        this(condition, thenBody, new ArrayList<>(), new ArrayList<>(), null);
    }

    public MaredIfCommand(String condition, List<MaredScriptCommand> thenBody,
                          List<String> elifConds,
                          List<List<MaredScriptCommand>> elifBodies,
                          List<MaredScriptCommand> elseBody) {
        this.condition = condition;
        this.thenBody = thenBody;
        this.elifConds = elifConds;
        this.elifBodies = elifBodies;
        this.elseBody = elseBody;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        // Выбираем ветку
        List<MaredScriptCommand> body = null;
        if (evaluateStatic(condition, ctx)) {
            body = thenBody;
        } else {
            for (int i = 0; i < elifConds.size(); i++) {
                if (evaluateStatic(elifConds.get(i), ctx)) {
                    body = elifBodies.get(i);
                    break;
                }
            }
            if (body == null) body = elseBody;
        }
        if (body != null && !body.isEmpty()) {
            exec.pushBody(body);
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "if " + condition; }

    /**
     * Вычислить условие. Используется этой командой и MaredWhileCommand.
     *
     * Через MaredExpr.evalBool — поддерживает:
     *   - сравнения: == != > < >= <=
     *   - логика: && || !
     *   - арифметика: + - * / %
     *   - скобки, переменные, функции
     */
    public static boolean evaluateStatic(String condition, MaredScriptContext ctx) {
        if (condition == null || condition.trim().isEmpty()) return false;
        String expr = condition.trim();
        if (expr.startsWith("${") && expr.endsWith("}")) expr = expr.substring(2, expr.length() - 1);
        return MaredExpr.evalBool(expr, ctx);
    }
}
