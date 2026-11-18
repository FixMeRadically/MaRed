package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import java.util.function.Consumer;

/** Path entry is kept inside advanced file actions. */
final class WorkspacePath129 extends MaredScreen {
    private final WorkspaceScreen122 previous;
    private final Consumer<String> accepted;
    private String saved;
    private EditBox value;
    WorkspacePath129(WorkspaceScreen122 previous,String initial,Consumer<String> accepted){super(WorkspaceScreen122.tr("path"));this.previous=previous;this.saved=initial;this.accepted=accepted;}
    @Override protected void init(){
        if(value!=null)saved=value.getValue();previous.attachClient();
        int w=Math.max(1,Math.min(650,width-20)),x=(width-w)/2;
        value=addRenderableWidget(new EditBox(font,x,50,w,20,title));value.setMaxLength(240);value.setValue(saved);value.setHint(WorkspaceScreen122.tr("path"));
        addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("continue"),b->{String path=value.getValue();minecraft.setScreen(previous);accepted.accept(path);}).bounds(x,80,w,20).build());
        addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("back"),b->onClose()).bounds(x,height-28,w,20).build());setInitialFocus(value);
    }
    @Override public void tick(){previous.tickClient();}
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());g.drawCenteredString(font,title,width/2,12,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());super.render(g,mx,my,dt);}
}
