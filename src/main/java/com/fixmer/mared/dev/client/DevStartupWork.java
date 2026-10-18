package com.fixmer.mared.dev.client;

import com.fixmer.genesis.supervisor.LaunchCacheClient;
import com.fixmer.genesis.supervisor.StartupWorkQueue;
import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevTelemetry;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Maintenance starts only after a stable usable screen/world, outside restart's load path. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class DevStartupWork {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"genesis-startup-maintenance");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    private static final StartupWorkQueue QUEUE=new StartupWorkQueue(1000,8,WORKER,(id,error)->Mared.LOGGER.warn("[Mared startup] Maintenance '{}' skipped ({})",id,error.getClass().getSimpleName()));
    private static boolean requested,resourcesMarked;
    private DevStartupWork() { }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){tickAt(System.nanoTime());}
    static void tickAt(long now){
        Minecraft mc=Minecraft.getInstance();
        boolean world=mc.player!=null&&mc.level!=null;
        boolean menu=mc.screen instanceof TitleScreen&&!DevQuickStart.isPending()&&!DevSessionResume.isPendingReturn();
        boolean ready=mc.getOverlay()==null&&(world||menu);
        if(ready&&!resourcesMarked){resourcesMarked=true;DevTelemetry.mark("resources_ready");}
        if(!requested){requested=true;submit("launch_cache",()->{LaunchCacheClient.runPending();DevTelemetry.report("cache_capture_"+LaunchCacheClient.status(),0);});}
        QUEUE.tick(ready&&!DevWorldProfile.isActive(),now);
    }
    public static boolean stable(){return QUEUE.stable();}
    public static boolean submit(String id,Runnable action){return QUEUE.submit(id,()->{long begin=System.nanoTime();try{action.run();}finally{DevTelemetry.report("startup_"+id,(System.nanoTime()-begin)/1_000_000);}});}
    public static void close(){QUEUE.close();WORKER.shutdownNow();}
}
