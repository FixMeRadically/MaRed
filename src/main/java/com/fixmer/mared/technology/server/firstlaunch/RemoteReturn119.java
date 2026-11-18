package com.fixmer.mared.technology.server.firstlaunch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import com.fixmer.mared.dev.client.FailureScreen101;
/** A return reaches a rendered menu/error report, never a JOIN or a world acknowledgement. */
public final class RemoteReturn119 {
 private RemoteReturn119(){}
 public static boolean menuFrame(){var mc=Minecraft.getInstance();return mc.level==null&&mc.player==null&&mc.getConnection()==null&&mc.getOverlay()==null&&(mc.screen instanceof TitleScreen||mc.screen instanceof FailureScreen101);}
}
