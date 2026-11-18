package com.fixmer.mared.dev.client;
import com.fixmer.genesis.supervisor.*;import com.fixmer.mared.MaredLang;import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
import java.time.ZoneId;import java.util.*;
/** Local, read-only diagnostics. File IO is asynchronous and publication is scoped to this screen. */
public final class RestartHistoryScreen97 extends MaredScreen {
    private final Screen previous;private HistoryLoad97 loader;private int tab,left,w,bodyTop,bodyBottom,scroll,maxScroll;private Button refresh,copy;private String notice="";private boolean compact;private final List<Button> tabs=new ArrayList<>();
    public RestartHistoryScreen97(Screen previous){super(tr("title"));this.previous=previous;}
    private static Component tr(String key,Object...args){return Component.translatable("mared.history97."+key,args);}
    private Button button(Component label,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(label,b->action.run()).bounds(x,y,Math.max(1,width),20).build());}
    @Override protected void init(){
        w=Math.max(1,Math.min(720,width-24));left=(width-w)/2;tabs.clear();compact=width<240||height<160;
        if(compact){refresh=copy=null;button(tr("back"),left,Math.max(0,height-26),w,this::onClose);return;}int gap=4,bw=(w-2*gap)/3;
        for(int i=0;i<3;i++){final int selected=i;tabs.add(button(tr("tab."+i),left+i*(bw+gap),30,i==2?w-2*(bw+gap):bw,()->{tab=selected;scroll=0;notice="";updateButtons();}));}
        int fw=(w-6)/2;refresh=button(tr("refresh"),left,height-50,fw,()->{notice="";loader.refresh();updateButtons();});
        copy=button(tr("copy"),left+fw+6,height-50,w-fw-6,this::copyReport);
        button(tr("recovery"),left,height-26,fw,()->minecraft.setScreen(new LocalRecoveryScreen91(this)));
        button(tr("back"),left+fw+6,height-26,w-fw-6,this::onClose);
        bodyTop=76;bodyBottom=Math.max(bodyTop,height-58);
        if(loader==null||loader.state().equals("CLOSED")){loader=new HistoryLoad97(minecraft.gameDirectory.toPath(),minecraft::execute,()->minecraft.screen==this);loader.refresh();}
        updateButtons();
    }
    private void copyReport(){if(loader==null||copy==null||!copy.active)return;minecraft.keyboardHandler.setClipboard((loader.state().equals("ERROR")?MaredLang.get("mared.history97.read_failed")+"\n":"")+HistoryPresentation97.clipboard(loader.snapshot(),(key,args)->MaredLang.format(key,args)));notice="copied";}
    private void updateButtons(){if(loader==null||refresh==null)return;boolean loading=loader.state().equals("READING");refresh.active=!loading;copy.active=!loading;for(int i=0;i<tabs.size();i++)tabs.get(i).active=i!=tab;}
    @Override public void tick(){updateButtons();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){if(loader!=null)loader.close();}
    @Override public void onClose(){if(loader!=null)loader.close();minecraft.setScreen(previous);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(x>=left&&x<=left+w&&y>=bodyTop&&y<bodyBottom){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(dy*24)));return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==67&&Screen.hasControlDown()){copyReport();return true;}if(key==294&&loader!=null){notice="";loader.refresh();return true;}if(key==266||key==267||key==268||key==269){scroll=key==268?0:key==269?maxScroll:Math.max(0,Math.min(maxScroll,scroll+(key==266?-Math.max(24,bodyBottom-bodyTop-12):Math.max(24,bodyBottom-bodyTop-12))));return true;}return super.keyPressed(key,scan,mods);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fillGradient(0,0,width,height,0xFF11141B,0xFF1A1D29);g.drawCenteredString(font,title,width/2,10,0xFFF0F1FC);
        if(compact){int y=30;for(var line:font.split(tr("small"),w)){if(y+font.lineHeight>height-32)break;g.drawString(font,line,left,y,0xFFCCD0DC,false);y+=font.lineHeight+2;}super.render(g,mx,my,dt);return;}
        String state=loader==null?"READING":loader.state();Component status=tr(!notice.isEmpty()?notice:state.equals("READING")?"loading":state.equals("ERROR")?"read_failed":"snapshot");
        g.drawString(font,status,left,58,state.equals("ERROR")?0xFFF2B8A2:0xFFBEC2D5,false);
        var snapshot=loader==null?RestartHistory97.Snapshot.empty():loader.snapshot();List<net.minecraft.util.FormattedCharSequence> lines=new ArrayList<>();List<Boolean> headings=new ArrayList<>();
        if(tab==0)for(var part:font.split(tr(DevRestartService.isUnderSupervisor()?"current_managed":"current_unmanaged"),Math.max(1,w-12))){lines.add(part);headings.add(true);}
        boolean emptyPending=(state.equals("READING")||state.equals("ERROR"))&&snapshot.attempts().isEmpty()&&snapshot.cycles().isEmpty();
        if(emptyPending){for(var part:font.split(tr(state.equals("ERROR")?"read_failed":"loading"),Math.max(1,w-12))){lines.add(part);headings.add(false);}}
        else for(var line:HistoryPresentation97.lines(tab,snapshot,ZoneId.systemDefault(),false))for(var part:font.split(Component.translatable(line.key(),line.args().toArray()),Math.max(1,w-12))){lines.add(part);headings.add(line.heading());}
        int row=font.lineHeight+3,available=Math.max(0,bodyBottom-bodyTop);maxScroll=Math.max(0,lines.size()*row-available);scroll=Math.min(scroll,maxScroll);
        if(available>0){g.enableScissor(left,bodyTop,left+w,bodyBottom);int y=bodyTop-scroll;for(int i=0;i<lines.size();i++){if(y+font.lineHeight>=bodyTop&&y<bodyBottom)g.drawString(font,lines.get(i),left+4,y,headings.get(i)?0xFFE0D4FF:0xFFCCD0DC,false);y+=row;}g.disableScissor();}
        if(maxScroll>0&&available>0){int handle=Math.max(8,available*available/(maxScroll+available));int sy=bodyTop+(available-handle)*scroll/maxScroll;g.fill(left+w-3,sy,left+w,sy+handle,0xFFB8A5F2);}
        super.render(g,mx,my,dt);
    }
}
