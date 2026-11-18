package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.Bootstrap76;
import com.fixmer.mared.Mared;import com.fixmer.mared.technology.server.firstlaunch.FirstLaunchServer58;
import net.minecraft.client.Minecraft;import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;import java.util.*;
/** One startup snapshot: a later disk edit is not evidence that its classes were loaded. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class BootstrapRuntime76 {
 private static boolean started;private static volatile String snapshot;private static volatile boolean complete;private BootstrapRuntime76(){}
 public static String snapshot(){return snapshot;}public static boolean complete(){return complete;}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(started)return;started=true;Path profile=Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();List<Path> loaded=FirstLaunchServer58.loaded();Thread task=new Thread(()->{try{snapshot=Bootstrap76.runtimeInventory(profile,loaded);}catch(Exception unavailable){snapshot=null;}finally{complete=true;}},"genesis-bootstrap76-runtime");task.setDaemon(true);task.start();}
}
