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
        double seconds = switch (unit.toLowerCase()) {
            case "ticks", "t"          -> amount / 20.0;
            case "ms", "milliseconds"  -> amount / 1000.0;
            case "minutes", "m"        -> amount * 60.0;
            case "seconds", "s", ""    -> amount;
            default -> amount;
        };
        return (int) Math.round(seconds * 20);
    }

    @Override public String describe() { return "wait " + amount + " " + unit; }
}