package com.fixmer.mared.technology.server.consent.client;

import com.fixmer.genesis.technology.server.waiting.GameConsentBridge.View;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.technology.server.consent.GenesisConsentPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Small consent-only UI, using the normal pixel font; never claims download/install completion. */
public final class GenesisConsentScreen extends MaredScreen {
    private final Screen previous;private final Runnable dismissed;private View view;private long received,sent;
    private UUID request;private Boolean choice;private int attempts;private Button accept,decline,retry;private boolean stale;
    public GenesisConsentScreen(Screen previous,View view,long received,Runnable dismissed){super(Component.translatable("mared.consent57.title"));this.previous=previous;this.view=view;this.received=received;this.dismissed=dismissed;}
    Screen previous(){return previous;}
    public boolean same(View next){return view.instance().equals(next.instance())&&view.update().equals(next.update())&&view.packageSha256().equals(next.packageSha256())&&view.offer().equals(next.offer());}
    public void update(View next,long time){view=next;received=time;stale=false;buttons();}
    @Override protected void init(){
        int bw=Math.min(150,Math.max(80,(width-36)/2)),x=width/2,top=Math.max(25,height-79);
        accept=addRenderableWidget(Button.builder(Component.translatable("mared.consent57.accept"),b->send(true)).bounds(x-bw-4,top,bw,20).build());
        decline=addRenderableWidget(Button.builder(Component.translatable("mared.consent57.decline"),b->send(false)).bounds(x+4,top,bw,20).build());
        retry=addRenderableWidget(Button.builder(Component.translatable("mared.consent57.retry"),b->send(Boolean.TRUE.equals(choice))).bounds(x-bw-4,top+25,bw,20).build());
        addRenderableWidget(Button.builder(Component.translatable("mared.consent57.close"),b->onClose()).bounds(x+4,top+25,bw,20).build());buttons();
    }
    private long remaining(){return Math.max(0,view.remainingMillis()-Math.max(0,(System.nanoTime()-received)/1000000));}
    private boolean pending(){return view.decision().equals("PENDING")&&!view.phase().equals("CANCELLED")&&!view.phase().equals("SUSPENDED")&&!view.paused()&&remaining()>0;}
    private void buttons(){if(accept==null)return;accept.active=decline.active=pending()&&request==null&&!stale;retry.active=pending()&&request!=null&&attempts<3&&System.nanoTime()-sent>=7_000_000_000L&&!stale;}
    private void send(boolean decision){
        if(!pending()||stale||attempts>=3||minecraft.getConnection()==null||!minecraft.getConnection().hasChannel(GenesisConsentPayloads.Choice.TYPE.id()))return;
        if(request==null){request=UUID.randomUUID();choice=decision;}else if(choice!=decision)return;
        sent=System.nanoTime();attempts++;PacketDistributor.sendToServer(new GenesisConsentPayloads.Choice(view.update(),view.packageSha256(),view.offer(),request,decision));buttons();
    }
    @Override public void tick(){stale=System.nanoTime()-received>10_000_000_000L;buttons();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){dismissed.run();minecraft.setScreen(previous);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,0xF0151820);int x=width/2,y=12;
        g.drawCenteredString(font,title,x,y,0xFFFFFFFF);y+=23;
        int textWidth=Math.max(80,Math.min(520,width-28));
        for(var line:font.split(Component.translatable("client".equals(System.getenv("GENESIS_FIRST58_ROLE"))?"mared.first58.explanation":"mared.consent57.explanation"),textWidth)){g.drawString(font,line,x-textWidth/2,y,0xFFCCCCDD,false);y+=font.lineHeight+2;}
        y+=9;g.drawCenteredString(font,Component.translatable("mared.consent57.package",view.packageSha256().substring(0,12)),x,y,0xFF8899BB);y+=18;
        String key=stale?"stale":view.phase().equals("SUSPENDED")?"suspended":view.phase().equals("CANCELLED")?"cancelled":view.paused()?"paused":view.decision().equals("ACCEPTED")?"accepted":view.decision().equals("DECLINED")?"declined":view.decision().equals("EXPIRED")||remaining()==0?"expired":request!=null?"sending":"remaining";
        // Reserve widget area on short windows; labels wrap instead of spilling past the right edge.
        for(var line:font.split(Component.translatable("mared.consent57."+key,(remaining()+999)/1000),textWidth)){if(y>=height-89)break;g.drawString(font,line,x-textWidth/2,y,0xFFFFCC88,false);y+=font.lineHeight+2;}
        super.render(g,mouseX,mouseY,delta);
    }
}
