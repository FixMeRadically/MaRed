package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.MaredLang;
import net.minecraft.client.Minecraft;

/** Server actions open the Minecraft panel; no external setup application is launched here. */
public final class ServerSetupLauncher59 {
    private ServerSetupLauncher59(){}
    public static boolean busy(){return ServerPanelService66.busy();}
    public static String status(){return MaredLang.get("mared.server66.phase."+ServerPanelService66.phase());}
    public static void open(){Minecraft mc=Minecraft.getInstance();mc.setScreen(new ServerPanelScreen66(mc.screen));}
}
