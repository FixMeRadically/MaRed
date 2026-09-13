package com.fixmer.mared.script.commands;

import com.fixmer.mared.script.MaredScriptContext;

public class MaredGiveCommand extends MaredScriptCommand {

    private final String target;
    private final String item;
    private final int count;

    public MaredGiveCommand(String target, String item, int count) {
        this.target = target;
        this.item = item;
        this.count = count;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        String t = ctx.substitute(target);
        String it = ctx.substitute(item);
        String cmd = "give " + t + " " + it + " " + count;

        if (ctx.getServer() == null) return false;

        var dispatcher = ctx.getServer().getCommands().getDispatcher();
        var source = ctx.getServer().createCommandSourceStack();

        try {
            // В новых версиях MC нужно передавать строку, а не ParseResults.
            dispatcher.execute(cmd, source);
            return true;
        } catch (Exception e) {
            ctx.log("[error] give: " + e.getMessage());
            return false;
        }
    }

    @Override public int getDelayTicks() { return 0; }

    @Override public String describe() { return "give " + target + " " + item + " " + count; }
}