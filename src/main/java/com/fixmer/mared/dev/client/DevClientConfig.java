package com.fixmer.mared.dev.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/** dev.json is real JSON; legacy Java-properties content is accepted for migration. */
public final class DevClientConfig {
    private static volatile Integer cached;
    private DevClientConfig() { }
    public static Path configPath() { try { com.fixmer.mared.dev.DevPaths.migrateLegacy(); } catch (IOException e) { throw new IllegalStateException("Dev config migration failed", e); }
        return com.fixmer.mared.dev.DevPaths.file("client.json"); }
    public static synchronized int controlPort() {
        if (cached != null) return cached;
        int port = 27670;
        Path path = configPath();
        try {
            if (Files.isRegularFile(path)) {
                String text = Files.readString(path,StandardCharsets.UTF_8);
                if (text.stripLeading().startsWith("{")) {
                    JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                    if (json.has("controlPort")) port = json.get("controlPort").getAsInt();
                } else {
                    Properties props = new Properties(); props.load(new StringReader(text));
                    port = Integer.parseInt(props.getProperty("controlPort","27670").trim());
                }
                if (port<1 || port>65535) throw new IOException("controlPort must be 1..65535");
            }
        } catch (Exception e) { throw new IllegalStateException("Invalid "+path+": "+e.getMessage(),e); }
        return cached = port;
    }
    public static synchronized void reload() { cached = null; }
    public static synchronized void setControlPort(int port) throws IOException {
        if (port<1 || port>65535) throw new IllegalArgumentException("controlPort must be 1..65535");
        Path path = configPath(); Files.createDirectories(path.getParent());
        JsonObject json = new JsonObject();
        if (Files.isRegularFile(path)) {
            String old = Files.readString(path,StandardCharsets.UTF_8);
            if (old.stripLeading().startsWith("{")) json = JsonParser.parseString(old).getAsJsonObject();
        }
        json.addProperty("controlPort",port);
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(json)+"\n",StandardCharsets.UTF_8);
        cached = port;
    }
}
