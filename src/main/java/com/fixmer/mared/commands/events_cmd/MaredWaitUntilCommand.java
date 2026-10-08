package com.fixmer.mared.commands.events_cmd;
import com.fixmer.mared.commands.control.MaredIfCommand;
import com.fixmer.mared.commands.engine.*;
/** Immutable instruction: wait state is per execution frame, never shared by event invocations. */
public class MaredWaitUntilCommand extends MaredScriptCommand {
    private static final int MAX_WAIT_TICKS = 24000;
    private final String condition;
    public MaredWaitUntilCommand(String condition) { this.condition = condition; }
    @Override public void execute(MaredScriptContext ctx, MaredScriptExecutor exec) {
        if (MaredIfCommand.evaluateStatic(condition, ctx)) { exec.completeRetry(); return; }
        if (exec.retryCurrentInstruction() >= MAX_WAIT_TICKS)
            throw new IllegalStateException("wait_until timeout: " + condition);
    }
    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "wait_until " + condition; }
}
