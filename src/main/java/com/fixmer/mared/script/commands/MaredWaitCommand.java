package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

public class MaredWaitCommand extends MaredScriptCommand {

    private final int ticks;

    public MaredWaitCommand(double amount, String unit) {
        this.ticks = convertToTicks(amount, unit);
    }

    private static int convertToTicks(double amount, String unit) {
        if (unit == null) unit = "seconds";
        switch (unit.toLowerCase()) {
            case "ticks":        return (int) Math.max(1, amount);
            case "milliseconds": return (int) Math.max(1, Math.round(amount / 50.0));
            case "seconds":      return (int) Math.max(1, Math.round(amount * 20.0));
            case "minutes":      return (int) Math.max(1, Math.round(amount * 1200.0));
            default:             return (int) Math.max(1, Math.round(amount * 20.0));
        }
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        return true;
    }

    @Override
    public int getDelayTicks() {
        return ticks;
    }

    @Override
    public String describe() {
        return "wait " + ticks + " ticks";
    }
}