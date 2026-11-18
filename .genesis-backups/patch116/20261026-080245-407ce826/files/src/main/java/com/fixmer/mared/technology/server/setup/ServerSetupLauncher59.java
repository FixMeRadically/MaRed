package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.MaredLang;
import net.minecraft.client.Minecraft;

/** Server actions open the Minecraft panel; no external setup application is launched here. */
public final class ServerSetupLauncher59 {
    private ServerSetupLauncher59(){}
    public static boolean busy(){return InPlaceServerService104.busy()||SimpleServerService103.busy()||ServerPanelService66.busy();}
    public static String status(){return MaredLang.get(InPlaceServerService104.busy()?"mared.server104.working":"mared.server104.title");}
    public static void open(){Minecraft mc=Minecraft.getInstance();mc.setScreen(new InPlaceServerScreen104(mc.screen));}
}
