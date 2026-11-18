package com.fixmer.mared.technology.server.consent.client;

import com.fixmer.genesis.technology.server.waiting.GameConsentBridge.View;
import com.fixmer.mared.Mared;
import com.fixmer.mared.technology.server.consent.GenesisConsentPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Client-only sink. A disconnected/replaced connection cannot retain an old consent dialog. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class GenesisConsentClient {
    private static Object connection;private static View view;private static long received;
    private static GenesisConsentScreen screen;private static UUID dismissed;
    static {GenesisConsentPayloads.clientSink=GenesisConsentClient::receive;}
    private GenesisConsentClient(){}
    private static void receive(GenesisConsentPayloads.Offer offer){
        Minecraft mc=Minecraft.getInstance();if(mc.getConnection()==null)return;
        if(connection!=mc.getConnection())reset(mc);
        connection=mc.getConnection();View next=offer.view();
        if(view!=null&&view.instance().equals(next.instance())&&view.offer().equals(next.offer())&&next.revision()<view.revision())return;
        view=next;received=System.nanoTime();
        if(screen!=null&&screen.same(next))screen.update(next,received);
        else if(!Objects.equals(dismissed,next.offer())&&!next.phase().equals("CANCELLED")&&!next.phase().equals("SUSPENDED")){
            screen=new GenesisConsentScreen(screen!=null&&mc.screen==screen?screen.previous():mc.screen,next,received,()->dismissed=next.offer());mc.setScreen(screen);
        }
    }
    private static void reset(Minecraft mc){if(mc.screen==screen)mc.setScreen(null);connection=null;view=null;screen=null;dismissed=null;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(connection!=null&&connection!=mc.getConnection())reset(mc);
    }
}
