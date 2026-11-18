package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.ServerView105;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** One primary action; folder-scoped state and errors use the same model as the standalone host. */
public final class InPlaceServerScreen104 extends MaredScreen {
    private final Screen previous;
    private EditBox folder;
    private Button primary,browse,advanced;
    private int left,w,top,scroll,maxScroll;
    private boolean remoteLayout,knownRemote;
    private Object serverConnection;
    private long sent;private UUID sentRequest;
    private String sentRun="",sentGeneration="",notice="";
    public InPlaceServerScreen104(Screen previous){super(tr("title"));this.previous=previous;}
    private static Component tr(String key,Object...args){return Component.translatable("mared.server104."+key,args);}
    private boolean remote(){
        Object connection=minecraft==null?null:minecraft.getConnection();
        if(connection!=serverConnection){serverConnection=connection;knownRemote=false;sent=0;notice="";}
        if(connection!=null&&DedicatedPanelService75.hasRemote()&&DedicatedPanelService75.remote().getOrDefault("inplace104","false").equals("true"))knownRemote=true;
        return connection!=null&&knownRemote;
    }
    private Map<String,String> state(){return remote()?DedicatedPanelService75.remote():InPlaceServerService104.state();}
    private ServerView105.View view(){return ServerView105.of(state(),remote()||folder!=null&&!folder.getValue().isBlank(),remote()?sent!=0:InPlaceServerService104.busy());}
    @Override protected void init(){
        remoteLayout=remote();w=Math.max(80,Math.min(560,width-24));left=(width-w)/2;int y=32;folder=null;browse=null;
        if(!remoteLayout){
            folder=addRenderableWidget(new EditBox(font,left,y+12,w-70,20,tr("folder")));folder.setMaxLength(4096);folder.setValue(InPlaceServerService104.folder());folder.setHint(tr("folder"));
            browse=addRenderableWidget(Button.builder(tr("choose"),b->{save();Path initial;try{initial=folder.getValue().isBlank()?minecraft.gameDirectory.toPath():Path.of(folder.getValue());}catch(Exception failed){initial=minecraft.gameDirectory.toPath();}minecraft.setScreen(new LocalPickerScreen68(this,true,initial,p->{InPlaceServerService104.folder(p.toString());folder.setValue(p.toString());}));}).bounds(left+w-64,y+12,64,20).build());y+=42;
        }
        primary=addRenderableWidget(Button.builder(tr("start"),b->start()).bounds(left,y,w,24).build());top=y+34;
        advanced=addRenderableWidget(Button.builder(tr("advanced"),b->{save();minecraft.setScreen(new InPlaceAdvanced104(this,folder==null?InPlaceServerService104.folder():folder.getValue().trim(),remote()));}).bounds(left,height-50,w,20).build());
        addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(left,height-26,w,20).build());tick();
    }
    private void save(){if(folder!=null)InPlaceServerService104.folder(folder.getValue().trim());}
    private void start(){
        var v=view();if(!v.canStart())return;save();notice="";
        if(remote()){
            sent=System.nanoTime();sentRun=state().getOrDefault("run","");sentGeneration=state().getOrDefault("generation","");
            DedicatedPanelService75.request("RESTART","r1");sentRequest=DedicatedPanelService75.submitted105();
        }else InPlaceServerService104.action(folder.getValue(),v.operation());
    }
    @Override public void tick(){
        if(primary==null)return;if(remoteLayout!=remote()){save();rebuildWidgets();return;}
        if(DedicatedPanelService75.hasRemote())DedicatedPanelService75.poll(false,"");
        if(!remote()&&folder!=null)InPlaceServerService104.poll(folder.getValue());
        if(remote()&&sent!=0){
            var reply=DedicatedPanelService75.control105();String result=reply!=null&&reply.request().equals(sentRequest)?reply.result():DedicatedPanelService75.result();var s=state();var raw=ServerView105.of(s,true,false);
            if(!s.getOrDefault("run","").equals(sentRun)||!s.getOrDefault("generation","").equals(sentGeneration)||!raw.phase().equals("RUNNING"))sent=0;
            else if(Set.of("BUSY","DENIED","ACCESS88_DENIED","NOT_MANAGED","REFUSED","INVALID").contains(result)){notice=result;sent=0;}
            else if(System.nanoTime()-sent>15_000_000_000L){notice="SERVER105_COMMAND_UNCONFIRMED";sent=0;}
        }
        var v=view();primary.setMessage(tr(v.operation().equals("restart")?"restart":"start"));
        primary.active=v.canStart()&&(!remote()||DedicatedPanelService75.canRemote88("RESTART"));
        advanced.active=remote()||!InPlaceServerService104.working();
        if(folder!=null)folder.setEditable(!InPlaceServerService104.working());if(browse!=null)browse.active=!InPlaceServerService104.working();
    }
    private List<Component> lines(){
        var v=view();var lines=new ArrayList<Component>();
        if(!remote()&&InPlaceServerService104.working())lines.add(tr("working"));
        else if(remote()?sent!=0:InPlaceServerService104.busy())lines.add(tr("queued"));
        else lines.add(tr("phase."+v.phase()));
        String error=remote()?notice:InPlaceServerService104.result();
        if(Set.of("","DONE","QUEUED","REPORT_SAVED").contains(error))error=v.error();
        if(remote()&&error.isBlank()&&Set.of("DENIED","ACCESS88_DENIED","REFUSED").contains(DedicatedPanelService75.result()))error=DedicatedPanelService75.result();
        if(!error.isBlank()){lines.add(tr("error."+ServerView105.hint(error)));lines.add(tr("report_hint"));}
        else if(v.needsReview())lines.add(tr("review_hint"));
        else if(v.phase().equals("RUNNING"))lines.add(tr(remote()?"remote_running":"running_hint"));
        return lines;
    }
    @Override public void onClose(){save();minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*22)));return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xF0151820);g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);
        if(folder!=null)g.drawString(font,tr("folder"),left,32,0xFFB9BFCA,false);
        var rows=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(Component c:lines())rows.addAll(font.split(c,Math.max(1,w-8)));
        int bottom=Math.max(top,height-58),step=font.lineHeight+3;maxScroll=Math.max(0,rows.size()*step-(bottom-top));scroll=Math.min(scroll,maxScroll);
        if(bottom>top){g.enableScissor(left,top,left+w,bottom);int y=top-scroll;for(var row:rows){if(y+step>=top&&y<bottom)g.drawString(font,row,left,y,0xFFCCD0DC,false);y+=step;}g.disableScissor();}
        super.render(g,mx,my,dt);
    }
}
