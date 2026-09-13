package com.fixmer.mared.script;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.fixmer.mared.script.commands.MaredScriptCommand;

public class MaredScriptExecutor {

    public static class Frame {
        public List<MaredScriptCommand> commands;
        public int index = 0;
        public int waitTicks = 0;
        public boolean stop = false;
        public boolean loopBody = false;
        public LoopOwner loopOwner = null;
        public boolean breakRequested = false;
        public boolean continueRequested = false;

        public Frame(List<MaredScriptCommand> commands) {
            this.commands = commands;
        }

        public boolean finished() {
            return stop || index >= commands.size();
        }
    }

    public interface LoopOwner {
        boolean onBodyFinished(MaredScriptContext ctx, MaredScriptExecutor exec, Frame bodyFrame);
        String describe();
    }

    private final MaredScriptContext context;
    private final Deque<Frame> stack = new ArrayDeque<>();
    private boolean finished = false;

    public MaredScriptExecutor(MaredScriptContext context, List<MaredScriptCommand> commands) {
        this.context = context;
        if (commands != null && !commands.isEmpty()) {
            stack.push(new Frame(commands));
        } else {
            finished = true;
        }
    }

    public boolean isFinished() { return finished; }
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

    public void stopAll() {
        while (!stack.isEmpty()) stack.pop().stop = true;
        finished = true;
    }

    public void pauseCurrent(int ticks) {
        Frame top = stack.peek();
        if (top != null) top.waitTicks = ticks;
    }

    public void tick() {
        if (finished) return;

        while (!stack.isEmpty()) {
            Frame f = stack.peek();

            if (f.waitTicks > 0) {
                f.waitTicks--;
                if (f.waitTicks == 0) context.log(MaredLang.get("mared.log.mared.pause_done"));
                return;
            }

            if (f.index >= f.commands.size()) {
                stack.pop();
                if (f.loopBody && f.loopOwner != null) {
                    boolean repeat = f.loopOwner.onBodyFinished(context, this, f);
                    if (repeat) return;
                }
                continue;
            }

            MaredScriptCommand cmd = f.commands.get(f.index);
            f.index++;

            context.log(MaredLang.format("mared.log.mared.command", cmd.describe()));
            try {
                cmd.execute(context, this);
            } catch (Exception e) {
                context.log("[error] " + e.getMessage());
                stopAll();
                return;
            }

            if (f.stop) {
                stack.pop();
                continue;
            }

            int delay = cmd.getDelayTicks();
            if (delay > 0) {
                f.waitTicks = delay;
                context.log(MaredLang.format("mared.log.mared.pause", delay));
                return;
            }

            if (f.breakRequested || f.continueRequested) {
                return;
            }
        }

        if (stack.isEmpty()) {
            context.log(MaredLang.get("mared.log.mared.all_done"));
            finished = true;
        }
    }
}