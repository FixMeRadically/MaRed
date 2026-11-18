package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Signed CLIENT package review and explicit handoff to the external client updater. */
public final class RemoteScreen69 extends MaredScreen {
    private final Screen previous;private EditBox game,endpoint;private Button inspect,trust,download,cancel,reports,install;private int left,bodyWidth,scroll,maxScroll;
    public RemoteScreen69(Screen previous){super(tr("title"));this.previous=previous;}
    private static Component tr(String key,Object...args){return Component.translatable("mared.remote69."+key,args);}
    private Button button(String key,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        String address=game==null?RemoteService69.game():game.getValue(),url=endpoint==null?RemoteService69.endpoint():endpoint.getValue();bodyWidth=Math.max(240,Math.min(660,width-24));left=(width-bodyWidth)/2;
        game=addRenderableWidget(new EditBox(font,left,40,bodyWidth,20,tr("game")));game.setMaxLength(255);game.setValue(address);game.setHint(Component.literal("play.example.org:25565"));
        endpoint=addRenderableWidget(new EditBox(font,left,77,bodyWidth,20,tr("endpoint")));endpoint.setMaxLength(2048);endpoint.setValue(url);endpoint.setHint(Component.literal("https://updates.example.org/genesis/"));
        int bw=(bodyWidth-10)/3;inspect=button("inspect",left,105,bw,()->RemoteService69.inspect(game.getValue(),endpoint.getValue()));trust=button("trust",left+bw+5,105,bw,()->{if(same())RemoteService69.trust();});download=button("download",left+2*(bw+5),105,bw,()->{if(same())RemoteService69.downloadPackage();});
        install=button("install",left,height-52,bodyWidth,()->{if(RemoteService69.download()!=null)minecraft.setScreen(new RemoteInstallScreen70(this));});
        cancel=button("cancel",left,height-27,bw,RemoteService69::cancel);reports=button("files",left+bw+5,height-27,bw,()->{var d=RemoteService69.download();net.minecraft.Util.getPlatform().openFile((d==null?RemoteService69.root():d.directory()).toFile());});button("back",left+2*(bw+5),height-27,bw,this::onClose);tick();
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){boolean busy=RemoteService69.busy();String phase=RemoteService69.phase();game.setEditable(!busy);endpoint.setEditable(!busy);inspect.active=!busy;trust.active=!busy&&phase.equals("UNTRUSTED")&&same();download.active=!busy&&RemoteService69.canDownload86()&&same();cancel.active=busy&&!phase.equals("CANCELLING");reports.active=install.active=RemoteService69.download()!=null&&!busy;}
    private boolean same(){try{return com.fixmer.genesis.technology.server.remote.RemoteOffer69.gameAddress(game.getValue()).equals(RemoteService69.game())&&com.fixmer.genesis.technology.server.remote.RemoteOffer69.base(endpoint.getValue()).equals(RemoteService69.endpoint());}catch(RuntimeException bad){return false;}}
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(y>=135&&y<height-62){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*28)));return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xFF11141B,0xFF1A1D29);g.drawCenteredString(font,title,width/2,12,0xFFF0F1FC);g.drawString(font,tr("game"),left,28,0xFFBEC2D5,false);g.drawString(font,tr("endpoint"),left,65,0xFFBEC2D5,false);
        List<Component> content=new ArrayList<>();content.add(tr("phase."+RemoteService69.phase()));if(!RemoteService69.error().isEmpty())content.add(tr("error."+RemoteService69.error()));
        var preview=RemoteService69.preview();if(preview!=null){var o=preview.offer();content.add(tr("server",o.game()));content.add(tr("release",o.sequence(),o.release().toString()));content.add(tr("version",o.minecraft(),o.neoforge()));content.add(tr("size",(o.bytes()+1023)/1024));content.add(tr("fingerprint"));content.add(Component.literal(o.signer()));if(RemoteService69.busy()&&RemoteService69.phase().equals("DOWNLOADING"))content.add(tr("progress",RemoteService69.received()*100/o.bytes()));}
        if(RemoteService69.phase().equals("UNTRUSTED"))content.add(tr("trust_explanation"));content.add(tr("scope"));
        List<net.minecraft.util.FormattedCharSequence> lines=new ArrayList<>();for(Component line:content)lines.addAll(font.split(line,bodyWidth-9));int row=font.lineHeight+3,top=137,bottom=height-63;maxScroll=Math.max(0,lines.size()*row-(bottom-top));scroll=Math.min(maxScroll,scroll);
        g.enableScissor(left,top,left+bodyWidth,bottom);int y=top-scroll;for(var line:lines){if(y+font.lineHeight>=top&&y<bottom)g.drawString(font,line,left,y,0xFFCCD0DC,false);y+=row;}g.disableScissor();super.render(g,mx,my,partial);
    }
}
