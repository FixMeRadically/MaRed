package com.fixmer.mared.commands.actions;

import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.input.MaredActionRegistry;
import com.fixmer.mared.commands.input.MaredActionRegistry.Action;

/**
 * move <direction> [on|off|toggle]
 * direction: forward | back | left | right | sneak | sprint
 * По умолчанию — on.
 */
public class MaredMoveCommand extends MaredScriptCommand {

    private final String direction;
    private final String mode;

    public MaredMoveCommand(String direction, String mode) {
        this.direction = direction.toLowerCase();
        this.mode = mode == null ? "on" : mode.toLowerCase();
    }

    private Action mapDirection() {
        switch (direction) {
            case "forward", "fwd", "w": return Action.FORWARD;
            case "back",    "b",   "s": return Action.BACK;
            case "left",    "l",   "a": return Action.LEFT;
            case "right",   "r",   "d": return Action.RIGHT;
            case "sneak",   "shift":    return Action.SNEAK;
            case "sprint",  "run":      return Action.SPRINT;
            default: return null;
        }
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        Action a = mapDirection();
        if (a == null) {
            ctx.log("[error] move: unknown direction: " + direction);
            return true;
        }
        switch (mode) {
            case "off", "stop", "release" -> {
                MaredActionRegistry.release(a);
                ctx.log("[action] move " + direction + " off");
            }
            case "toggle" -> {
                boolean now = !MaredActionRegistry.isPressed(a);
                MaredActionRegistry.toggle(a, now);
                ctx.log("[action] move " + direction + " toggle → " + now);
            }
            default -> {
                MaredActionRegistry.press(a);
                ctx.log("[action] move " + direction + " on");
            }
        }
        return true;
    }

    @Override public int getDelayTicks() { return 0; }
    @Override public String describe() { return "move " + direction + " " + mode; }
}