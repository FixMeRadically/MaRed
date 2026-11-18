package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.ServerDirectory76;
import com.fixmer.mared.Mared;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.*;import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;import net.minecraft.client.gui.components.Button;import net.minecraft.client.multiplayer.*;import net.minecraft.client.multiplayer.resolver.ServerAddress;import net.minecraft.network.chat.Component;import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.client.event.ScreenEvent;
/** Only explicitly configured server addresses are intercepted. Internal updater connections bypass exactly once. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class BootstrapHooks76 {
 private static final ThreadLocal<Boolean> direct=ThreadLocal.withInitial(()->false);private BootstrapHooks76(){}
 public static void direct(Runnable action){boolean old=direct.get();direct.set(true);try{action.run();}finally{direct.set(old);}}
 public static boolean intercept(Screen parent,Minecraft mc,ServerAddress address,ServerData data,boolean quick,TransferState transfer){
  if(direct.get()||data==null||transfer!=null||mc.level!=null||mc.getConnection()!=null)return false;
  try{String endpoint=ServerDirectory76.find(RemoteService69.root(),data.ip);if(endpoint==null)return false;mc.setScreen(new BootstrapScreen76(parent,data.ip,endpoint,()->direct(()->ConnectScreen.startConnecting(parent,mc,address,data,quick,transfer)),true));return true;}catch(Exception invalid){return false;}
 }
 @SubscribeEvent public static void screen(ScreenEvent.Init.Post e){if(e.getScreen() instanceof JoinMultiplayerScreen screen){e.addListener(Button.builder(Component.translatable("mared.bootstrap76.open"),b->Minecraft.getInstance().setScreen(new BootstrapScreen76(screen,RemoteService69.game(),RemoteService69.endpoint(),null,false))).bounds(Math.max(4,screen.width-94),4,90,20).build());}}
}
