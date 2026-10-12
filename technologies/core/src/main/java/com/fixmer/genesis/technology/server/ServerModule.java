package com.fixmer.genesis.technology.server;

/** Java runtime module, not a NeoForge @Mod entrypoint. Callbacks run on the server thread. */
public interface ServerModule {
    void onEnable(ServerContext context) throws Exception;
    default void onDisable() throws Exception {}
}
