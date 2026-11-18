package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.ServerView105;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Contextual controls: a recovery acknowledgement is shown only when it is actually required. */
public final class InPlaceAdvanced104 extends MaredScreen {
    private final Screen previous;private final String folder;private final boolean remote;private final Object connection;
    private Button stop,report,open,ack,repair;private int left,w,top,scroll,maxScroll;private String mode="";
    public InPlaceAdvanced104(Screen p,String folder,boolean remote){super(tr("advanced"));previous=p;this.folder=folder;this.remote=remote;connection=net.minecraft.client.Minecraft.getInstance().getConnection();}
    private static Component tr(String key,Object...args){return Component.translatable("mared.server104."+key,args);}
    private boolean sameConnection(){return !remote||connection==minecraft.getConnection();}
    private Map<String,String> state(){return remote?DedicatedPanelService75.remote():InPlaceServerService104.state();}
    private ServerView105.View view(){return ServerView105.of(state(),remote||!folder.isBlank(),!remote&&InPlaceServerService104.busy());}
    private String mode(){return view().needsReview()?state().getOrDefault("repairPending","false"):"none";}
    @Override protected void init(){
        w=Math.max(80,Math.min(560,width-24));left=(width-w)/2;int y=36;mode=mode();report=open=ack=repair=null;
        if(!remote){
            report=addRenderableWidget(Button.builder(tr("report"),b->InPlaceServerService104.action(folder,"report")).bounds(left,y,w,20).build());y+=26;
            open=addRenderableWidget(Button.builder(tr("open_report"),b->{try{Path p=Path.of(InPlaceServerService104.report());net.minecraft.Util.getPlatform().openFile(p.getParent().toFile());}catch(Exception ignored){}}).bounds(left,y,w,20).build());y+=26;
        }
        stop=addRenderableWidget(Button.builder(tr("stop"),b->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes&&sameConnection()){if(remote)DedicatedPanelService75.request("SHUTDOWN","r1");else InPlaceServerService104.action(folder,"stop");}},tr("stop"),tr("stop_confirm")))).bounds(left,y,w,20).build());y+=26;
        if(!remote&&view().needsReview()){
            if(mode.equals("true")){repair=addRenderableWidget(Button.builder(tr("repair"),b->InPlaceServerService104.action(folder,"repair")).bounds(left,y,w,20).build());y+=26;}
            else{ack=addRenderableWidget(Button.builder(tr("acknowledge"),b->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)InPlaceServerService104.action(folder,"acknowledge");},tr("acknowledge"),tr("review_confirm")))).bounds(left,y,w,20).build());y+=26;}
        }
        addRenderableWidget(Button.builder(Component.translatable("mared.integration116.tools"),b->minecraft.setScreen(new ServerToolsScreen116(this,remote))).bounds(left,y,w,20).build());y+=26;
        addRenderableWidget(Button.builder(Component.translatable("mared.folder118.title"),b->minecraft.setScreen(new FolderUpdateScreen118(this,folder,remote))).bounds(left,y,w,20).build());y+=26;
        top=y+4;addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(left,height-26,w,20).build());tick();
    }
    @Override public void tick(){
        if(stop==null)return;if(!remote)InPlaceServerService104.poll(folder);else if(sameConnection())DedicatedPanelService75.poll(false,"");
        if(!mode.equals(mode())){rebuildWidgets();return;}
        boolean busy=!remote&&InPlaceServerService104.working();var v=view();
        stop.active=sameConnection()&&v.canStop()&&(!remote||DedicatedPanelService75.canRemote88("SHUTDOWN"));
        if(report!=null)report.active=!busy&&!folder.isBlank();if(open!=null)open.active=!InPlaceServerService104.report().isBlank();
        if(ack!=null)ack.active=!busy;if(repair!=null)repair.active=!busy;
    }
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*22)));return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xF0151820);g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);
        var text=new ArrayList<Component>();var control=remote?DedicatedPanelService75.control105():null;String result=remote?(control!=null&&!Set.of("QUEUED","STATE").contains(control.result())?control.result():DedicatedPanelService75.result()):InPlaceServerService104.result();
        if(!sameConnection())text.add(tr("connection_changed"));
        else if(!remote&&InPlaceServerService104.working())text.add(tr("working"));
        else if(result.equals("REPORT_SAVED")){text.add(tr("report_saved"));try{text.add(Component.literal(Path.of(InPlaceServerService104.report()).getFileName().toString()));}catch(Exception ignored){}}
        else if(Set.of("DONE","QUEUED","STATE","SENT","IDLE","").contains(result))text.add(tr("phase."+view().phase()));
        else text.add(tr("error."+ServerView105.hint(result)));
        if(remote)text.add(tr("remote_report_hint"));else if(view().needsReview())text.add(tr(mode.equals("true")?"repair_hint":"review_hint"));
        var rows=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(Component line:text)rows.addAll(font.split(line,w));int bottom=Math.max(top,height-34),step=font.lineHeight+3;maxScroll=Math.max(0,rows.size()*step-(bottom-top));scroll=Math.min(scroll,maxScroll);
        if(bottom>top){g.enableScissor(left,top,left+w,bottom);int y=top-scroll;for(var row:rows){if(y+step>=top&&y<bottom)g.drawString(font,row,left,y,0xFFCCD0DC,false);y+=step;}g.disableScissor();}
        super.render(g,mx,my,dt);
    }
}
