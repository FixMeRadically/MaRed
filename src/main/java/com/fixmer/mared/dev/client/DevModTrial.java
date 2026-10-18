package com.fixmer.mared.dev.client;
import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevPaths;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
/** The trial is acknowledged only after player/world exist; compiling or spawning a JVM is not success. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT) public final class DevModTrial {
    private static final AtomicBoolean SENDING=new AtomicBoolean();
    private static volatile boolean confirmed;
    private static volatile long retryAt;
    private DevModTrial() {
    }
    /** Avoid starting another automatic deployment before this candidate has a durable ready confirmation. */
    public static boolean isPending() {
        return System.getenv("GENESIS_MOD_TRIAL")!=null&&DevRestartService.isUnderSupervisor()&&!confirmed;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String token=System.getenv("GENESIS_MOD_TRIAL");
        Minecraft mc=Minecraft.getInstance();
        if(confirmed||token==null||!DevRestartService.isUnderSupervisor()||mc.player==null||mc.level==null||!mc.hasSingleplayerServer()||System.nanoTime()<retryAt||!SENDING.compareAndSet(false,true))return;
        Thread task=new Thread(() -> {
            try(HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+DevRestartService.supervisorPort()+"/deployment/ready")) .header("X-Genesis-Token",System.getenv("GENESIS_SUPERVISOR_TOKEN")).header("X-Genesis-Trial",token) .timeout(Duration.ofSeconds(3)).POST(HttpRequest.BodyPublishers.noBody()).build(); var response=http.send(request,HttpResponse.BodyHandlers.ofString()); if(response.statusCode()==202)Mared.LOGGER.info("[Mared mod] World ready; confirming candidate mod");
                // Confirmation is serialized by the supervisor; retry until its durable journal disappears.
                confirmed=!java.nio.file.Files.exists(DevPaths.file("deployments/trial.properties"));
            }
            catch(Exception error) {
                Mared.LOGGER.warn("[Mared mod] Trial confirmation will retry: {}",error.toString());
            }
            finally {
                retryAt=System.nanoTime()+Duration.ofSeconds(2).toNanos();SENDING.set(false);
            }
        }
        ,"mared-mod-world-ready");
        task.setDaemon(true);
        task.start();
    }
}
