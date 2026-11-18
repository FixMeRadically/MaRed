package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.scripts.BuildSnapshot84;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** One review and one explicit confirmation; the existing owner performs save/apply/trial/return. */
public final class ServerUpdateScreen115 extends MaredScreen {
    private final Screen previous;
    private final BuildSnapshot84.Snapshot snapshot;
    private EditBox folder;
    private Button browse,reviewButton,apply,back,players118;
    private String selected,notice="";
    private volatile boolean working;
    private volatile ServerUpdate115.Review reviewed;
    private int left,w,top,scroll,maxScroll;
    private boolean opened;
    public ServerUpdateScreen115(Screen previous,BuildSnapshot84.Snapshot snapshot){
        this(previous,snapshot,InPlaceServerService104.folder());
    }
    public ServerUpdateScreen115(Screen previous,BuildSnapshot84.Snapshot snapshot,String folder){
        super(tr("title"));this.previous=previous;this.snapshot=snapshot;selected=Objects.requireNonNull(folder);
    }
    private static Component tr(String key,Object...args){return Component.translatable("mared.server115."+key,args);}
    @Override protected void init(){
        if(folder!=null)selected=folder.getValue().trim();w=Math.max(80,Math.min(620,width-24));left=(width-w)/2;
        folder=addRenderableWidget(new EditBox(font,left,38,w-70,20,tr("folder")));folder.setMaxLength(4096);folder.setValue(selected);
        folder.setResponder(value->{if(!value.trim().equals(selected)){selected=value.trim();reviewed=null;notice="";}});
        browse=addRenderableWidget(Button.builder(tr("choose"),b->{Path initial;try{initial=selected.isBlank()?minecraft.gameDirectory.toPath():Path.of(selected);}catch(Exception bad){initial=minecraft.gameDirectory.toPath();}minecraft.setScreen(new LocalPickerScreen68(this,true,initial,p->{folder.setValue(p.toString());review();}));}).bounds(left+w-64,38,64,20).build());
        int half=(w-4)/2;reviewButton=addRenderableWidget(Button.builder(tr("review"),b->review()).bounds(left,height-50,half,20).build());
        apply=addRenderableWidget(Button.builder(tr("apply"),b->confirm()).bounds(left+half+4,height-50,half,20).build());
        players118=addRenderableWidget(Button.builder(Component.translatable("mared.folder118.script_release"),b->{if(selected.isBlank()||working)return;try{var p=DedicatedPanelService75.preferences();p.addProperty("root",ServerApp104.root(Path.of(selected)).toString());DedicatedPanelService75.save(p);minecraft.setScreen(new ScriptPublicationScreen84(this,snapshot));}catch(Exception failure){failed(failure);}}).bounds(left,height-74,w,20).build());
        back=addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(left,height-26,w,20).build());top=66;
        if(!opened){opened=true;if(!selected.isBlank())review();}tick();
    }
    private void failed(Exception failure){
        String code=failure.getMessage();notice=code!=null&&code.matches("[A-Z][A-Z0-9_]{1,100}")?code:"SERVER115_OPERATION_FAILED";
        try{ServerApp104.diagnostic(Path.of(selected),failure);}catch(Exception ignored){}
    }
    private void review(){
        if(working||selected.isBlank())return;String scope=selected;reviewed=null;notice="";working=true;
        Thread t=new Thread(()->{ServerUpdate115.Review next=null;Exception error=null;
            try{next=ServerUpdate115.review(Path.of(scope),snapshot.directory());}catch(Exception failed){error=failed;}
            var value=next;var failure=error;Minecraft.getInstance().execute(()->{if(scope.equals(selected)){if(value!=null)folder.setValue(value.server().toString());reviewed=value;if(failure!=null)failed(failure);}working=false;});
        },"genesis-server115-review");t.setDaemon(true);t.start();
    }
    private void confirm(){
        var plan=reviewed;if(working||plan==null)return;
        minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)submit(plan);},tr("confirm_title"),tr("confirm",plan.target())));
    }
    private void submit(ServerUpdate115.Review plan){
        if(working||reviewed!=plan||!plan.server().toString().equals(selected))return;
        working=true;notice="";var ticket=ServerReturnClient108.begin(UUID.randomUUID());InPlaceServerService104.folder(selected);
        Thread t=new Thread(()->{UUID queued=null;boolean local=false;Exception error=null;
            try{if(ticket!=null)try{local=ServerReturnClient108.matchesLocal(ticket,plan.server());}catch(Exception targetUnknown){com.fixmer.mared.Mared.LOGGER.warn("[Genesis server115] Could not verify local reconnect target",targetUnknown);}queued=ServerUpdate115.submit(plan);}catch(Exception failed){error=failed;}
            var request=queued;boolean reconnect=local&&request!=null;var failure=error;
            Minecraft.getInstance().execute(()->{working=false;if(ticket!=null)ServerReturnClient108.answer(ticket.id(),reconnect);
                if(failure!=null){failed(failure);reviewed=null;}else{notice="QUEUED";reviewed=null;if(minecraft.screen==this)minecraft.setScreen(new InPlaceServerScreen104(previous,true));}
            });
        },"genesis-server115-apply");t.setDaemon(true);t.start();
    }
    @Override public void tick(){if(folder==null)return;folder.setEditable(!working);browse.active=!working;reviewButton.active=!working&&!selected.isBlank();apply.active=!working&&reviewed!=null;back.active=!working;players118.active=!working&&!selected.isBlank();}
    @Override public void removed(){if(folder!=null)selected=folder.getValue().trim();}
    @Override public void onClose(){if(!working)minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*22)));return true;}
    private List<Component> lines(){
        var out=new ArrayList<Component>();out.add(tr("build",snapshot.projectId(),snapshot.task()));out.add(tr("mods",String.join(", ",snapshot.modIds())));out.add(tr("scope"));
        if(working)out.add(tr("working"));else if(reviewed!=null){out.add(tr(reviewed.previous().isEmpty()?"addition":"replacement",reviewed.target()));out.add(tr("hash",reviewed.sha256()));out.add(tr("ready"));}
        else if(notice.isBlank())out.add(tr("select"));
        if(!notice.isBlank()){out.add(tr("error."+hint(notice)));out.add(Component.literal(notice));out.add(tr("report"));}return out;
    }
    private static String hint(String code){return switch(code){
        case "SERVER115_NEW_OWNER_REQUIRED"->"owner";
        case "SERVER115_UNCHANGED"->"unchanged";
        case "SERVER115_OWNER_BUSY_OR_CHANGED","SERVER115_QUEUE_BUSY","SERVER115_CHANGE_PENDING"->"busy";
        case "SERVER115_PARTIAL_REPLACEMENT","SERVER115_DUPLICATE_MOD_ID","SERVER115_AMBIGUOUS_REPLACEMENT","SERVER115_PROTECTED_MOD","SERVER115_NAME_COLLISION","SERVER115_MOD_FILENAME"->"mods";
        case "SERVER115_REVIEW_CHANGED","SERVER115_INPUT_CHANGED","SCRIPT84_INPUTS_CHANGED","SCRIPT84_DEPENDENCIES_CHANGED","SCRIPT84_BINARY_CHANGED"->"changed";
        default->"generic";
    };}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xF0151820);g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);g.drawString(font,tr("folder"),left,26,0xFFCCD0DC,false);
        var rows=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:lines())rows.addAll(font.split(line,Math.max(1,w-8)));
        int bottom=Math.max(top,height-82),step=font.lineHeight+3;maxScroll=Math.max(0,rows.size()*step-(bottom-top));scroll=Math.min(scroll,maxScroll);
        if(bottom>top){g.enableScissor(left,top,left+w,bottom);int y=top-scroll;for(var row:rows){if(y+step>=top&&y<bottom)g.drawString(font,row,left,y,0xFFCCD0DC,false);y+=step;}g.disableScissor();}super.render(g,mx,my,dt);
    }
}
