package com.fixmer.mared.technology.catalog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;

/** Session identity includes dispatcher replacement and explicit catalog refresh. Main thread only. */
public final class MinecraftCommandTools {
    public record Session(ClientPacketListener connection, Object dispatcher, long revision,
                          BrigadierCommandTools<SharedSuggestionProvider> tools) {
        public boolean current() {
            var now = Minecraft.getInstance().getConnection();
            return now == connection && now != null && now.getCommands() == dispatcher
                && com.fixmer.mared.commands.registry.MaredCommandRegistry.revision() == revision;
        }
    }
    private MinecraftCommandTools() {}
    public static Session session() {
        var connection = Minecraft.getInstance().getConnection();
        MinecraftCommandCatalog.poll(connection);
        if (connection == null) return null;
        var dispatcher = connection.getCommands();
        return new Session(connection, dispatcher, com.fixmer.mared.commands.registry.MaredCommandRegistry.revision(),
            new BrigadierCommandTools<>(dispatcher, connection.getSuggestionsProvider()));
    }
}
