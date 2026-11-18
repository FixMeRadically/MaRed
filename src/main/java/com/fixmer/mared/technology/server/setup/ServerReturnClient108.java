package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.RestartReturn108;
import com.fixmer.genesis.technology.server.remote.ReturnLayout110;
import com.fixmer.genesis.technology.server.remote.ReturnPresentation110;
import com.fixmer.genesis.technology.server.remote.ServerDiagnostics105;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.dev.client.GenesisRestartPainter110;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
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
    private static RestartReturn108.Phase logged;private static int loggedAttempt;private static boolean worldFrame,inputReported;private static int readyFrames;private static String lastFailure="";
    private static UUID lastNotice125;
    static{com.fixmer.mared.technology.server.remote.ServerRestartPayload125.clientSink=id->{if(id.equals(lastNotice125))return;lastNotice125=id;var ticket=begin(id);if(ticket!=null)answer(id,true);};}
    private ServerReturnClient108(){}
    private static long now(){return System.nanoTime()/1_000_000L;}
    private static Component tr(String name,Object...args){return Component.translatable("mared.return108."+name,args);}
    public static Ticket begin(UUID id){
        Minecraft mc=Minecraft.getInstance();ServerData data=mc.getCurrentServer();
        if(mc.player==null||mc.level==null||mc.getConnection()==null||data==null||data.ip==null||data.ip.isBlank()||data.ip.length()>512||data.ip.chars().anyMatch(c->c<32)||flow.active())return null;
        ServerLocalTarget109.Endpoint address;try{address=ServerLocalTarget109.parse(data.ip);}catch(Exception invalid){return null;}
        if(!flow.begin(id,now()))return null;
        target=data;origin=mc.getConnection();cover=new Cover();attempt=null;logged=null;loggedAttempt=0;worldFrame=false;inputReported=false;readyFrames=0;lastFailure="";
        Mared.LOGGER.info("[Genesis return110] captured_family={}; port={}",address.host().contains(":")?"IPv6":"IPv4_or_name",address.port());trace();return new Ticket(id,address.host(),address.port());
    }
    /** Called off the game thread. Restarting a different local server must not reconnect this session. */
    public static boolean matchesLocal(Ticket ticket,Path folder)throws Exception{
        var result=localResult(ticket,folder);Mared.LOGGER.info("[Genesis return110] local_target={}",result);return result==ServerLocalTarget109.Result.MATCH;
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
    public static void answer(UUID id,boolean queued){var before=flow.phase();flow.answer(id,queued,now());if(before==RestartReturn108.Phase.PENDING&&flow.phase()!=before)Mared.LOGGER.info("[Genesis return110] request={}",queued?"QUEUED":"DECLINED_OR_TARGET_MISMATCH");trace();if(!flow.active())releaseReferences();}
    private static void trace(){if(logged!=flow.phase()||loggedAttempt!=flow.attempts()){logged=flow.phase();loggedAttempt=flow.attempts();Mared.LOGGER.info("[Genesis return110] phase={}; attempt={}",logged,loggedAttempt);}}
    private static void releaseReferences(){origin=null;target=null;cover=null;attempt=null;worldFrame=false;readyFrames=0;lastFailure="";}
    private static boolean ownWorld(Minecraft mc){return target!=null&&mc.level!=null&&mc.player!=null&&mc.getConnection()!=null&&mc.getConnection()!=origin&&mc.getCurrentServer()!=null&&mc.getCurrentServer().ip.equals(target.ip);}
    private static void abortAttempt(){
        ConnectScreen own=attempt;attempt=null;if(own==null)return;
        var accessor=(FirstLaunchConnectAccessor58)(Object)own;accessor.genesisFirst58SetAborted(true);
        var future=accessor.genesisFirst58Future();if(future!=null)future.cancel(true);
        var connection=accessor.genesisFirst58Connection();if(connection!=null)connection.disconnect(tr("cancelled"));
    }
    private static void cancel(){
        Minecraft mc=Minecraft.getInstance();boolean visible=mc.screen==cover||masks(mc.screen);
        flow.cancel();try{abortAttempt();}catch(Exception failed){Mared.LOGGER.warn("[Genesis return110] Could not close the owned connection attempt",failed);}
        trace();if(visible)mc.setScreen(mc.level==null?new TitleScreen():null);releaseReferences();
    }
    private static boolean transition(){return Set.of(RestartReturn108.Phase.WAITING,RestartReturn108.Phase.CONNECTING,RestartReturn108.Phase.LOADING,RestartReturn108.Phase.PROMPT).contains(flow.phase());}
    private static boolean masks(Screen screen){
        Minecraft mc=Minecraft.getInstance();if(!flow.active()||cover==null||screen==null||screen==cover)return false;
        var surface=screen==attempt?ReturnPresentation110.Surface.OWN_CONNECT:screen instanceof ReceivingLevelScreen?ReturnPresentation110.Surface.RECEIVING:screen instanceof DisconnectedScreen?ReturnPresentation110.Surface.DISCONNECTED:ReturnPresentation110.Surface.OTHER;
        return ReturnPresentation110.masks(flow.phase(),surface,attempt!=null,screen==mc.screen,mc.getOverlay()!=null,mc.getConnection()!=origin);
    }
    private static void prepareCover(){Minecraft mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();if(cover!=null&&(cover.width!=w||cover.height!=h))cover.init(mc,w,h);}
    /** The underlying screen continues ticking and owns its normal connection lifecycle. */
    public static boolean renderReplacement(Screen screen,GuiGraphics g,int x,int y,float dt){
        if(!masks(screen))return false;prepareCover();cover.render(g,x,y,dt);return true;
    }
    public static boolean mouseReplacement(long window,int button,int action){
        Minecraft mc=Minecraft.getInstance();if(window!=mc.getWindow().getWindow()||!masks(mc.screen))return false;
        prepareCover();double x=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/Math.max(1,mc.getWindow().getScreenWidth());
        double y=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/Math.max(1,mc.getWindow().getScreenHeight());
        if(action==1)cover.mouseClicked(x,y,button);else if(action==0)cover.mouseReleased(x,y,button);return action!=0;
    }
    public static boolean keyReplacement(long window,int key,int scan,int action,int modifiers){
        Minecraft mc=Minecraft.getInstance();if(window!=mc.getWindow().getWindow()||!masks(mc.screen)||action==0)return false;
        prepareCover();if(action==1||action==2)cover.keyPressed(key,scan,modifiers);return true;
    }
    /** Window swap hook: a connection/player alone does not establish a presented world frame. */
    public static void afterFrame(){
        if(!flow.active()||!Set.of(RestartReturn108.Phase.CONNECTING,RestartReturn108.Phase.LOADING).contains(flow.phase()))return;
        Minecraft mc=Minecraft.getInstance();if(!ownWorld(mc)||mc.screen!=null||mc.getOverlay()!=null){readyFrames=0;worldFrame=false;return;}
        if(++readyFrames<2)return;boolean first=!worldFrame;worldFrame=true;flow.loading(now());if(first)Mared.LOGGER.info("[Genesis return110] world_frame_presented");
        // Do not focus the OS window or grab input while the player is in another application.
        if(!inputReported&&mc.isWindowActive())try{
            if(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_FOCUSED)==org.lwjgl.glfw.GLFW.GLFW_TRUE){
                if(!mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.grabMouse();inputReported=true;
                Mared.LOGGER.info("[Genesis return110] mouse_grabbed={}",mc.mouseHandler.isMouseGrabbed());
            }
        }catch(RuntimeException failed){inputReported=true;Mared.LOGGER.warn("[Genesis return110] Optional input restoration failed",failed);}

    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!flow.active())return;Minecraft mc=Minecraft.getInstance();
        try{
            boolean joined=ownWorld(mc);
            if(mc.level!=null&&mc.player!=null&&mc.getConnection()!=origin&&!joined){cancel();return;}
            if(transition()&&mc.getOverlay()!=null){flow.pause(now());return;}
            flow.resume(now());
            if(attempt!=null){
                if(((FirstLaunchConnectAccessor58)(Object)attempt).genesisFirst58Aborted()){cancel();return;}
                if(mc.screen instanceof TitleScreen||mc.screen instanceof JoinMultiplayerScreen){cancel();return;}
                if(mc.screen!=null&&mc.screen!=attempt&&mc.screen!=cover&&!(mc.screen instanceof DisconnectedScreen)&&!(mc.screen instanceof ReceivingLevelScreen)){
                    // Resource-pack / authentication / mod confirmation screens retain their own input and rendering.
                    flow.pause(now());return;
                }
                if(mc.screen instanceof DisconnectedScreen disconnected){
                    String reason=ServerDiagnostics105.redact(disconnected.getNarrationMessage().getString()).replaceAll("[\\p{Cntrl}]"," ");
                    if(reason.length()>1024)reason=reason.substring(0,1024);Mared.LOGGER.info("[Genesis return110] attempt_failed: {}",reason);lastFailure=reason.length()>160?reason.substring(0,160)+"…":reason;
                    abortAttempt();worldFrame=false;readyFrames=0;inputReported=false;flow.failed(now());}
                else if(joined||mc.screen instanceof ReceivingLevelScreen){lastFailure="";flow.loading(now());}
                else if(flow.attemptTimedOut(now())){abortAttempt();worldFrame=false;readyFrames=0;inputReported=false;flow.failed(now());if(mc.level==null)mc.setScreen(cover);}
            }
            var before=flow.phase();flow.tick(now(),mc.getConnection()==origin,joined&&worldFrame&&mc.screen==null&&mc.getOverlay()==null,mc.getConnection()!=null||attempt!=null||mc.screen instanceof ConnectScreen);
            trace();if(flow.phase()==RestartReturn108.Phase.COMPLETE){releaseReferences();return;}
            if(!flow.active()){if(mc.screen==cover)mc.setScreen(mc.level==null?new TitleScreen():null);releaseReferences();return;}
            if(flow.phase()==RestartReturn108.Phase.WAITING||flow.phase()==RestartReturn108.Phase.PROMPT){
                if(mc.level==null&&mc.getConnection()==null){
                    if(mc.screen!=cover&&!(mc.screen instanceof DisconnectedScreen)&&before!=RestartReturn108.Phase.ARMED&&before!=RestartReturn108.Phase.CONNECTING&&before!=RestartReturn108.Phase.LOADING){cancel();return;}
                    if(mc.screen!=cover)mc.setScreen(cover);
                }
            }
            if(flow.phase()==RestartReturn108.Phase.CONNECTING&&attempt==null){
                try{
                    BootstrapHooks76.direct(()->ConnectScreen.startConnecting(cover,mc,ServerAddress.parseString(target.ip),target,false,null));
                    if(mc.screen instanceof ConnectScreen screen)attempt=screen;
                    else {flow.failed(now());if(mc.level==null&&mc.getConnection()==null)mc.setScreen(cover);}
                }catch(Exception failed){abortAttempt();flow.failed(now());if(mc.level==null)mc.setScreen(cover);Mared.LOGGER.warn("[Genesis return110] Connection attempt failed",failed);}
            }
        }catch(Exception failed){cancel();Mared.LOGGER.warn("[Genesis return110] Return cancelled after client error",failed);}
    }
    private static final class CoreButton extends Button {
        CoreButton(int x,int y,int w,int h,Component text,OnPress press){super(x,y,w,h,text,press,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int x,int y,float dt){
            boolean selected=active&&(isHovered()||isFocused());int left=getX(),top=getY(),w=getWidth(),h=getHeight();
            int border=selected?0xFFD5D9EA:0xFF545A70;g.fill(left,top,left+w,top+h,selected?0xFF282C3B:0xFF151822);
            g.fill(left,top,left+w,top+1,border);g.fill(left,top+h-1,left+w,top+h,border);g.fill(left,top,left+1,top+h,border);g.fill(left+w-1,top,left+w,top+h,border);
            g.drawCenteredString(Minecraft.getInstance().font,getMessage(),left+w/2,top+(h-Minecraft.getInstance().font.lineHeight)/2,active?0xFFF0F1FC:0xFF777C8E);
        }
    }
    private static final class Cover extends MaredScreen {
        private final GenesisRestartPainter110 painter=new GenesisRestartPainter110();private final long started=now();
        private Button again,stop;private boolean painterFailed;
        Cover(){super(tr("title"));}
        private boolean question(){return flow.phase()==RestartReturn108.Phase.PROMPT||flow.loadingSlow(now());}
        private Component status(){Component message=tr(flow.phase()==RestartReturn108.Phase.LOADING?(question()?"loading_question":"loading"):(question()?"question":flow.phase()==RestartReturn108.Phase.CONNECTING?"connecting":"waiting"));if(question()&&!lastFailure.isBlank())return Component.empty().append(message).append(Component.literal("\n")).append(tr("last_error",lastFailure));return message;}
        @Override protected void init(){
            var layout=ReturnLayout110.of(width,height,1,font.lineHeight);
            again=addRenderableWidget(new CoreButton(layout.padding(),layout.buttonY(),layout.buttonWidth(),layout.buttonHeight(),tr("continue_short"),b->{flow.continueWaiting(now());trace();}));
            stop=addRenderableWidget(new CoreButton(layout.padding(),layout.buttonY(),layout.buttonWidth(),layout.buttonHeight(),tr("cancel_short"),b->cancel()));
            placeButtons(layout);setInitialFocus(stop);
        }
        private void placeButtons(ReturnLayout110 layout){
            boolean ask=question();again.visible=ask;again.active=ask;
            int gap=layout.padding(),each=Math.max(1,Math.min(150,(width-3*gap)/2)),left=(width-2*each-gap)/2;
            again.setX(left);again.setWidth(each);stop.setX(ask?left+each+gap:(width-layout.buttonWidth())/2);stop.setWidth(ask?each:layout.buttonWidth());
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){cancel();}
        @Override public void render(GuiGraphics g,int x,int y,float dt){
            var rows=font.split(status(),Math.max(1,width-24));var layout=ReturnLayout110.of(width,height,Math.max(2,rows.size()),font.lineHeight);
            if(!painterFailed)try{painter.render(g,width,height,(now()-started)/1000f+2f,layout.coreY(),layout.radius());}
            catch(RuntimeException failed){painterFailed=true;Mared.LOGGER.warn("[Genesis return110] Core rendering disabled for this transition",failed);}
            if(painterFailed)g.fillGradient(0,0,width,height,0xFF010104,0xFF050510);
            g.drawCenteredString(font,"GENESIS",width/2,layout.titleY(),0xFFF0F1FC);
            for(int i=0;i<Math.min(rows.size(),layout.rows());i++){
                int rowY=layout.statusY()+(layout.rows()-Math.min(rows.size(),layout.rows()))*(font.lineHeight+2)/2+i*(font.lineHeight+2);
                if(i==layout.rows()-1&&rows.size()>layout.rows())g.drawCenteredString(font,"…",width/2,rowY,0xFFBEC2D5);
                else {var row=rows.get(i);g.drawString(font,row,(width-font.width(row))/2,rowY,0xFFBEC2D5,false);}
            }
            g.drawCenteredString(font,tr("attempt_short",flow.attempts()),width/2,layout.attemptY(),0xFF9EA6BA);placeButtons(layout);super.render(g,x,y,dt);
        }
    }
}
