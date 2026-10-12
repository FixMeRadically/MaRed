package com.fixmer.genesis.technology.server;

import java.util.UUID;

/** Host/permission seam: local integration implements this; multiplayer transport must authorize it. */
public interface ServerModuleHost {
    UUID owner();
    String worldSession();
    boolean isServerThread();
    boolean allowed();
    void log(String message);
    void broadcast(String message);
    default void validateBroadcast(String message) {}
    default void validateDayTime(String dimension,long ticks) {}
    default void validateWeather(int duration,boolean raining,boolean thundering) {}
    /** Null means this action cannot be compensated; the engine reports partial apply explicitly. */
    default Runnable undoDayTime(String dimension){return null;}
    default Runnable undoWeather(){return null;}
    default Runnable undoBlock(String dimension,int x,int y,int z){return null;}
    void setDayTime(String dimension, long ticks);
    void setWeather(int durationTicks, boolean raining, boolean thundering);
    void validateBlock(String dimension, int x, int y, int z, String blockId);
    void setBlock(String dimension, int x, int y, int z, String blockId);
    Object nativeServer();
}
