package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.Screen;import net.minecraft.client.gui.components.Tooltip;
/** The shared-workspace entry point: six actions, no unrelated developer catalog. */
public final class WorkspaceHome131 extends MaredScreen {
    private final Screen previous;private WorkspaceScreen122 workspace;
    public WorkspaceHome131(Screen previous){super(WorkspaceScreen122.tr("title"));this.previous=previous;}
    private static final String[] SECTIONS={"section_project","section_members","section_history",
            "section_personal","section_advanced","section_delete"};
    private static final int[] ROUTES={0,1,2,3,5,4};

    @Override protected void init(){
        int available=Math.max(1,Math.min(680,width-24));
        int x=(width-available)/2;
        int cols=width>=540||height<222?2:1;
        int regular=SECTIONS.length-1;
        int rows=(regular+cols-1)/cols;
        int gap=5;
        int top=44;
        int cardHeight=Math.max(12,Math.min(34,(height-top-52-rows*gap)/(rows+1)));
        int cardWidth=Math.max(1,(available-gap*(cols-1))/cols);
        for(int i=0;i<regular;i++){
            addSection(i,x+(i%cols)*(cardWidth+gap),top+(i/cols)*(cardHeight+gap),cardWidth,cardHeight);
        }
        int deleteY=top+rows*(cardHeight+gap)+5;
        addSection(regular,x,deleteY,cardWidth,cardHeight);
        addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("back"),b->onClose())
                .bounds(x,height-23,available,18).build());
    }
    private void addSection(int index,int x,int y,int width,int height){
        String key=SECTIONS[index];
        var button=WorkspaceButton131.builder(WorkspaceScreen122.tr(key),b->{
            if(workspace==null)workspace=new WorkspaceScreen122(this);
            minecraft.setScreen(workspace);
            var shared=WorkspaceIntegrated132.current132();
            if(shared!=null)workspace.adopt131(shared.context132());
            workspace.startSection131(ROUTES[index]);
        }).bounds(x,y,width,height).build();
        button.active=minecraft.getConnection()!=null;
        button.setTooltip(Tooltip.create(WorkspaceScreen122.tr(key+".help131")));
        addRenderableWidget(button);
    }
    @Override public void onClose(){minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}@Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());g.drawCenteredString(font,title,width/2,14,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());g.drawCenteredString(font,font.plainSubstrByWidth(WorkspaceScreen122.tr("home_hint131").getString(),Math.max(1,width-24)),width/2,31,com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim());super.render(g,mx,my,dt);}
}
