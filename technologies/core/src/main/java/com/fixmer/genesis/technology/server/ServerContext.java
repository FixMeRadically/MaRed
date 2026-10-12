package com.fixmer.genesis.technology.server;

import java.util.UUID;
import java.util.function.LongConsumer;

/** Stable parent-loaded API. No Minecraft, Commands, MR or client classes. Not an OS sandbox. */
public interface ServerContext {
    String API_VERSION = "1";
    interface Subscription extends AutoCloseable { @Override void close(); }
    UUID owner();
    String worldSession();
    long generation();
    void log(String message);
    void broadcast(String message);
    void setDayTime(String dimension, long ticks);
    void setWeather(int durationTicks, boolean raining, boolean thundering);
    void setBlock(String dimension, int x, int y, int z, String blockId);
    String state(String key);
    void state(String key, String value);
    Subscription onTick(LongConsumer listener);
    Subscription afterTicks(int ticks, Runnable action);
    /** Advanced Gradle modules can cast this to MinecraftServer. Bypasses managed mutation staging. */
    Object nativeServer();
}
