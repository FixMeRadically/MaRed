package com.fixmer.mared.commands.expr;

import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.genesis.technology.expression.ExpressionEngine;

/** Compatibility facade: preserve the old method descriptors for existing callers. */
public final class MaredExpr extends ExpressionEngine {
    private MaredExpr() {}
    public static Object eval(String expression, MaredScriptContext context) {
        return ExpressionEngine.eval(expression, context);
    }
    public static String evalString(String expression, MaredScriptContext context) {
        return ExpressionEngine.evalString(expression, context);
    }
    public static boolean evalBool(String expression, MaredScriptContext context) {
        return ExpressionEngine.evalBool(expression, context);
    }
    public static double evalNumber(String expression, MaredScriptContext context) {
        return ExpressionEngine.evalNumber(expression, context);
    }
}
