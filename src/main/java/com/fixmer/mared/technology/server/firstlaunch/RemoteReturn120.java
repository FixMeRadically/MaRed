package com.fixmer.mared.technology.server.firstlaunch;
import net.minecraft.client.Minecraft;import net.neoforged.fml.loading.FMLPaths;
import com.fixmer.genesis.supervisor.FailureReport101;import com.fixmer.mared.dev.client.FailureScreen101;
/** Reuse the existing error viewer after confirmed rollback rejoin; no report is silently acknowledged. */
public final class RemoteReturn120 {
 private RemoteReturn120(){}public static final int VERSION=120;private static boolean requested;
 public static synchronized void connected(){if(requested)return;requested=true;var profile=FMLPaths.GAMEDIR.get();var thread=new Thread(()->{try{var report=FailureReport101.read(profile);if(report!=null&&!report.acknowledged())Minecraft.getInstance().execute(()->{var mc=Minecraft.getInstance();if(mc.level!=null&&mc.screen==null)mc.setScreen(new FailureScreen101(null,report));});}catch(Exception ignored){}},"genesis-return120-report");thread.setDaemon(true);thread.start();}
}
