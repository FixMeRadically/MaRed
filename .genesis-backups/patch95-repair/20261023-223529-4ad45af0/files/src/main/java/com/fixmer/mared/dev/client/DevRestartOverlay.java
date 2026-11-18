package com.fixmer.mared.dev.client;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.fixmer.mared.gui2.genesis.GenesisWorld;
import com.fixmer.mared.gui2.genesis.render.GenesisCoreRenderer;
import com.fixmer.mared.gui2.genesis.render.GenesisRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;

/** Does not replace/close the editor or change the world during checkpointing. Render thread only. */
final class DevRestartOverlay extends Overlay {
    private static DevRestartOverlay active;
    private final GenesisCoreRenderer core=new GenesisCoreRenderer();
    private final GenesisCamera camera=new GenesisCamera();
    private final com.fixmer.genesis.supervisor.RestartSurface surface=DevRestartTransition.begin();
    private final long begin=System.nanoTime();
    private String phase;
    private DevRestartOverlay(String phase){this.phase=phase;}
    static void show(String phase){
        Minecraft mc=Minecraft.getInstance();
        if(mc==null)return;
        mc.execute(()->{
            if(mc.getOverlay() instanceof DevRestartOverlay overlay){overlay.phase=phase;return;}
            if(mc.getOverlay()==null){active=new DevRestartOverlay(phase);mc.setOverlay(active);}
        });
    }
    static void clear(){
        Minecraft mc=Minecraft.getInstance();if(mc==null)return;
        mc.execute(()->{if(active!=null&&mc.getOverlay()==active)mc.setOverlay(null);active=null;DevRestartTransition.clear();});
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        Minecraft mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        g.fillGradient(0,0,w,h,0xFF010104,0xFF050510);
        float seconds=surface==null?(System.nanoTime()-begin)/1_000_000_000f:surface.seconds();
        g.flush();
        var vc=g.bufferSource().getBuffer(GenesisRenderTypes.GLOW);
        core.render(vc,g.pose().last().pose(),camera,w,h,GenesisWorld.isReducedMotion()?2f:seconds+2f,com.fixmer.genesis.supervisor.RestartSurface.coreRadius(h),0xFFFFFFFF);
        g.bufferSource().endBatch(GenesisRenderTypes.GLOW);
        int bottom=com.fixmer.genesis.supervisor.RestartSurface.textTop(h);
        g.drawCenteredString(mc.font,"GENESIS",w/2,bottom,0xFFF0F1FC);
        g.drawCenteredString(mc.font,MaredLang.get(phase),w/2,bottom+15,0xFFBEC2D5);
    }
    @Override public boolean isPauseScreen(){return false;}
}
