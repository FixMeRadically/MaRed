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
    private final boolean localOwner;
    private EditBox folder;
    private Button primary,browse,advanced,join128;
    private int left,w,top,scroll,maxScroll;
    private boolean remoteLayout,knownRemote;
    private Object serverConnection;
    private long sent;private UUID sentRequest;
    private String sentRun="",sentGeneration="",notice="";
    public InPlaceServerScreen104(Screen previous){this(previous,false);}
    public InPlaceServerScreen104(Screen previous,boolean localOwner){super(tr("title"));this.previous=previous;this.localOwner=localOwner;}
    private static Component tr(String key,Object...args){return Component.translatable("mared.server104."+key,args);}
    private boolean remote(){
        if(localOwner)return false;
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
            browse=addRenderableWidget(button151(tr("choose"),b->{save();Path initial;try{initial=folder.getValue().isBlank()?minecraft.gameDirectory.toPath():Path.of(folder.getValue());}catch(Exception failed){initial=minecraft.gameDirectory.toPath();}open151(new LocalPickerScreen68(parent151(this),true,initial,p->{InPlaceServerService104.folder(p.toString());folder.setValue(p.toString());}));}).bounds(left+w-64,y+12,64,20).build());y+=42;
        }
        primary=addRenderableWidget(button151(tr("start"),b->start()).bounds(left,y,w,24).build());top=y+34;join128=null;if(!remoteLayout){join128=addRenderableWidget(button151(tr("join128"),b->InPlaceServerService104.join128(parent151(this))).bounds(left,top,w,22).build());top+=30;}
        advanced=addRenderableWidget(button151(tr("advanced"),b->{save();open151(new InPlaceAdvanced104(parent151(this),folder==null?InPlaceServerService104.folder():folder.getValue().trim(),remote()));}).bounds(left,height-50,w,20).build());
        if(!embedded151())addRenderableWidget(button151(tr("back"),b->onClose()).bounds(left,height-26,w,20).build());tick();
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
        if(join128!=null)join128.active=folder!=null&&InPlaceServerService104.matchesFolder128(folder.getValue())&&InPlaceServerService104.canJoin128();
        advanced.active=remote()||!InPlaceServerService104.working();
        if(folder!=null)folder.setEditable(!InPlaceServerService104.working());if(browse!=null)browse.active=!InPlaceServerService104.working();
    }
    private List<Component> lines(){
        var v=view();var lines=new ArrayList<Component>();
        if(!remote()&&state().getOrDefault("launchMode","").equals("BAT"))lines.add(tr("launch_bat124"));
        if(!remote()&&InPlaceServerService104.working())lines.add(tr("working"));
        else if(remote()?sent!=0:InPlaceServerService104.busy())lines.add(tr("queued"));
        else lines.add(tr("phase."+v.phase()));
        String error=remote()?notice:InPlaceServerService104.result();
        if(Set.of("","DONE","QUEUED","REPORT_SAVED").contains(error))error=v.error();
        if(remote()&&error.isBlank()&&Set.of("DENIED","ACCESS88_DENIED","REFUSED").contains(DedicatedPanelService75.result()))error=DedicatedPanelService75.result();
        if(!error.isBlank()){lines.add(tr("error."+ServerView105.hint(error)));lines.add(tr("report_hint"));}
        else if(v.needsReview())lines.add(tr("review_hint"));
        else if(v.phase().equals("RUNNING"))lines.add(tr(remote()?"remote_running":"running_hint"));
        if(!remote()&&v.phase().equals("RUNNING")&&!InPlaceServerService104.joinAddress128().isBlank()){lines.add(tr("address128",InPlaceServerService104.joinAddress128()));lines.add(tr("listed128"));for(String lan:InPlaceServerService104.lanAddresses128())lines.add(tr("lan128",lan));lines.add(tr("internet128"));}
        if(remote()&&!state().getOrDefault("test152_status","").isEmpty()){lines.add(Component.translatable("mared.remote152.last",state().get("test152_status"),state().getOrDefault("test152_target","")));if(!state().getOrDefault("test152_error","").isEmpty())lines.add(Component.literal(state().get("test152_error")));}
        if(com.fixmer.mared.technology.permissions.MaopClient153.required())lines.add(Component.translatable("mared.auth153.state."+com.fixmer.mared.technology.permissions.MaopClient153.state()));
        return lines;
    }
    @Override public void removed(){save();}
    @Override public void onClose(){save();open151(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*22)));return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,panelBackground151());g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);
        if(folder!=null)g.drawString(font,tr("folder"),left,32,0xFFB9BFCA,false);
        var rows=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(Component c:lines())rows.addAll(font.split(c,Math.max(1,w-8)));
        int bottom=Math.max(top,height-58),step=font.lineHeight+3;maxScroll=Math.max(0,rows.size()*step-(bottom-top));scroll=Math.min(scroll,maxScroll);
        if(bottom>top){g.enableScissor(left,top,left+w,bottom);int y=top-scroll;for(var row:rows){if(y+step>=top&&y<bottom)g.drawString(font,row,left,y,0xFFCCD0DC,false);y+=step;}g.disableScissor();}
        super.render(g,mx,my,dt);
    }
}
