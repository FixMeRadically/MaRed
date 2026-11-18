package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.storage.DevResumeStore;

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

    public static java.util.concurrent.CompletableFuture<Boolean> restartAsync() { return restartAsync(()->true); }

    public static synchronized java.util.concurrent.CompletableFuture<Boolean> restartAsync(java.util.function.BooleanSupplier current) {
        if(!DevMode.isEnabled()){message(MaredLang.get("mared.dev.restart.requires_dev"));return java.util.concurrent.CompletableFuture.completedFuture(false);}
        if(!isUnderSupervisor()){message(MaredLang.get("mared.dev.restart.no_supervisor"));return java.util.concurrent.CompletableFuture.completedFuture(false);}
        if(restartInProgress){message(MaredLang.get("mared.dev.restart.already"));return java.util.concurrent.CompletableFuture.completedFuture(false);}
        if(supervisorPort()<1){message("Invalid supervisor port; restart declined.");return java.util.concurrent.CompletableFuture.completedFuture(false);}
        if (!"95".equals(System.getenv("GENESIS_RESTART_PROTOCOL95"))) {
            message(MaredLang.get("mared.restart95.owner_old"));
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        final DevResumeStore.Target target;
        try { target=DevSessionResume.captureCurrent(); }
        catch(RuntimeException e){message(e.getMessage());return java.util.concurrent.CompletableFuture.completedFuture(false);}
        long generation=DevIntegratedRestart90.generation95();
        java.util.function.BooleanSupplier guard=()->generation==DevIntegratedRestart90.generation95()&&current.getAsBoolean();
        var result=new java.util.concurrent.CompletableFuture<Boolean>();
        restartInProgress=true;
        DevActivationService.lastStatus=MaredLang.get("mared.integrated90.saving");
        DevRestartOverlay.show("mared.integrated90.saving");
        Thread worker=new Thread(()->performRestart(supervisorPort(),target,guard,result),"mared-dev-restart95");
        worker.setDaemon(true);worker.start();return result;
    }

    private static void performRestart(int port,DevResumeStore.Target target,java.util.function.BooleanSupplier current,java.util.concurrent.CompletableFuture<Boolean> result) {
        String ticket=null;
        DevIntegratedRestart90.Receipt receipt=null;
        com.fixmer.genesis.supervisor.RestartConnection95 connection=null;
        try {
            LocalRecovery91.activationCheck(target);
            receipt=DevIntegratedRestart90.prepare(target,current);
            ticket=DevSessionResume.prepare(target);
            connection=new com.fixmer.genesis.supervisor.RestartConnection95(port,System.getenv("GENESIS_SUPERVISOR_TOKEN"),
                System.getenv("GENESIS_SUPERVISOR_INSTANCE95"),System.getenv("GENESIS_LAUNCH_ID"),receipt.gate().id(),
                (receipt.epoch().isEmpty()?"-":receipt.epoch())+"."+ticket);
            var prepared=connection.prepare();
            if(!"PREPARED".equals(prepared.getProperty("state")))throw new IllegalStateException("Restart preparation refused");
            String grant=prepared.getProperty("grant");
            clientStep(receipt,false);
            if(!receipt.gate().active(System.nanoTime()))throw new java.util.concurrent.CancellationException("Restart cancelled");
            var committed=connection.commit(grant);
            if(!"COMMITTED".equals(committed.getProperty("state")))throw new IllegalStateException("Restart commit refused");
            clientStep(receipt,true);
            result.complete(true);
        } catch(Exception error) {
            // The shutdown file may have won while the authenticated HTTP response was lost.
            if(receipt!=null&&!DevIntegratedRestart90.cancel(receipt)){result.complete(true);return;}
            DevSessionResume.cancel(ticket);
            if(connection!=null)try{connection.cancel();}catch(Exception ignored){}
            String cancelled=MaredLang.get("mared.restart95.cancelled");
            fail(cancelled.equals(DevActivationService.lastStatus)?cancelled:MaredLang.format("mared.dev.restart.connect_failed",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));
            result.complete(false);
        }
    }

    static void clientStep(DevIntegratedRestart90.Receipt receipt,boolean exit)throws Exception {
        var done=new java.util.concurrent.CompletableFuture<Void>();
        Minecraft.getInstance().execute(()->{
            try {
                if(receipt.gate().exiting()){done.complete(null);return;}
                if(exit){if(!DevIntegratedRestart90.exit95(receipt))throw new java.util.concurrent.CancellationException();}else DevIntegratedRestart90.authorize95(receipt);
                done.complete(null);
            }catch(Throwable e){done.completeExceptionally(e);}
        });
        try{done.get(5,java.util.concurrent.TimeUnit.SECONDS);}
        catch(java.util.concurrent.TimeoutException timeout){
            if(DevIntegratedRestart90.cancel(receipt))throw timeout;
            // Exit was claimed atomically; never revoke its replacement after that point.
        }
    }

    public static boolean cancelBeforeExit95(){
        if(!DevIntegratedRestart90.cancelPending95())return false;
        DevActivationService.lastStatus=MaredLang.get("mared.restart95.cancelled");
        DevRestartOverlay.clear();return true;
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