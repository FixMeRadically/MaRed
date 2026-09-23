package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

public class MaredWaitCommand extends MaredScriptCommand {

    private final double amount;
    private final String unit;

    public MaredWaitCommand(double amount, String unit) {
        this.amount = amount;
        this.unit = unit == null ? "seconds" : unit;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        // сама задержка выполняется через getDelayTicks()
        return true;
    }

    @Override
    public int getDelayTicks() {
        String u = unit.toLowerCase();
        double seconds;
        switch (u) {
            // F8b FIX: tick / ticks / t
            case "tick", "ticks", "t" -> seconds = amount / 20.0;
            // F8b FIX: ms / millisecond / milliseconds
            case "ms", "millisecond", "milliseconds" -> seconds = amount / 1000.0;
            // F8b FIX: minute / minutes / m
            case "minute", "minutes", "m" -> seconds = amount * 60.0;
            // F8b FIX: second / seconds / s / ""
            case "second", "seconds", "s", "" -> seconds = amount;
            default -> seconds = amount;
        }
        return (int) Math.round(seconds * 20);
    }

    @Override public String describe() { return "wait " + amount + " " + unit; }
}