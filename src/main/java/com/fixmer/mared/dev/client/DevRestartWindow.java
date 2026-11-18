package com.fixmer.mared.dev.client;

import com.fixmer.genesis.supervisor.RestartSurface;
import com.fixmer.genesis.supervisor.RestartCover;
import com.fixmer.genesis.supervisor.GameSessionClient;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;

/** Render-thread handoff. A delayed cover is retryable; a dismissed cover is terminal. */
public final class DevRestartWindow {
    private static boolean applied,finished,transparent,initializing;
    private static volatile String stage="awaiting_window";
    private static volatile int hooks;
    private static RestartSurface surface;
    private static Path file;
    private static float originalOpacity=1f;
    private static long nextProbe;
    private static RestartCover.State cover=RestartCover.State.UNAVAILABLE;
    private static int readyFrames;
    private static boolean pendingInput;
    private static long inputDeadline,coverHiddenAt,stableInputAt,nextFocusRequest;
    private static int focusRequests;
    private static boolean regrabbed;
    private static long observedForeground,observeUntil;
    private static boolean sawKey,sawMouse;
    /** Called from real Minecraft callbacks; never injects a key or click. */
    public static void inputEvent(long window,boolean keyboard){
        if(observeUntil==0||System.nanoTime()-observeUntil>=0)return;
        if(keyboard?sawKey:sawMouse)return;
        if(keyboard)sawKey=true;else sawMouse=true;
        System.out.println("[Genesis restart] v32 input: callback_observed; kind="+(keyboard?"keyboard":"mouse")+"; foreground="+DevNativeFocus.ownsForeground(window));
    }
    private DevRestartWindow() { }
    private static void state(String value){
        if(!stage.equals(value)){stage=value;System.out.println("[Genesis restart] v32 window: "+value+"; hooks="+hooks);}
    }
    public static void beforeMode(Window window){
        if(applied&&!window.isFullscreen())try{GLFW.glfwSetWindowAttrib(window.getWindow(),GLFW.GLFW_DECORATED,GLFW.GLFW_TRUE);}catch(RuntimeException ignored) { }
    }
    public static void restore(Window window){beforeFrame(window);}
    private static boolean initialize(Window window){
        if(applied)return true;
        if(initializing||finished)return false;
        if(!"1".equals(System.getenv("GENESIS_SUPERVISOR"))){finished=true;state("ordinary_launch");return false;}
        String raw=System.getenv("GENESIS_TRANSITION_FILE");
        if(raw==null){finished=true;state("transition_environment_missing");return false;}
        initializing=true;
        try{
            file=Path.of(raw);surface=RestartSurface.load(file);
            if(surface==null){state("waiting_for_surface");return false;}
            if(surface.fullscreen()){
                GLFW.glfwSetWindowAttrib(window.getWindow(),GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowMonitor(window.getWindow(),0,surface.x(),surface.y(),surface.width(),surface.height(),GLFW.GLFW_DONT_CARE);
            }else{
                GLFW.glfwSetWindowPos(window.getWindow(),surface.x(),surface.y());GLFW.glfwSetWindowSize(window.getWindow(),surface.width(),surface.height());
            }
            applied=true;
            // Announce the protocol before ready can reach the controller, even if the first cover pulse is late.
            GameSessionClient.frameHandoff(true);
            state("waiting_for_cover");return true;
        }catch(RuntimeException|LinkageError unsupported){release(window,"window_setup_unavailable");return false;}
        finally{initializing=false;}
    }
    public static void beforeFrame(Window window){ 
        GameSessionClient.start();
        startupFailure101(window);

        hooks++;
        if(finished||initializing||!initialize(window))return;
        try{
            long now=System.nanoTime();
            if(nextProbe==0||now-nextProbe>=0){nextProbe=now+250_000_000L;cover=RestartCover.state(file,surface.generation(),System.currentTimeMillis());}
            if(cover==RestartCover.State.HIDDEN){release(window,"cover_dismissed");return;}
            if(cover!=RestartCover.State.VISIBLE){
                restoreOpacity(window);state("waiting_for_cover");return;
            }
            if(GLFW.glfwGetWindowMonitor(window.getWindow())!=0)GLFW.glfwSetWindowMonitor(window.getWindow(),0,surface.x(),surface.y(),surface.width(),surface.height(),GLFW.GLFW_DONT_CARE);
            if(!transparent){originalOpacity=GLFW.glfwGetWindowOpacity(window.getWindow());transparent=true;}
            if(GLFW.glfwGetWindowOpacity(window.getWindow())!=0f)GLFW.glfwSetWindowOpacity(window.getWindow(),0f);
            if(GLFW.glfwGetWindowOpacity(window.getWindow())>0.001f){release(window,"opacity_unavailable");return;}
            state("covered");
        }catch(RuntimeException|LinkageError unsupported){release(window,"opacity_unavailable");}
    }
    /** Window.updateDisplay RETURN: two swapped frames without a resource/world loading screen. */
    public static void afterFrame(Window window){
        restoreInput(window);
        if(!applied||finished||!GameSessionClient.frameRequested())return;
        Minecraft mc=Minecraft.getInstance();
        if(mc==null||mc.getOverlay()!=null){readyFrames=0;return;}
        if(GameSessionClient.worldFrameRequested()&&(mc.player==null||mc.level==null||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)){readyFrames=0;return;}
        if(++readyFrames<2)return;
        release(window,"frame_ready");
        com.fixmer.mared.dev.DevTelemetry.mark("frame_ready");
        pendingInput=true;inputDeadline=System.nanoTime()+5_000_000_000L;
        coverHiddenAt=0;stableInputAt=0;nextFocusRequest=0;focusRequests=0;regrabbed=false;observedForeground=0;observeUntil=System.nanoTime()+30_000_000_000L;
        System.out.println("[Genesis restart] v32 input: awaiting_cover_hidden");
    }
    private static void restoreInput(Window window){
        if(!pendingInput)return;
        try{
            long now=System.nanoTime();
            if(now-inputDeadline>=0){pendingInput=false;System.out.println("[Genesis restart] v32 input: timeout; nativeFocus="+(GLFW.glfwGetWindowAttrib(window.getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE));return;}
            // frame_ready only requests cover removal. Wait for the desktop window to acknowledge it.
            if(RestartCover.state(file,surface.generation(),System.currentTimeMillis())!=RestartCover.State.HIDDEN){coverHiddenAt=0;stableInputAt=0;return;}
            if(coverHiddenAt==0){coverHiddenAt=now;return;}
            if(now-coverHiddenAt<150_000_000L)return;
            long foreground=DevNativeFocus.foreground();
            Boolean nativeForeground=DevNativeFocus.ownsForeground(window.getWindow());
            if(observedForeground==0)observedForeground=foreground;
            else if(focusRequests>0&&foreground>0&&foreground!=observedForeground&&!Boolean.TRUE.equals(nativeForeground)){
                pendingInput=false;System.out.println("[Genesis restart] v32 input: user_switched_window");return;
            }
            boolean focused=GLFW.glfwGetWindowAttrib(window.getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE;
            // Ask once even when GLFW's cached flag is true. It does not establish OS foreground ownership.
            if(focusRequests==0||(!focused||Boolean.FALSE.equals(nativeForeground))&&focusRequests<2&&(nextFocusRequest==0||now-nextFocusRequest>=0)){
                focusRequests++;nextFocusRequest=now+350_000_000L;GLFW.glfwFocusWindow(window.getWindow());
                boolean accepted=DevNativeFocus.request(window.getWindow());
                observedForeground=DevNativeFocus.foreground();
                System.out.println("[Genesis restart] v32 input: focus_requested; attempt="+focusRequests+"; win32Accepted="+accepted+"; foreground="+DevNativeFocus.ownsForeground(window.getWindow()));
                stableInputAt=0;return;
            }
            if(!focused||Boolean.FALSE.equals(nativeForeground)){stableInputAt=0;return;}
            Minecraft mc=Minecraft.getInstance();if(mc==null)return;
            // Never synthesize Minecraft focus while Windows reports a different foreground window.
            if(!mc.isWindowActive())mc.setWindowActive(true);
            boolean gameplay=GameSessionClient.worldFrameRequested()&&mc.screen==null;
            if(gameplay){
                if(mc.getOverlay()!=null||mc.player==null||mc.level==null){stableInputAt=0;return;}
                // Rebuild the grab once after the cover is gone, even if the old flags agree.
                // MouseHandler also resets its first-move state and Minecraft key state here.
                if(!regrabbed){mc.mouseHandler.releaseMouse();mc.mouseHandler.grabMouse();regrabbed=true;stableInputAt=0;return;}
                int cursorMode=GLFW.glfwGetInputMode(window.getWindow(),GLFW.GLFW_CURSOR);
                if(mc.mouseHandler.isMouseGrabbed()&&cursorMode!=GLFW.GLFW_CURSOR_DISABLED)mc.mouseHandler.releaseMouse();
                if(!mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.grabMouse();
                if(!mc.mouseHandler.isMouseGrabbed()||GLFW.glfwGetInputMode(window.getWindow(),GLFW.GLFW_CURSOR)!=GLFW.GLFW_CURSOR_DISABLED){stableInputAt=0;return;}
            }else if(mc.screen!=null){
                if(mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.releaseMouse();
                if(GLFW.glfwGetInputMode(window.getWindow(),GLFW.GLFW_CURSOR)==GLFW.GLFW_CURSOR_DISABLED)GLFW.glfwSetInputMode(window.getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
            }
            if(stableInputAt==0){stableInputAt=now;return;}
            if(now-stableInputAt<250_000_000L)return;
            pendingInput=false;
            System.out.println("[Genesis restart] v32 input: "+(gameplay?"handoff_settled":"menu_settled")+"; foreground="+nativeForeground+"; coverHidden=true; nativeFocus=true; gameActive="+mc.isWindowActive()+"; mouseGrabbed="+mc.mouseHandler.isMouseGrabbed());
        }catch(RuntimeException|LinkageError unavailable){pendingInput=false;System.out.println("[Genesis restart] v32 input: unavailable");}
    }
    private static boolean stopping101;
    private static void startupFailure101(Window window){
        if(System.getenv("GENESIS_MOD_TRIAL")==null)return;
        try{
            Minecraft mc=Minecraft.getInstance();if(mc==null)return;
            String failure=com.fixmer.genesis.supervisor.StartupErrorProbe101.classify(mc.screen);
            GameSessionClient.attention101(failure.equals("ATTENTION"));
            if(!failure.isEmpty()){
                release(window,"loader_attention101");
                if(failure.equals("LOADER_ERROR"))GameSessionClient.failure101(failure);
            }
            if(!stopping101&&GameSessionClient.abortRequested101()&&mc.level==null&&mc.getSingleplayerServer()==null){
                stopping101=true;release(window,"startup_abort101");GameSessionClient.normalClosing();mc.stop();
            }
        }catch(RuntimeException|LinkageError unavailable){/* Reveal errors via the controller timeout if an optional provider is unavailable. */}
    }
    private static void restoreOpacity(Window window){
        if(transparent){GLFW.glfwSetWindowOpacity(window.getWindow(),originalOpacity);transparent=false;}
    }
    private static void release(Window window,String reason){
        try{restoreOpacity(window);}
        catch(RuntimeException|LinkageError ignored){}
        finally{transparent=false;finished=true;GameSessionClient.frameHandoff(false);state(reason);}
    }
}
