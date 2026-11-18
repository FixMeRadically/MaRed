package com.fixmer.mared.technology.workspace.live122;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Shared controls have their own neutral styling, independent of the current editor tab. */
public final class WorkspaceButton131 extends Button {
    private WorkspaceButton131(Button.Builder builder){super(builder);}
    public static Button.Builder builder(Component label,Button.OnPress action){return new Button.Builder(label,action){@Override public Button build(){return new WorkspaceButton131(this);}};}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
        boolean hover=isHoveredOrFocused();g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),hover?com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised():com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel());g.renderOutline(getX(),getY(),getWidth(),getHeight(),active&&hover?com.fixmer.mared.technology.editor.GenesisEditorVisuals.text():com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge());
        var font=Minecraft.getInstance().font;String text=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,getWidth()-10));g.drawString(font,text,getX()+(getWidth()-font.width(text))/2,getY()+(getHeight()-font.lineHeight)/2,active?com.fixmer.mared.technology.editor.GenesisEditorVisuals.text():com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(),false);
    }
}
