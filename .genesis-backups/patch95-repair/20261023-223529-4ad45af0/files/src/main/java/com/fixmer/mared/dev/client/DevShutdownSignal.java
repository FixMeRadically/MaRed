package com.fixmer.mared.dev.client;
import com.fixmer.mared.Mared;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
/** Windows-safe normal shutdown request. Only the exact current launch token/file is accepted. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT) public final class DevShutdownSignal {
    private static boolean stopping,worldReported;
    private static long nextCheck;
    private DevShutdownSignal() {
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!DevRestartService.isUnderSupervisor())return;
        Minecraft mc=Minecraft.getInstance();
        if(!worldReported&&mc.player!=null&&mc.level!=null&&mc.hasSingleplayerServer()) {
            worldReported=true;
            com.fixmer.mared.dev.DevTelemetry.mark("world_ready");
        }
        if(stopping||System.nanoTime()<nextCheck)return;
        nextCheck=System.nanoTime()+100_000_000L;
        String id=System.getenv("GENESIS_LAUNCH_ID"),raw=System.getenv("GENESIS_SHUTDOWN_FILE");
        if(id==null||raw==null)return;
        try {
            Path file=Path.of(raw).toAbsolutePath().normalize();
            Path expected=com.fixmer.mared.dev.DevPaths.file("runtime/"+java.util.UUID.fromString(id)+"/shutdown.request").toAbsolutePath().normalize();
            if(!file.equals(expected)||Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>128)return;
            if(!Files.readString(file).trim().equals(id))return;
            stopping=true;
            DevIntegratedRestart90.validatePending();
            if(mc.hasSingleplayerServer()&&!DevIntegratedRestart90.hasPreparedWorld()){
                var target=DevSessionResume.captureCurrent();
                Thread save=new Thread(()->{try{var receipt=DevIntegratedRestart90.prepare(target);mc.execute(()->{try{DevIntegratedRestart90.validate(receipt);DevWorldProfile.restore();mc.stop();}catch(RuntimeException changed){DevActivationService.lastStatus=changed.getMessage();Mared.LOGGER.warn("[Genesis integrated90] Shutdown cancelled; world changed");}});}catch(Exception refused){DevActivationService.lastStatus=com.fixmer.mared.MaredLang.get("mared.integrated90.save_failed");Mared.LOGGER.warn("[Genesis integrated90] Shutdown cancelled; save not confirmed");}},"genesis-integrated-save90");save.setDaemon(true);save.start();
            }else{DevWorldProfile.restore();Mared.LOGGER.info("[Mared dev] Supervisor requested normal shutdown");mc.stop();}
        }
        catch(Exception error) {
            Mared.LOGGER.warn("[Mared dev] Shutdown signal ignored: {}",error.toString());
        }
    }
}
