package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.storage.DevResumeStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-only cooperative restart. Minecraft.stop() leaves saving to the normal game shutdown path. */
public final class DevRestartService {

    private DevRestartService() {}

    private static final String ENV_FLAG = "GENESIS_SUPERVISOR";
    private static final String ENV_PORT = "GENESIS_SUPERVISOR_PORT";
    
    

    private static volatile boolean restartInProgress = false;

    public static boolean isRestartInProgress() { return restartInProgress; }

    /** Запущен ли клиент супервизором. */
    public static boolean isUnderSupervisor() {
        String v = System.getenv(ENV_FLAG);
        return "1".equals(v) && System.getenv("GENESIS_SUPERVISOR_TOKEN") != null;
    }

    /** Порт HTTP-контроля супервизора. -1, если не под супервизором. */
    public static int supervisorPort() {
        if (!isUnderSupervisor()) return -1;
        String raw = System.getenv(ENV_PORT);
        if (raw == null || raw.isBlank()) return -1;
        try {
            int n = Integer.parseInt(raw.trim());
            if (n < 1 || n > 65535) return -1;
            return n;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Пользовательское действие «перезапустить клиент».
     * Возвращает true, если запрос был принят (не означает, что супервизор
     * уже подтвердил — подтверждение придёт асинхронно).
     */
    public static boolean requestRestart() {
        var future = restartAsync();
        return !future.isDone() || future.getNow(false);
    }

    public static synchronized java.util.concurrent.CompletableFuture<Boolean> restartAsync() {
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        if (!DevMode.isEnabled()) {
            message(MaredLang.get("mared.dev.restart.requires_dev"));
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (!isUnderSupervisor()) {
            message(MaredLang.get("mared.dev.restart.no_supervisor"));
            Mared.LOGGER.warn(
                "[Mared] restart requested but GENESIS_SUPERVISOR is not set");
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (supervisorPort() < 1) { message("Invalid supervisor port in environment; restart declined."); return java.util.concurrent.CompletableFuture.completedFuture(false); }
        if (restartInProgress) {
            message(MaredLang.get("mared.dev.restart.already"));
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        final com.fixmer.mared.dev.storage.DevResumeStore.Target target;
        try { target = DevSessionResume.captureCurrent(); }
        catch (RuntimeException e) { message(e.getMessage()); DevActivationService.lastStatus = e.getMessage(); return java.util.concurrent.CompletableFuture.completedFuture(false); }
        restartInProgress = true;
        DevRestartOverlay.show("mared.dev.restart.animation.requesting");
        DevActivationService.lastStatus = MaredLang.get("mared.dev.menu.requesting");

        int port = supervisorPort();
        message(MaredLang.format("mared.dev.restart.requesting", port));
        Mared.LOGGER.info("[Mared] restart requested, supervisor port={}", port);

        Thread t = new Thread(() -> performRestart(port, target, result), "mared-dev-restart");
        t.setDaemon(true);
        t.start();
        return result;
    }

    private static void performRestart(int port, com.fixmer.mared.dev.storage.DevResumeStore.Target target, java.util.concurrent.CompletableFuture<Boolean> result) {
        String ticket = null;
        try {
            if(target!=null&&target.localWorld()&&!"90".equals(System.getenv("GENESIS_INTEGRATED_PROTOCOL90")))throw new IllegalStateException(MaredLang.get("mared.integrated90.owner_old"));
            if(target!=null&&target.localWorld()){DevRestartOverlay.show("mared.integrated90.saving");DevActivationService.lastStatus=MaredLang.get("mared.integrated90.saving");}
            var local90=DevIntegratedRestart90.prepare(target);
            ticket = DevSessionResume.prepare(target);
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/restart?cooperative=true"))
                .header("X-Genesis-Token", System.getenv("GENESIS_SUPERVISOR_TOKEN"))
                .header("X-Genesis-Launch", System.getenv("GENESIS_LAUNCH_ID"))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 202 || !resp.body().contains("\"ok\":true")) {
                DevSessionResume.cancel(ticket);
                fail(MaredLang.format("mared.dev.restart.http_error",
                    resp.statusCode(), resp.body()));
                result.complete(false); return;
            }

            Mared.LOGGER.info("[Mared] supervisor accepted restart: {}",
                resp.body());
            scheduleExit(result,local90,ticket);
        } catch (Exception t) {
            DevSessionResume.cancel(ticket);
            fail(MaredLang.format("mared.dev.restart.connect_failed",
                t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            result.complete(false);
        }
    }

    private static void scheduleExit(java.util.concurrent.CompletableFuture<Boolean> result,DevIntegratedRestart90.Receipt local90,String ticket) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try{DevIntegratedRestart90.validate(local90);}catch(RuntimeException changed){DevSessionResume.cancel(ticket);fail(changed.getMessage());result.complete(false);return;}
            message(MaredLang.get("mared.dev.restart.bye"));
            Mared.LOGGER.info("[Mared] normal shutdown for supervisor restart");
            DevWorldProfile.restore();
            mc.stop();
            result.complete(true);
        });
    }

    private static void fail(String reason) {
        DevActivationService.lastStatus = reason;
        Mared.LOGGER.warn("[Mared] restart failed: {}", reason);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> message(MaredLang.format(
                "mared.dev.restart.failed", reason)));
        }
        restartInProgress = false;
        DevRestartOverlay.clear();
    }

    private static void message(String text) {
        Minecraft instance = Minecraft.getInstance();
        if (instance != null) instance.execute(() -> displayMessage(text));
    }

    private static void displayMessage(String text) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(text), false);
            } else {
                Mared.LOGGER.info("[Mared] {}", text);
            }
        } catch (Throwable ignored) {}
    }

    /** Для логов и /mared dev status. */
    public static String describe() {
        if (!isUnderSupervisor()) return "no supervisor";
        return String.format(Locale.ROOT, "supervisor port=%d", supervisorPort());
    }
}