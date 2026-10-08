package com.fixmer.genesis.technology.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;



/**
 * Executor стека кадров.
 *
 * Один tick = одна порция работы. Внутри tick'а мы можем выполнить
 * ограниченную ExecutionLimits порцию работы, затем отдаём управление.
 */
public class FrameExecutor {

    private final com.fixmer.genesis.technology.runtime.ExecutionLimits limits;
    private static final int FRAME_POOL_SIZE = 16;
    private static final Instruction[] EMPTY = new Instruction[0];

    // ============================================================
    //  Frame
    // ============================================================

    public static final class Frame {
        public Instruction[] commands = EMPTY;
        public int index = 0;
        public int waitTicks = 0;
        public boolean loopBody = false;
        public LoopOwner loopOwner = null;
        public boolean breakRequested = false;
        public boolean continueRequested = false;
        public boolean functionCall = false;
        public int retryIndex = -1;
        public int retryTicks;

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
            retryIndex = -1;
            retryTicks = 0;
        }
    }

    public interface LoopOwner {
        boolean onBodyFinished(FrameExecutor exec, Frame bodyFrame);
        String describe();
        default void onDiscard() {}
    }

    // ============================================================
    //  State
    // ============================================================

    private final ExecutionScope scope;
    private final Deque<Frame> stack = new ArrayDeque<>(16);
    private final Deque<Frame> pool = new ArrayDeque<>(FRAME_POOL_SIZE);
    public interface Instruction { void execute(FrameExecutor executor) throws Exception; default int getDelayTicks(){return 0;} default String describe(){return getClass().getSimpleName();} }
    protected void trace(String kind,Object value) {}

    private boolean debuggerPaused = false;
    private boolean singleStep;

    private volatile boolean finished = false;
    private boolean yielded;
    private long totalSteps;
    private volatile Throwable failure;
    private volatile boolean stopRequested;
    private com.fixmer.genesis.technology.runtime.ExecutionScope.Lease ownership;
    public record Snapshot(boolean finished, boolean waiting, boolean paused, long steps, int depth, Throwable failure) {}
    private volatile Snapshot snapshot = new Snapshot(false, false, false, 0, 0, null);
    public Snapshot snapshot() { return snapshot; }
    /** Nonblocking request; stack cleanup runs on the executor's next owner tick. */
    public void requestStop() { stopRequested = true; }
    public synchronized void fail(Throwable error) { failure = error; stopAll(); }
    public Throwable failure() { return failure; }
    private boolean aborted = false;
    private Object returnValue = null;
    private boolean hasReturnValue = false;

    public FrameExecutor(List<Instruction> commands,ExecutionLimits limits,ExecutionScope scope){
        this(limits,scope);initialize(commands);
    }
    protected FrameExecutor(ExecutionLimits limits,ExecutionScope scope){this.limits=java.util.Objects.requireNonNull(limits);this.scope=scope;}
    protected final void initialize(List<Instruction> commands){
        if(commands!=null&&!commands.isEmpty())stack.push(borrowFrame(toArray(commands)));else finished=true;
        publishSnapshot();
        if(!finished&&scope!=null)ownership=scope.own(ExecutionScope.Kind.EXECUTOR,this::requestStop);
    }

    // ============================================================
    //  Frame pool
    // ============================================================

    private Frame borrowFrame(Instruction[] commands) {
        if (stack.size() >= limits.stackDepth()) throw new IllegalStateException("Script stack limit exceeded");
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
    public boolean isStackEmpty() { return stack.isEmpty(); }

    public boolean isWaiting() {
        Frame top = stack.peek();
        return top != null && (top.waitTicks > 0 || yielded);
    }

    public Frame peekTopFrame() { return stack.peek(); }

    public int getStackDepth() { return stack.size(); }

    public Frame pushInstructions(List<Instruction> body) {
        Frame f = borrowFrame(toArray(body));
        stack.push(f);
        return f;
    }

    public Frame pushLoopInstructions(List<Instruction> body, LoopOwner owner) {
        Frame f = borrowFrame(toArray(body));
        f.loopBody = true;
        f.loopOwner = owner;
        stack.push(f);
        return f;
    }

    public Frame pushFunctionInstructions(List<Instruction> body, LoopOwner owner) {
        Frame f = borrowFrame(toArray(body));
        f.loopOwner = owner;
        f.functionCall = true;
        stack.push(f);
        return f;
    }

    public Frame findEnclosingLoop() {
        for (Frame f : stack) {
            if (f.functionCall) return null;
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
            discardFrame(top);
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
            discardFrame(top);
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

    public void resumeFromDebugger() {
        debuggerPaused = false;
    }

    public void pauseFromDebugger() {
        debuggerPaused = true;
    }

    public void stepOverDebugger() {
        debuggerPaused = false;
        singleStep=true;
        try { tick(); } finally { singleStep=false;debuggerPaused=true;publishSnapshot(); }
    }

    private void discardFrame(Frame f) {
        try { if (f.loopOwner != null) f.loopOwner.onDiscard(); }
        finally { releaseFrame(f); }
    }

    public synchronized void stopAll() {
        aborted = true;
        try {
            while (!stack.isEmpty()) {
                try { discardFrame(stack.pop()); }
                catch (RuntimeException error) { if (failure == null) failure = error; }
            }
        } finally {
            finished = true;
            publishSnapshot();
        }
    }

    private void publishSnapshot() {
        if (finished && ownership != null) ownership.close();
        if (failure != null && scope != null) scope.fail(failure);
        Frame top = stack.peek();
        snapshot = new Snapshot(finished, !finished && (yielded || top != null && top.waitTicks > 0),
            debuggerPaused, totalSteps, stack.size(), failure);
    }

    public void pauseCurrent(int ticks) {
        Frame top = stack.peek();
        if (top != null) top.waitTicks = ticks;
    }

    // ============================================================
    //  Tick
    // ============================================================

    /** Re-execute the current instruction on a later tick; state belongs to this frame. */
    public int retryCurrentInstruction() {
        Frame f = stack.peek();
        if (f == null || f.index == 0) throw new IllegalStateException("No active instruction");
        int instruction = f.index - 1;
        if (f.retryIndex != instruction) { f.retryIndex = instruction; f.retryTicks = 0; }
        f.index = instruction;
        yielded = true;
        return ++f.retryTicks;
    }

    public void completeRetry() {
        Frame f = stack.peek();
        if (f != null) { f.retryIndex = -1; f.retryTicks = 0; }
    }

    public synchronized void tick() {
        try {
            if (stopRequested && !finished) stopAll();
            tickInternal();
        } finally { publishSnapshot(); }
    }

    private void tickInternal() {
        if (finished) return;

        if (debuggerPaused) return;

        yielded = false;
        int steps = 0;
        long started = System.nanoTime();

        while (!stack.isEmpty()) {
            if (stopRequested) { stopAll(); return; }
            Frame f = stack.peek();

            if (f.waitTicks > 0) {
                f.waitTicks--;
                if (f.waitTicks == 0) trace("pause_done",null);
                return;
            }

            // Completing a plain frame requires no script instruction or host callback.
            // Let a finite one-command expression finish even if its host call consumed the budget.
            if (f.index >= f.commands.length && f.loopOwner == null) {
                stack.pop(); releaseFrame(f); continue;
            }

            if (steps >= (singleStep?1:limits.stepsPerTick()) || (steps > 0 && System.nanoTime() - started >= limits.nanosPerTick())) return;
            steps++;
            if (++totalSteps > limits.totalSteps()) {
                failure = new IllegalStateException("Script instruction limit exceeded");
                trace("error",failure);
                stopAll(); return;
            }

            if (f.index >= f.commands.length) {
                stack.pop();
                if (f.loopOwner != null) {
                    boolean repeat;
                    try { repeat = f.loopOwner.onBodyFinished(this, f); }
                    catch (Exception error) {
                        failure = error;
                        try { discardFrame(f); } catch(RuntimeException cleanup){if(cleanup!=error)error.addSuppressed(cleanup);}
                        stopAll();
                        trace("error",error); return;
                    }
                    releaseFrame(f);

                    if (repeat) {
                        continue;
                    }
                } else {
                    releaseFrame(f);
                }
                continue;
            }

            Instruction cmd = f.commands[f.index];
            f.index++;

            trace("command",cmd.describe());

            try {
                cmd.execute(this);
            } catch (Exception e) {
                failure = e;
                trace("error",e);
                stopAll();
                return;
            }

            if (finished || yielded) return;

            int delay = cmd.getDelayTicks();
            if (delay > 0) {
                f.waitTicks = delay;
                trace("pause",delay);
                return;
            }

            if (f.breakRequested || f.continueRequested) return;
        }

        if (stack.isEmpty()) {
            trace(aborted?"aborted":"all_done",null);
            finished = true;
        }
    }

    // ============================================================
    //  Util
    // ============================================================

    static Instruction[] toArray(List<Instruction> list) {
        if (list == null || list.isEmpty()) return EMPTY;
        int n = list.size();
        Instruction[] arr = new Instruction[n];
        for (int i = 0; i < n; i++) arr[i] = list.get(i);
        return arr;
    }
}
