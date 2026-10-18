package com.fixmer.mared.technology.scripts;

/** Captured on the client thread; an automatic apply must not cross world/player ownership. */
public record NativeWorldTarget(net.minecraft.client.server.IntegratedServer server, java.util.UUID owner) {
    public static NativeWorldTarget capture() {
        var mc=net.minecraft.client.Minecraft.getInstance();var server=mc.getSingleplayerServer();
        return server==null||server.isPublished()||mc.player==null?null:new NativeWorldTarget(server,mc.player.getUUID());
    }
    public boolean current() {
        var mc=net.minecraft.client.Minecraft.getInstance();
        return mc.getSingleplayerServer()==server&&!server.isPublished()&&mc.player!=null&&owner.equals(mc.player.getUUID());
    }
}
