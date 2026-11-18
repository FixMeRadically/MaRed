package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;import java.util.Map;
public final class WorkspaceDiffScreen126 extends MaredScreen {
    private final Screen previous;private final WorkspaceDiffPanel131 panel=new WorkspaceDiffPanel131();
    public WorkspaceDiffScreen126(Screen previous,Map<String,String> data){super(Component.literal(data.getOrDefault("path","")));this.previous=previous;panel.set(data);}
    @Override protected void init(){addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("back"),b->onClose()).bounds(12,height-28,width-24,20).build());}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(hasShiftDown())panel.horizontal=Math.max(0,panel.horizontal+(int)(-dy*8));else panel.scroll=Math.max(0,panel.scroll+(int)(-dy*3));return true;}
    @Override public void tick(){previous.tick();}@Override public void onClose(){minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());panel.render(g,font,12,12,width-24,height-48);super.render(g,mx,my,dt);}
}
