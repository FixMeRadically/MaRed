package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.MaredLang;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** Starts only the bundled setup UI. It never stops or adopts the current game. */
public final class ServerSetupLauncher59 {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile String statusKey = "mared.server59.ready";
    private ServerSetupLauncher59() {}
    public static boolean busy() { return BUSY.get(); }
    public static String status() { return MaredLang.get(statusKey); }

    public static void open() {
        if (!BUSY.compareAndSet(false, true)) return;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                || System.getenv("LOCALAPPDATA") == null) {
            statusKey = "mared.server59.windows"; BUSY.set(false); return;
        }
        if (System.getenv("GENESIS_FIRST58_ROOT") != null || System.getenv("GENESIS_SUPERVISOR_TOKEN") != null) {
            statusKey = "mared.server59.unmanaged"; BUSY.set(false); return;
        }
        final String account;
        final String clientProfile;
        final String neo;
        final String sessionJson;
        try {
            Minecraft mc = Minecraft.getInstance();
            clientProfile = mc.gameDirectory.toPath().toAbsolutePath().normalize().toString();
            account = mc.getUser().getProfileId().toString();
            sessionJson = new com.google.gson.Gson().toJson(Map.of(
                "schema", 61, "pid", ProcessHandle.current().pid(), "profile", clientProfile,
                "account", account, "name", mc.getUser().getName(),
                "accessToken", mc.getUser().getAccessToken(), "userType", mc.getUser().getType().getName(),
                "issuedAt", System.currentTimeMillis()));
            neo = ModList.get().getModContainerById("neoforge")
                .orElseThrow().getModInfo().getVersion().toString();
        } catch (RuntimeException unavailable) {
            statusKey = "mared.server59.failed"; BUSY.set(false); return;
        }
        statusKey = "mared.server59.opening";
        Thread thread = new Thread(() -> {
            Path sessionFile = null;
            try {
                Path base = Path.of(System.getenv("LOCALAPPDATA"), "Genesis", "setup-runtime").toAbsolutePath().normalize();
                ServerSetupBundle59.ensureDirectory(base);
                Path runtime = Files.createTempDirectory(base, "setup59-");
                ServerSetupBundle59.privateDirectory(runtime);
                ServerSetupBundle59.unpack(runtime, ServerSetupLauncher59.class.getResourceAsStream("/genesis/server59/controller.zip"));
                sessionFile = runtime.resolve("client-session61.json");
                Files.writeString(sessionFile, sessionJson, java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                Path log = runtime.resolve("setup.log");
                Process process;
                try {
                    process = new ProcessBuilder("py.exe", "-3", runtime.resolve("tools/server_setup.py").toString(),
                        runtime.toString(), "--client-pid", Long.toString(ProcessHandle.current().pid()),
                        "--client-profile", clientProfile, "--session-file", sessionFile.toString(),
                        "--account", account, "--neoforge", neo)
                        .directory(runtime.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                } catch (IOException missingPython) {
                    statusKey = "mared.server59.python";
                    return;
                }
                statusKey = "mared.server59.opened";
                int result = process.waitFor();
                statusKey = result == 0 ? "mared.server59.closed" : "mared.server59.failed";
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); statusKey = "mared.server59.failed";
            } catch (IOException | RuntimeException failed) {
                statusKey = "mared.server59.failed";
            } finally {
                if (sessionFile != null) try { Files.deleteIfExists(sessionFile); } catch (IOException ignored) { }
                BUSY.set(false);
            }
        }, "genesis-server-setup59");
        thread.setDaemon(true);
        thread.start();
    }

}
