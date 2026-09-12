package com.fixmer.mared;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MaredSayCommand extends MaredScriptCommand {

    private final String text;
    private final String scope; // "all" | "self" | "player:<ник>"

    public MaredSayCommand(String text, String scope) {
        this.text = text;
        this.scope = scope == null ? "all" : scope;
    }

    @Override
    public boolean execute(MaredScriptContext ctx) {
        MinecraftServer server = ctx.getServer();
        if (server == null) return false;

        String message = ctx.substitute(text);
        Component component = Component.literal("[Mared] " + message);

        switch (scope) {
            case "self":
                if (ctx.getInitiator() != null) {
                    ctx.getInitiator().sendSystemMessage(component);
                }
                break;
            case "all":
                server.getPlayerList().broadcastSystemMessage(component, false);
                break;
            default:
                if (scope.startsWith("player:")) {
                    String targetName = scope.substring("player:".length());
                    ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
                    if (target != null) {
                        target.sendSystemMessage(component);
                    }
                }
                break;
        }

        ctx.log("[say → " + scope + "] " + message);
        return true;
    }

    @Override
    public String describe() {
        return "say \"" + text + "\" scope=" + scope;
    }
}