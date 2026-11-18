package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.RestartReturn108;
import com.fixmer.genesis.technology.server.remote.ServerLocalTarget109;
import com.fixmer.mared.Mared;
import com.fixmer.mared.mixin.FirstLaunchConnectAccessor58;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
import java.net.*;
import java.nio.file.*;

/** Client-thread state survives leaving the admin screen. No automatic restart on an ordinary disconnect. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class ServerReturnClient108 {
    public record Ticket(UUID id,String host,int port) {}
    private static RestartReturn108 flow=new RestartReturn108();
    private static Object origin;private static ServerData target;private static Cover cover;private static ConnectScreen attempt;
    private static RestartReturn108.Phase logged;private static int loggedAttempt;
    private ServerReturnClient108(){}
    private static long now(){return System.nanoTime()/1_000_000L;}
    private static Component tr(String name,Object...args){return Component.translatable("mared.return108."+name,args);}
    public static Ticket begin(UUID id){
        Minecraft mc=Minecraft.getInstance();ServerData data=mc.getCurrentServer();
        if(mc.player==null||mc.level==null||mc.getConnection()==null||data==null||data.ip==null||data.ip.isBlank()||data.ip.length()>512||data.ip.chars().anyMatch(c->c<32)||flow.active())return null;
        ServerLocalTarget109.Endpoint address;try{address=ServerLocalTarget109.parse(data.ip);}catch(Exception invalid){return null;}
        if(!flow.begin(id,now()))return null;
        target=data;origin=mc.getConnection();cover=new Cover();attempt=null;logged=null;loggedAttempt=0;
        Mared.LOGGER.info("[Genesis return109] captured_family={}; port={}",address.host().contains(":")?"IPv6":"IPv4_or_name",address.port());trace();return new Ticket(id,address.host(),address.port());
    }
    /** Called off the game thread. Restarting a different local server must not reconnect this session. */
    public static boolean matchesLocal(Ticket ticket,Path folder)throws Exception{
        var result=localResult(ticket,folder);Mared.LOGGER.info("[Genesis return109] local_target={}",result);return result==ServerLocalTarget109.Result.MATCH;
    }
    private static ServerLocalTarget109.Result localResult(Ticket ticket,Path folder)throws Exception{
        if(ticket==null)return ServerLocalTarget109.Result.HOST_UNRESOLVED;
        Path file=folder.resolve("server.properties");if(!Files.isRegularFile(file)||Files.size(file)>1024*1024)return ServerLocalTarget109.Result.NO_PROPERTIES;
        Properties p=new Properties();try(var reader=Files.newBufferedReader(file)){p.load(reader);}
        int port=Integer.parseInt(p.getProperty("server-port","25565"));
        if(ticket.port()!=port)return ServerLocalTarget109.Result.PORT_MISMATCH;
        String bind=p.getProperty("server-ip","").trim();
        var local=new ArrayList<InetAddress>();var interfaces=NetworkInterface.getNetworkInterfaces();int count=0;
        while(interfaces!=null&&interfaces.hasMoreElements()){
            if(++count>256)throw new IllegalStateException("RETURN109_INTERFACE_LIMIT");
            var addresses=interfaces.nextElement().getInetAddresses();
            while(addresses.hasMoreElements()){if(local.size()>=1024)throw new IllegalStateException("RETURN109_ADDRESS_LIMIT");local.add(addresses.nextElement());}
        }
        List<InetAddress> targets;try{targets=Arrays.asList(InetAddress.getAllByName(ticket.host()));}catch(UnknownHostException unknown){return ServerLocalTarget109.Result.HOST_UNRESOLVED;}
        return ServerLocalTarget109.evaluate(ticket.port(),port,targets,local,bind.isBlank()?List.of():Arrays.asList(InetAddress.getAllByName(bind)),bind.isBlank());
    }
    public static void answer(UUID id,boolean queued){var before=flow.phase();flow.answer(id,queued,now());if(before==RestartReturn108.Phase.PENDING&&flow.phase()!=before)Mared.LOGGER.info("[Genesis return109] request={}",queued?"QUEUED":"DECLINED_OR_TARGET_MISMATCH");trace();}
    private static void trace(){if(logged!=flow.phase()||loggedAttempt!=flow.attempts()){logged=flow.phase();loggedAttempt=flow.attempts();Mared.LOGGER.info("[Genesis return108] phase={}; attempt={}",logged,loggedAttempt);}}
    private static void abortAttempt(){
        ConnectScreen own=attempt;attempt=null;if(own==null)return;
        var accessor=(FirstLaunchConnectAccessor58)(Object)own;accessor.genesisFirst58SetAborted(true);
        var future=accessor.genesisFirst58Future();if(future!=null)future.cancel(true);
        var connection=accessor.genesisFirst58Connection();if(connection!=null)connection.disconnect(tr("cancelled"));
    }
    private static void cancel(){flow.cancel();try{abortAttempt();}catch(Exception failed){Mared.LOGGER.warn("[Genesis return108] Could not close the owned connection attempt",failed);}trace();Minecraft mc=Minecraft.getInstance();if(mc.screen==cover)mc.setScreen(new TitleScreen());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!flow.active())return;Minecraft mc=Minecraft.getInstance();
        try{
            boolean joined=mc.level!=null&&mc.player!=null&&mc.getConnection()!=origin;
            if(joined&&(mc.getCurrentServer()==null||!mc.getCurrentServer().ip.equals(target.ip))){cancel();return;}
            if(attempt!=null){
                if(((FirstLaunchConnectAccessor58)(Object)attempt).genesisFirst58Aborted()){cancel();return;}
                if(mc.screen instanceof DisconnectedScreen){attempt=null;flow.failed(now());}
                else if(flow.attemptTimedOut(now())){abortAttempt();flow.failed(now());if(mc.level==null)mc.setScreen(cover);}
                else if(mc.screen!=attempt&&mc.screen!=cover&&mc.level==null){cancel();return;}
            }
            var before=flow.phase();flow.tick(now(),mc.getConnection()==origin,joined,mc.getConnection()!=null||attempt!=null||mc.screen instanceof ConnectScreen);
            trace();if(flow.phase()==RestartReturn108.Phase.COMPLETE){attempt=null;if(mc.screen==cover)mc.setScreen(null);return;}
            if(!flow.active()){if(mc.screen==cover)mc.setScreen(new TitleScreen());return;}
            if(flow.phase()==RestartReturn108.Phase.WAITING||flow.phase()==RestartReturn108.Phase.PROMPT){
                if(mc.level==null&&mc.getConnection()==null){
                    if(mc.screen!=cover&&!(mc.screen instanceof DisconnectedScreen)&&before!=RestartReturn108.Phase.ARMED&&before!=RestartReturn108.Phase.CONNECTING){cancel();return;}
                    if(mc.screen!=cover)mc.setScreen(cover);
                    cover.question(flow.phase()==RestartReturn108.Phase.PROMPT);
                }
            }
            if(flow.phase()==RestartReturn108.Phase.CONNECTING&&attempt==null){
                try{
                    BootstrapHooks76.direct(()->ConnectScreen.startConnecting(cover,mc,ServerAddress.parseString(target.ip),target,false,null));
                    if(mc.screen instanceof ConnectScreen screen)attempt=screen;
                    else {flow.failed(now());if(mc.level==null&&mc.getConnection()==null)mc.setScreen(cover);}
                }catch(Exception failed){abortAttempt();flow.failed(now());mc.setScreen(cover);Mared.LOGGER.warn("[Genesis return108] Connection attempt failed",failed);}
            }
        }catch(Exception failed){cancel();Mared.LOGGER.warn("[Genesis return108] Return cancelled after client error",failed);}
    }
    private static final class Cover extends Screen {
        private Button again;
        Cover(){super(tr("title"));}
        @Override protected void init(){
            again=addRenderableWidget(Button.builder(tr("continue"),b->{flow.continueWaiting(now());question(false);trace();}).bounds(width/2-105,height/2+45,210,20).build());
            addRenderableWidget(Button.builder(tr("cancel"),b->cancel()).bounds(width/2-105,height/2+70,210,20).build());question(flow.phase()==RestartReturn108.Phase.PROMPT);
        }
        void question(boolean value){if(again!=null){again.visible=value;again.active=value;}}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){cancel();}
        @Override public void render(GuiGraphics g,int x,int y,float dt){
            g.fill(0,0,width,height,0xFF10131B);int cx=width/2,cy=height/2-48;long frame=now()/110;
            for(int i=0;i<8;i++){int col=i%4,row=i/4;g.fill(cx-23+col*12,cy+row*12,cx-15+col*12,cy+8+row*12,(frame%8==i)?0xFFCA9BFF:0xFF50356E);}
            g.drawCenteredString(font,title,cx,height/2-8,0xFFFFFFFF);
            g.drawCenteredString(font,tr(flow.phase()==RestartReturn108.Phase.PROMPT?"question":"waiting"),cx,height/2+10,0xFFCDD0DC);
            g.drawCenteredString(font,tr("attempt",flow.attempts()),cx,height/2+25,0xFF999EAD);super.render(g,x,y,dt);
        }
    }
}
