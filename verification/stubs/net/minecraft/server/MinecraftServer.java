package net.minecraft.server;
public class MinecraftServer { public Players getPlayerList(){return new Players();} public static class Players { public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID id){return null;} public net.minecraft.server.level.ServerPlayer getPlayerByName(String name){return null;} } }
