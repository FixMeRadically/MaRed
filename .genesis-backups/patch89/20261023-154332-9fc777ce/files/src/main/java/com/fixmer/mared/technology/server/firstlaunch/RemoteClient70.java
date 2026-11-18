package com.fixmer.mared.technology.server.firstlaunch;

import com.fixmer.genesis.technology.server.remote.RemoteSession70;
import com.fixmer.mared.Mared;
import com.fixmer.mared.mixin.FirstLaunchConnectAccessor58;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Client-only executor. At most one ConnectScreen; cancellation follows vanilla's cancel-button actions. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class RemoteClient70 {
    private static final ScheduledExecutorService IO=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Genesis-remote70-client");t.setDaemon(true);return t;});
    private static volatile boolean runtime73;public static boolean ready73(){return runtime73&&!failed;}
    private static volatile RemoteSession70 session;private static volatile boolean failed,starting,joining,worldReported,framePending;private static volatile long sequence;private static long focusDeadline;private static volatile UUID request=new UUID(0,0);
    private RemoteClient70(){}
    private static void fail(){failed=true;Mared.LOGGER.warn("[Genesis remote70] Client suspended; no automatic install or new connection");try{if(session!=null)session.status("ERROR",sequence,request);}catch(Exception ignored){}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(System.getenv("GENESIS_REMOTE70_ROOT")==null)return;Minecraft mc=Minecraft.getInstance();
        if(!starting){starting=true;List<Path> paths=FirstLaunchServer58.loaded();String game=FirstLaunchServer58.version("minecraft"),neo=FirstLaunchServer58.version("neoforge");IO.execute(()->{try{session=RemoteSession70.open(System.getenv());session.verifyRuntime(paths,game,neo);runtime73=true;session.status("READY",0,new UUID(0,0));IO.scheduleWithFixedDelay(RemoteClient70::poll,250,250,TimeUnit.MILLISECONDS);}catch(Exception bad){fail();}});}
        if(session==null||failed)return;
        if(joining&&mc.player!=null&&mc.level!=null&&mc.getCurrentServer()!=null&&mc.getCurrentServer().ip.equals(session.address())&&mc.player.getUUID().equals(session.account())&&!worldReported){joining=false;framePending=true;}
        if(joining&&mc.screen instanceof DisconnectedScreen){joining=false;IO.execute(()->{try{session.status("JOIN_FAILED",sequence,request);}catch(Exception bad){fail();}});}
    }
    private static void poll(){
        if(session==null||failed)return;
        try{var c=session.command();if(c.isEmpty())return;long seq=Long.parseLong(c.get("sequence"));if(seq<=sequence)return;sequence=seq;request=UUID.fromString(c.get("request"));String action=c.get("phase");UUID nonce=request;
            if(!Set.of("STOP","JOIN","CANCEL","FOCUS").contains(action))throw new IllegalStateException("Unknown remote70 command");Minecraft.getInstance().execute(()->apply(action,seq,nonce));
        }catch(Exception bad){fail();}
    }
    private static void apply(String action,long seq,UUID nonce){
        if(failed||seq!=sequence||!nonce.equals(request))return;Minecraft mc=Minecraft.getInstance();
        if(action.equals("FOCUS")){
            focusDeadline=System.nanoTime()+3_000_000_000L;org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());com.fixmer.mared.dev.client.DevNativeFocus.request(mc.getWindow().getWindow());return;
        }
        if(action.equals("STOP")){IO.execute(()->{try{session.status("STOP_REQUESTED",seq,nonce);mc.execute(()->{if(seq==sequence&&nonce.equals(request))mc.stop();});}catch(Exception bad){fail();}});return;}
        if(action.equals("CANCEL")){
            if(mc.screen instanceof ConnectScreen connect){var accessor=(FirstLaunchConnectAccessor58)(Object)connect;accessor.genesisFirst58SetAborted(true);var future=accessor.genesisFirst58Future();if(future!=null)future.cancel(true);var connection=accessor.genesisFirst58Connection();if(connection!=null)connection.disconnect(Component.translatable("mared.first58.cancelled"));mc.setScreen(new TitleScreen());}
            joining=false;framePending=false;IO.execute(()->{try{session.status("CANCELLED",seq,nonce);}catch(Exception bad){fail();}});return;
        }
        if(mc.player!=null||mc.getConnection()!=null||mc.screen instanceof ConnectScreen){IO.execute(()->{try{session.status("JOIN_BUSY",seq,nonce);}catch(Exception bad){fail();}});return;}
        joining=true;worldReported=false;framePending=false;
        try{com.fixmer.mared.technology.server.setup.BootstrapHooks76.direct(()->ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(session.address()),new ServerData("Genesis remote update",session.address(),ServerData.Type.OTHER),false,null));IO.execute(()->{try{session.status("JOINING",seq,nonce);}catch(Exception bad){fail();}});}catch(Exception bad){joining=false;fail();}
    }
    /** Called after Window.updateDisplay's frame swap; no I/O on the render thread. */
    public static void afterFrame(){
        if(focusDeadline>0){Minecraft game=Minecraft.getInstance();if(System.nanoTime()>=focusDeadline)focusDeadline=0;
            else if(game.screen==null&&game.getOverlay()==null&&game.player!=null&&game.level!=null&&Boolean.TRUE.equals(com.fixmer.mared.dev.client.DevNativeFocus.ownsForeground(game.getWindow().getWindow()))&&org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(game.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_FOCUSED)==org.lwjgl.glfw.GLFW.GLFW_TRUE){
                if(!game.isWindowActive())game.setWindowActive(true);if(!game.mouseHandler.isMouseGrabbed())game.mouseHandler.grabMouse();
                if(game.mouseHandler.isMouseGrabbed()){focusDeadline=0;long seq=sequence;UUID nonce=request;IO.execute(()->{try{session.status("INPUT_READY",seq,nonce);}catch(Exception bad){fail();}});}
            }
        }
        if(!framePending||failed||session==null)return;Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getCurrentServer()==null||!mc.getCurrentServer().ip.equals(session.address())||!mc.player.getUUID().equals(session.account())){framePending=false;return;}if(mc.getOverlay()!=null||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen||mc.screen instanceof ConnectScreen)return;framePending=false;worldReported=true;long seq=sequence;UUID nonce=request;
        IO.execute(()->{try{if(seq==sequence&&nonce.equals(request))session.status("WORLD_FRAME_READY",seq,nonce);}catch(Exception bad){fail();}});
    }
}
