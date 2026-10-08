package com.fixmer.genesis.technology.runtime;
/** Cooperative limits: a blocking host operation is not preempted. */
public record ExecutionLimits(int stepsPerTick, long totalSteps, int stackDepth, long nanosPerTick) {
    public static final ExecutionLimits DEFAULT = new ExecutionLimits(1000, 1_000_000, 128, 2_000_000);
    public ExecutionLimits {
        if (stepsPerTick < 1 || totalSteps < stepsPerTick || stackDepth < 1 || nanosPerTick < 1)
            throw new IllegalArgumentException("Invalid execution limits");
    }
}
