package com.fixmer.mared.script.commands;

import java.lang.reflect.Array;
import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptExecutor.Frame;
import com.fixmer.mared.script.MaredScriptExecutor.LoopOwner;

public class MaredForInCommand extends MaredScriptCommand {

    private static final int MAX_ITER = 100_000;

    private final String var;
    private final String arrayName;
    private final List<MaredScriptCommand> body;

    public MaredForInCommand(String var, String arrayName, List<MaredScriptCommand> body) {
        this.var = var;
        this.arrayName = arrayName;
        this.body = body;
    }

    @Override
    public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        Object raw = ctx.getVariable(arrayName);

        if (raw == null) {
            ctx.log(MaredLang.format("mared.log.mared.for_in_not_array", arrayName));
            return;
        }

        final Object[] elements = toObjectArray(raw);

        if (elements == null) {
            ctx.log(MaredLang.format("mared.log.mared.for_in_not_array", arrayName));
            return;
        }

        if (elements.length == 0) {
            ctx.log(MaredLang.format("mared.log.mared.for_in_empty", arrayName));
            return;
        }

        // F9: только в verbose
        if (MaredSettings.isVerboseScriptLog()) {
            ctx.log(MaredLang.format("mared.log.mared.for_in_start", var, arrayName, elements.length));
        }

        final boolean hadVar = ctx.hasVariable(var);
        final Object oldVar = ctx.getVariable(var);

        LoopOwner owner = new LoopOwner() {
            int index = 0;
            int iteration = 0;

            @Override
            public boolean onBodyFinished(MaredScriptContext c, MaredScriptExecutor ex, Frame bodyFrame) {
                if (bodyFrame.breakRequested) {
                    bodyFrame.breakRequested = false;
                    // F9: только в verbose
                    if (MaredSettings.isVerboseScriptLog()) {
                        c.log("[mared] break — выход из for-in");
                    }
                    restoreVar(c);
                    return false;
                }
                if (bodyFrame.continueRequested) {
                    bodyFrame.continueRequested = false;
                }
                iteration++;
                if (iteration >= MAX_ITER) {
                    c.log("[warn] for-in: превышен лимит " + MAX_ITER + " итераций");
                    restoreVar(c);
                    return false;
                }

                index++;
                if (index >= elements.length) {
                    restoreVar(c);
                    return false;
                }

                c.setVariable(var, elements[index]);
                ex.pushLoopBody(body, this);
                return true;
            }

            private void restoreVar(MaredScriptContext c) {
                if (hadVar) {
                    c.setVariable(var, oldVar);
                } else {
                    c.setVariable(var, null);
                }
            }

            @Override
            public String describe() {
                return "for $" + var + " in $" + arrayName;
            }
        };

        ctx.setVariable(var, elements[0]);
        exec.pushLoopBody(body, owner);
    }

    private static Object[] toObjectArray(Object raw) {
        if (raw == null) return null;

        if (raw instanceof List<?> list) {
            return list.toArray();
        }

        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            Object[] out = new Object[len];
            for (int i = 0; i < len; i++) {
                out[i] = Array.get(raw, i);
            }
            return out;
        }

        return null;
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "for $" + var + " in $" + arrayName; }
}