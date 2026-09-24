package com.fixmer.mared.commands.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredSettings;

/**
 * Executor стека кадров.
 *
 * Один tick = одна порция работы. Внутри tick'а мы можем выполнить
 * до LOOP_BATCH_SIZE итераций циклов, чтобы не тормозить сервер.
 */
public class MaredScriptExecutor {

    private static final int LOOP_BATCH_SIZE = 5000;
    private static final int FRAME_POOL_SIZE = 16;
    private static final MaredScriptCommand[] EMPTY = new MaredScriptCommand[0];

    // ============================================================
    //  Frame
    // ============================================================

    public static final class Frame {
        public MaredScriptCommand[] commands = EMPTY;
        public int index = 0;
        public int waitTicks = 0;
        public boolean loopBody = false;
        public LoopOwner loopOwner = null;
        public boolean breakRequested = false;
        public boolean continueRequested = false;
        public boolean functionCall = false;

        boolean finished() { return index >= commands.length; }

        void reset() {
            commands = EMPTY;
            index = 0;
            waitTicks = 0;
            loopBody = false;
            loopOwner = null;
            breakRequested = false;
            continueRequested = false;
            functionCall = false;
        }
    }

    public interface LoopOwner {
        boolean onBodyFinished(MaredScriptContext ctx, MaredScriptExecutor exec, Frame bodyFrame);
        String describe();
    }

    // ============================================================
    //  State
    // ============================================================

    private final MaredScriptContext context;
    private final Deque<Frame> stack = new ArrayDeque<>(16);
    private final Deque<Frame> pool = new ArrayDeque<>(FRAME_POOL_SIZE);
    private final boolean verboseLog;

    private boolean finished = false;
    private boolean aborted = false;
    private Object returnValue = null;
    private boolean hasReturnValue = false;

    public MaredScriptExecutor(MaredScriptContext context, List<MaredScriptCommand> commands) {
        this.context = context;
        this.verboseLog = MaredSettings.isVerboseScriptLog();

        if (commands != null && !commands.isEmpty()) {
            stack.push(borrowFrame(toArray(commands)));
        } else {
            finished = true;
        }
    }

    // ============================================================
    //  Frame pool
    // ============================================================

    private Frame borrowFrame(MaredScriptCommand[] commands) {
        Frame f = pool.pollFirst();
        if (f == null) f = new Frame();
        f.commands = commands;
        return f;
    }

    private void releaseFrame(Frame f) {
        if (f == null) return;
        f.reset();
        if (pool.size() < FRAME_POOL_SIZE) pool.addLast(f);
    }

    // ============================================================
    //  Public API
    // ============================================================

    public boolean isFinished() { return finished; }
    public MaredScriptContext getContext() { return context; }
    public boolean isStackEmpty() { return stack.isEmpty(); }

    public boolean isWaiting() {
        Frame top = stack.peek();
        return top != null && top.waitTicks > 0;
    }

    public Frame peekTopFrame() { return stack.peek(); }

    public int getStackDepth() { return stack.size(); }

    public Frame pushBody(List<MaredScriptCommand> body) {
        Frame f = borrowFrame(toArray(body));
        stack.push(f);
        return f;
    }

    public Frame pushLoopBody(List<MaredScriptCommand> body, LoopOwner owner) {
        Frame f = borrowFrame(toArray(body));
        f.loopBody = true;
        f.loopOwner = owner;
        stack.push(f);
        return f;
    }

    public Frame pushFunctionBody(List<MaredScriptCommand> body, LoopOwner owner) {
        Frame f = borrowFrame(toArray(body));
        f.loopBody = true;
        f.loopOwner = owner;
        f.functionCall = true;
        stack.push(f);
        return f;
    }

    public Frame findEnclosingLoop() {
        for (Frame f : stack) {
            if (f.loopBody) return f;
        }
        return null;
    }

    public Frame findEnclosingFunction() {
        for (Frame f : stack) {
            if (f.functionCall) return f;
        }
        return null;
    }

    public void abortFramesUpTo(Frame target) {
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top == target) {
                top.index = top.commands.length;
                return;
            }
            stack.pop();
            releaseFrame(top);
        }
    }

    public void abortToFunction(Frame fnFrame) {
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top == fnFrame) {
                top.index = top.commands.length;
                return;
            }
            stack.pop();
            releaseFrame(top);
        }
    }

    public void setReturnValue(Object value) {
        this.returnValue = value;
        this.hasReturnValue = true;
    }

    public Object getReturnValue() { return returnValue; }
    public boolean hasReturnValue() { return hasReturnValue; }

    public void clearReturnValue() {
        this.returnValue = null;
        this.hasReturnValue = false;
    }

    public void stopAll() {
        aborted = true;
        while (!stack.isEmpty()) {
            releaseFrame(stack.pop());
        }
        finished = true;
    }

    public void pauseCurrent(int ticks) {
        Frame top = stack.peek();
        if (top != null) top.waitTicks = ticks;
    }

    // ============================================================
    //  Tick
    // ============================================================

    public void tick() {
        if (finished) return;

        int loopIterations = 0;

        while (!stack.isEmpty()) {
            Frame f = stack.peek();

            if (f.waitTicks > 0) {
                f.waitTicks--;
                if (f.waitTicks == 0 && verboseLog) {
                    context.log(MaredLang.get("mared.log.mared.pause_done"));
                }
                return;
            }

            if (f.index >= f.commands.length) {
                stack.pop();
                if (f.loopBody && f.loopOwner != null) {
                    boolean repeat = f.loopOwner.onBodyFinished(context, this, f);
                    releaseFrame(f);

                    if (repeat) {
                        loopIterations++;
                        if (loopIterations >= LOOP_BATCH_SIZE) return;
                        continue;
                    }
                } else {
                    releaseFrame(f);
                }
                continue;
            }

            MaredScriptCommand cmd = f.commands[f.index];
            f.index++;

            if (verboseLog) {
                context.log(MaredLang.format("mared.log.mared.command", cmd.describe()));
            }

            try {
                cmd.execute(context, this);
            } catch (Exception e) {
                context.log("[error] " + e.getMessage());
                stopAll();
                return;
            }

            if (finished) return;

            int delay = cmd.getDelayTicks();
            if (delay > 0) {
                f.waitTicks = delay;
                if (verboseLog) {
                    context.log(MaredLang.format("mared.log.mared.pause", delay));
                }
                return;
            }

            if (f.breakRequested || f.continueRequested) return;
        }

        if (stack.isEmpty()) {
            if (verboseLog) {
                context.log(MaredLang.get(aborted
                    ? "mared.log.mared.aborted"
                    : "mared.log.mared.all_done"));
            }
            finished = true;
        }
    }

    // ============================================================
    //  Util
    // ============================================================

    static MaredScriptCommand[] toArray(List<MaredScriptCommand> list) {
        if (list == null || list.isEmpty()) return EMPTY;
        int n = list.size();
        MaredScriptCommand[] arr = new MaredScriptCommand[n];
        for (int i = 0; i < n; i++) arr[i] = list.get(i);
        return arr;
    }
}