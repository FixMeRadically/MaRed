package com.fixmer.mared.dev.client;
import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.fixmer.mared.gui2.genesis.GenesisWorld;
import com.fixmer.mared.gui2.genesis.render.GenesisCoreRenderer;
import com.fixmer.mared.gui2.genesis.render.GenesisRenderTypes;
import net.minecraft.client.gui.GuiGraphics;

/** The same Genesis core and render type as client restart. No fullscreen blur, GUI settings or textures. */
public final class GenesisRestartPainter110 {
    private final GenesisCoreRenderer core=new GenesisCoreRenderer();
    private final GenesisCamera camera=new GenesisCamera();
    public void render(GuiGraphics g,int width,int height,float seconds,int centerY,int radius){
        g.fillGradient(0,0,width,height,0xFF010104,0xFF050510);
        if(radius<=0)return;
        g.flush();g.pose().pushPose();boolean opened=false;
        try{
            g.pose().translate(0,centerY-height/2f,0);
            var vc=g.bufferSource().getBuffer(GenesisRenderTypes.GLOW);opened=true;
            core.render(vc,g.pose().last().pose(),camera,width,height,GenesisWorld.isReducedMotion()?2f:seconds,radius,0xFFFFFFFF);
        }finally{try{if(opened)g.bufferSource().endBatch(GenesisRenderTypes.GLOW);}finally{g.pose().popPose();}}
    }
}
