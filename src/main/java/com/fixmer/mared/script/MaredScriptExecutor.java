package com.fixmer.mared.script;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.commands.MaredScriptCommand;

public class MaredScriptExecutor {

    /**
     * BUG-10 FIX: сколько итераций цикла выполнять за один tick.
     * 1000 = до 20000 итераций/сек, при 20 tps.
     */
    private static final int LOOP_BATCH_SIZE = 1000;

    public static class Frame {
        public MaredScriptCommand[] commands;
        public int index = 0;
        public int waitTicks = 0;
        public boolean stop = false;
        public boolean loopBody = false;
        public LoopOwner loopOwner = null;
        public boolean breakRequested = false;
        public boolean continueRequested = false;
        public boolean functionCall = false;

        public Frame(MaredScriptCommand[] commands) {
            this.commands = commands;
        }

        public Frame(List<MaredScriptCommand> commands) {
            this.commands = toArray(commands);
        }

        public boolean finished() {
            return stop || index >= commands.length;
        }

        private static MaredScriptCommand[] toArray(List<MaredScriptCommand> list) {
            if (list == null || list.isEmpty()) return EMPTY;
            int n = list.size();
            MaredScriptCommand[] arr = new MaredScriptCommand[n];
            for (int i = 0; i < n; i++) arr[i] = list.get(i);
            return arr;
        }
    }

    private static final MaredScriptCommand[] EMPTY = new MaredScriptCommand[0];

    public interface LoopOwner {
        boolean onBodyFinished(MaredScriptContext ctx, MaredScriptExecutor exec, Frame bodyFrame);
        String describe();
    }

    private final MaredScriptContext context;
    private final Deque<Frame> stack = new ArrayDeque<>();
    private boolean finished = false;
    private boolean aborted = false;

    private Object returnValue = null;
    private boolean hasReturnValue = false;

    private final boolean verboseLog;

    public MaredScriptExecutor(MaredScriptContext context, List<MaredScriptCommand> commands) {
        this.context = context;
        this.verboseLog = MaredSettings.isVerboseScriptLog();
        if (commands != null && !commands.isEmpty()) {
            stack.push(new Frame(commands));
        } else {
            finished = true;
        }
    }

    public boolean isFinished() { return finished; }

    public boolean isWaiting() {
        Frame top = stack.peek();
        return top != null && top.waitTicks > 0;
    }

    public MaredScriptContext getContext() { return context; }
    public boolean isStackEmpty() { return stack.isEmpty(); }

    public Frame peekTopFrame() { return stack.peek(); }

    public Frame pushBody(List<MaredScriptCommand> body) {
        Frame f = new Frame(body);
        stack.push(f);
        return f;
    }

    public Frame pushLoopBody(List<MaredScriptCommand> body, LoopOwner owner) {
        Frame f = new Frame(body);
        f.loopBody = true;
        f.loopOwner = owner;
        stack.push(f);
        return f;
    }

    public Frame pushFunctionBody(List<MaredScriptCommand> body, LoopOwner owner) {
        Frame f = new Frame(body);
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
            Frame f = stack.pop();
            f.stop = true;
        }
        finished = true;
    }

    public void pauseCurrent(int ticks) {
        Frame top = stack.peek();
        if (top != null) top.waitTicks = ticks;
    }

    /**
     * BUG-10 FIX: батчинг итераций циклов.
     */
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
                    if (repeat) {
                        loopIterations++;
                        if (loopIterations >= LOOP_BATCH_SIZE) {
                            return;
                        }
                        continue;
                    }
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

            if (f.stop) {
                stack.pop();
                continue;
            }

            int delay = cmd.getDelayTicks();
            if (delay > 0) {
                f.waitTicks = delay;
                if (verboseLog) {
                    context.log(MaredLang.format("mared.log.mared.pause", delay));
                }
                return;
            }

            if (f.breakRequested || f.continueRequested) {
                return;
            }
        }

        if (stack.isEmpty()) {
            if (verboseLog) {
                if (aborted) {
                    context.log(MaredLang.get("mared.log.mared.aborted"));
                } else {
                    context.log(MaredLang.get("mared.log.mared.all_done"));
                }
            }
            finished = true;
        }
    }
}