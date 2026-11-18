package com.fixmer.mared.technology.server.remote.client;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.remote.RemoteConsent72.*;
import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.technology.server.remote.RemoteConsentPayloads72;
import com.fixmer.mared.technology.server.setup.RemoteService69;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Consent only: verifies HTTPS identity before a positive answer; NEVER downloads, installs or stops Minecraft. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class RemoteConsentClient72 {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"genesis-remote72-client");t.setDaemon(true);return t;});
    private static final AtomicBoolean busy=new AtomicBoolean();private static AtomicBoolean cancelled=new AtomicBoolean();
    private static Object connection;private static Frame view;private static long received,deadline;private static int generation;private static boolean needInspect,needTrust;private static String verification="CHECKING";private static RemoteClient69.Preview preview;private static RemoteClient69 client;private static ConsentScreen screen;private static UUID dismissed,request;private static Boolean answer;private static long sent;private static int attempts;
    static{RemoteConsentPayloads72.clientSink=o->receive(o.frame());}
    private RemoteConsentClient72(){}private static Component tr(String key,Object...args){return Component.translatable("mared.remote72."+key,args);}
    private static boolean same(Frame a,Frame b){return a!=null&&a.instance().equals(b.instance())&&a.round().equals(b.round())&&a.ticket().equals(b.ticket());}
    private static boolean binding(Frame a,Frame b){return same(a,b)&&a.release().equals(b.release())&&a.distribution().equals(b.distribution())&&a.sha().equals(b.sha())&&a.game().equals(b.game())&&a.endpoint().equals(b.endpoint())&&a.signer().equals(b.signer())&&a.revision()==b.revision();}
    private static void reset(){cancelled.set(true);generation++;connection=null;view=null;preview=null;client=null;dismissed=null;request=null;answer=null;attempts=0;needInspect=needTrust=false;Minecraft mc=Minecraft.getInstance();if(mc.screen==screen)mc.setScreen(null);screen=null;}
    private static void receive(Frame next){
        Minecraft mc=Minecraft.getInstance();if(mc.getConnection()==null||mc.getCurrentServer()==null)return;if(connection!=mc.getConnection())reset();connection=mc.getConnection();long now=System.nanoTime();
        if(same(view,next)){
            if(next.serial()<=view.serial())return;if(!binding(view,next)){verification="MISMATCH";cancelled.set(true);return;}deadline=Math.min(deadline,now+next.remaining()*1000000);
        }else{
            cancelled.set(true);cancelled=new AtomicBoolean();generation++;preview=null;client=null;request=null;answer=null;attempts=0;deadline=now+next.remaining()*1000000;verification="CHECKING";needInspect=true;needTrust=false;
        }
        view=next;received=now;if(next.phase().equals("CANCELLED")){cancelled.set(true);needInspect=needTrust=false;}
        if(!Objects.equals(dismissed,next.ticket())&&!next.phase().equals("CANCELLED")){
            if(screen==null||mc.screen!=screen){Screen previous=mc.screen;screen=new ConsentScreen(previous);mc.setScreen(screen);}
        }
    }
    private static long remaining(){return Math.max(0,(deadline-System.nanoTime())/1000000);}
    private static boolean pending(){return view!=null&&view.phase().equals("ACTIVE")&&view.decision().equals("PENDING")&&remaining()>0&&System.nanoTime()-received<10_000_000_000L;}
    private static boolean matches(Frame frame,RemoteClient69.Preview p){var o=p.offer();return o.release().equals(frame.release())&&o.distribution().equals(frame.distribution())&&o.archiveSha().equals(frame.sha())&&o.game().equals(frame.game())&&o.endpoint().equals(frame.endpoint())&&o.signer().equals(frame.signer())&&o.sequence()==frame.revision();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(connection!=null&&connection!=mc.getConnection()){reset();return;}if(view==null||!pending()||!(needInspect||needTrust)||!busy.compareAndSet(false,true))return;
        final Frame frame=view;final int epoch=generation;final Object expected=connection;final AtomicBoolean cancel=cancelled;final boolean trust=needTrust;final RemoteClient69.Preview original=preview;final RemoteClient69 existing=client;needInspect=needTrust=false;verification="CHECKING";
        String address=mc.getCurrentServer()==null?"":mc.getCurrentServer().ip;String neo=ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();
        IO.execute(()->{RemoteClient69 c=existing;RemoteClient69.Preview p=null;String result="FAILED";
            try{if(!RemoteOffer69.gameAddress(address).equals(frame.game())){result="ADDRESS";throw new IllegalStateException();}if(c==null)c=new RemoteClient69(RemoteService69.root(),"1.21.1",neo);
                if(trust){if(original==null||!matches(frame,original)||cancel.get())throw new IllegalStateException();c.trust(original);}
                p=c.inspect(frame.game(),frame.endpoint(),cancel::get);if(!matches(frame,p))result="MISMATCH";else result=p.trusted()?"TRUSTED":"UNTRUSTED";
            }catch(Exception ignored){}final RemoteClient69 use=c;final RemoteClient69.Preview checked=p;final String status=result;
            mc.execute(()->{if(epoch==generation&&connection==expected&&!cancel.get()&&binding(view,frame)){client=use;preview=checked;verification=status;}});busy.set(false);
        });
    }
    private static void choose(boolean accept){
        Minecraft mc=Minecraft.getInstance();if(!pending()||attempts>=3||mc.getConnection()!=connection||!mc.getConnection().hasChannel(RemoteConsentPayloads72.Answer.TYPE.id())||accept&&!verification.equals("TRUSTED"))return;
        if(request==null){request=UUID.randomUUID();answer=accept;}else if(!Objects.equals(answer,accept)||System.nanoTime()-sent<2_000_000_000L)return;
        sent=System.nanoTime();attempts++;PacketDistributor.sendToServer(new RemoteConsentPayloads72.Answer(new Choice(view.round(),view.release(),view.sha(),view.ticket(),request,accept)));
    }
    private static final class ConsentScreen extends MaredScreen {
        private final Screen previous;private Button yes,no,trust;private int scroll,maxScroll;
        ConsentScreen(Screen previous){super(tr("title"));this.previous=previous;}
        @Override protected void init(){int w=Math.max(180,Math.min(520,width-24)),left=(width-w)/2,bw=(w-6)/2;
            yes=addRenderableWidget(Button.builder(tr("accept"),b->choose(true)).bounds(left,height-78,bw,20).build());no=addRenderableWidget(Button.builder(tr("decline"),b->choose(false)).bounds(left+bw+6,height-78,bw,20).build());
            trust=addRenderableWidget(Button.builder(tr("trust"),b->{if(pending()&&!busy.get()){if(verification.equals("UNTRUSTED"))needTrust=true;else if(Set.of("FAILED","MISMATCH").contains(verification))needInspect=true;}}).bounds(left,height-53,w,20).build());
            addRenderableWidget(Button.builder(tr("close"),b->onClose()).bounds(left,height-28,w,20).build());tick();}
        @Override public void tick(){yes.active=pending()&&verification.equals("TRUSTED")&&(request==null||Boolean.TRUE.equals(answer)&&attempts<3&&System.nanoTime()-sent>=2_000_000_000L);no.active=pending()&&(request==null||Boolean.FALSE.equals(answer)&&attempts<3&&System.nanoTime()-sent>=2_000_000_000L);trust.setMessage(tr(verification.equals("UNTRUSTED")?"trust":"retry"));trust.active=pending()&&Set.of("UNTRUSTED","FAILED","MISMATCH").contains(verification)&&!busy.get();}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){if(view!=null)dismissed=view.ticket();minecraft.setScreen(previous);}
        @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*24)));return true;}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){
            g.fill(0,0,width,height,0xF0151820);g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);int w=Math.max(120,Math.min(520,width-24)),left=(width-w)/2;List<Component> text=new ArrayList<>();
            if(view!=null){text.add(tr("explanation"));text.add(tr("server",view.game()));text.add(tr("release",view.release().toString()));text.add(tr("key",view.signer()));String phase=view.phase().equals("CANCELLED")?"CANCELLED":System.nanoTime()-received>10_000_000_000L?"STALE":view.decision();text.add(tr("decision."+phase,(remaining()+999)/1000));if(view.decision().equals("PENDING")&&!view.phase().equals("CANCELLED"))text.add(tr("verify."+verification));text.add(tr("consent_only"));}
            List<net.minecraft.util.FormattedCharSequence> lines=new ArrayList<>();for(Component c:text)lines.addAll(font.split(c,w));int row=font.lineHeight+3,top=35,bottom=height-85;maxScroll=Math.max(0,lines.size()*row-(bottom-top));scroll=Math.min(scroll,maxScroll);g.enableScissor(left,top,left+w,bottom);int y=top-scroll;for(var line:lines){if(y+font.lineHeight>=top&&y<bottom)g.drawString(font,line,left,y,0xFFCCD0DC,false);y+=row;}g.disableScissor();super.render(g,mx,my,delta);
        }
    }
}
