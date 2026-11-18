package com.fixmer.mared.dev.client;
import com.fixmer.mared.Mared;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
/** Exact launch + attempt binding. A cancelled restart never falls back to a fresh save/exit. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT) public final class DevShutdownSignal {
    private static boolean stopping,worldReported;
    private static long nextCheck;
    private DevShutdownSignal(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!DevRestartService.isUnderSupervisor())return;
        Minecraft mc=Minecraft.getInstance();
        if(!worldReported&&mc.player!=null&&mc.level!=null&&mc.hasSingleplayerServer()){
            worldReported=true;com.fixmer.mared.dev.DevTelemetry.mark("world_ready");
        }
        if(stopping||System.nanoTime()<nextCheck)return;
        nextCheck=System.nanoTime()+100_000_000L;
        String id=System.getenv("GENESIS_LAUNCH_ID"),raw=System.getenv("GENESIS_SHUTDOWN_FILE");
        if(id==null||raw==null)return;
        try{
            Path file=Path.of(raw).toAbsolutePath().normalize();
            Path expected=com.fixmer.mared.dev.DevPaths.file("runtime/"+java.util.UUID.fromString(id)+"/shutdown.request").toAbsolutePath().normalize();
            if(!file.equals(expected)||Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>128)return;
            String signal=Files.readString(file);
            if(signal.startsWith(id+"\nrestart95=")){
                String request=signal.substring((id+"\nrestart95=").length());
                if(!java.util.UUID.fromString(request).toString().equals(request))return;
                var receipt=DevIntegratedRestart90.pending95();
                if(receipt==null||!receipt.gate().id().equals(request))return;
                if(receipt.gate().exiting()){stopping=true;return;}
                if(!receipt.gate().phase().equals("AUTHORIZED"))return;
                try{stopping=DevIntegratedRestart90.exit95(receipt);}
                catch(RuntimeException changed){DevIntegratedRestart90.cancel(receipt);Mared.LOGGER.warn("[Genesis restart95] Changed context; exit declined");}
                return;
            }
            if(!signal.trim().equals(id))return;
            // A separate explicit supervisor Stop uses the same checkpoint gate, not a late naked mc.stop().
            if(!DevIntegratedRestart90.cancelPending95())return;
            stopping=true;
            var target=DevSessionResume.captureCurrent();long generation=DevIntegratedRestart90.generation95();
            Thread worker=new Thread(()->{
                DevIntegratedRestart90.Receipt receipt=null;
                try{receipt=DevIntegratedRestart90.prepare(target,()->generation==DevIntegratedRestart90.generation95());DevRestartService.clientStep(receipt,false);DevRestartService.clientStep(receipt,true);}
                catch(Exception refused){if(receipt!=null)DevIntegratedRestart90.cancel(receipt);mc.execute(()->stopping=false);Mared.LOGGER.warn("[Genesis restart95] Explicit stop declined; checkpoint/context not confirmed");}
            },"genesis-explicit-stop95");worker.setDaemon(true);worker.start();
        }catch(Exception error){Mared.LOGGER.warn("[Mared dev] Shutdown signal ignored: {}",error.toString());}
    }
}
