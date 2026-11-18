package com.fixmer.mared.gui2.framework.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Custom MaRed screens own their background. Vanilla Screen.render must never blur their completed frame. */
public abstract class MaredScreen extends Screen {
    protected MaredScreen(Component title) { super(title); }
    private Screen embeddedRoot151;
    public final void embedded151(Screen root){embeddedRoot151=root;}
    protected final boolean embedded151(){return embeddedRoot151!=null;}
    protected final Screen parent151(Screen usual){return embeddedRoot151==null?usual:embeddedRoot151;}
    protected final void open151(Screen next){
        if(embeddedRoot151 instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell)shell.suspendForSettings();
        net.minecraft.client.Minecraft.getInstance().setScreen(next);
    }
    protected final int panelBackground151(){return embedded151()?com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanel:0xF0151820;}
    protected final net.minecraft.client.gui.components.Button.Builder button151(Component label,net.minecraft.client.gui.components.Button.OnPress action){
        return new net.minecraft.client.gui.components.Button.Builder(label,action){
            @Override public net.minecraft.client.gui.components.Button build(){return embedded151()?new PanelButton151(this):super.build();}
        };
    }
    private static final class PanelButton151 extends net.minecraft.client.gui.components.Button {
        PanelButton151(net.minecraft.client.gui.components.Button.Builder builder){super(builder);}
        @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
            com.fixmer.mared.gui2.development.DevelopmentButton129.draw(graphics,getX(),getY(),getWidth(),getHeight(),getMessage(),active,isHoveredOrFocused(),false);
        }
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
}
