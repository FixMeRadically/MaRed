package com.fixmer.mared.commands.server_cmd;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import java.util.Locale;

/** Validated, immutable tick delay; invalid durations never become a zero wait. */
public class MaredWaitCommand extends MaredScriptCommand {
    private final double amount;
    private final String unit;
    private final int ticks;
    public MaredWaitCommand(double amount,String unit) {
        this.amount=amount;
        this.unit=unit==null?"seconds":unit.toLowerCase(Locale.ROOT);
        double scale=switch(this.unit) {
            case "tick","ticks","t" -> 1;
            case "ms","millisecond","milliseconds" -> 0.02;
            case "minute","minutes","m" -> 1200;
            case "second","seconds","s","" -> 20;
            default -> throw new IllegalArgumentException("Unknown wait unit: "+unit);
        };
        double duration=amount*scale;
        if(!Double.isFinite(amount)||amount<0||!Double.isFinite(duration)||duration>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Wait duration must be finite, nonnegative and at most 2147483647 ticks");
        ticks=Math.toIntExact(Math.round(duration));
    }
    @Override public boolean execute(MaredScriptContext ctx){return true;}
    @Override public int getDelayTicks(){return ticks;}
    @Override public String describe(){return "wait "+amount+" "+unit;}
}
