package com.fixmer.mared.dev.client;

import com.fixmer.genesis.supervisor.RestartSurface;
import com.fixmer.mared.dev.DevPaths;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

/** Capture on the client thread before closing its window. Does not alter saved fullscreen options. */
final class DevRestartTransition {
    private static RestartSurface current;
    private DevRestartTransition() { }
    static RestartSurface begin(){
        Minecraft mc=Minecraft.getInstance();if(mc==null)return null;
        try{
            var window=mc.getWindow();int[] x={0},y={0},w={window.getWidth()},h={window.getHeight()};
            GLFW.glfwGetWindowPos(window.getWindow(),x,y);GLFW.glfwGetWindowSize(window.getWindow(),w,h);
            current=new RestartSurface(x[0],y[0],w[0],h[0],window.getGuiScaledWidth(),window.getGuiScaledHeight(),window.isFullscreen(),System.currentTimeMillis(),UUID.randomUUID().toString());
            current.save(DevPaths.file("restart-surface.properties"));
            return current;
        }catch(Exception e){return null;}
    }
    static RestartSurface current(){return current;}
    static void clear(){current=null;}
}
