package com.fixmer.mared.dev.client;
import com.fixmer.genesis.supervisor.FailureReport101;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
import java.util.*;
/** Error and the failed launch's logs in the game. No automatic clipboard, shell or process actions. */
public final class FailureScreen101 extends MaredScreen {
 private final Screen previous;private final FailureReport101.Snapshot report;private boolean log;private int scroll,maxScroll,cachedWidth=-1;private List<String> cached=List.of();
 public FailureScreen101(Screen previous,FailureReport101.Snapshot report){super(tr("title"));this.previous=previous;this.report=report;}
 private static Component tr(String key,Object...args){return Component.translatable("mared.failure101."+key,args);}
 @Override protected void init(){int w=Math.max(1,Math.min(520,width-16)),left=(width-w)/2,gap=6,bw=Math.max(1,(w-gap*2)/3);int y=Math.max(0,height-26);
  addRenderableWidget(Button.builder(tr("logs"),b->{log=!log;b.setMessage(tr(log?"summary":"logs"));scroll=0;cachedWidth=-1;}).bounds(left,y,bw,20).build());
  addRenderableWidget(Button.builder(tr("copy"),b->{minecraft.keyboardHandler.setClipboard(summary()+"\n\n"+report.text());}).bounds(left+bw+gap,y,bw,20).build());
  addRenderableWidget(Button.builder(tr("continue"),b->onClose()).bounds(left+2*(bw+gap),y,bw,20).build());
 }
 private String summary(){return tr("state."+report.status()).getString()+"\n"+tr("reason."+report.reason()).getString()+"\n"+tr("world_note").getString()+"\n"+tr("log_note").getString()+"\n"+tr("location","config/mared/dev/failures101/"+report.id()).getString();}
 private void wrap(int w){if(cachedWidth==w)return;cachedWidth=w;var out=new ArrayList<String>();String body=log?report.text():summary();
  for(String paragraph:body.replace("\r","").split("\n",-1)){
   if(out.size()>=3000){out.add("[truncated]");break;}
   String safe=paragraph.codePoints().filter(c->c=='\t'||c>=32&&c!=127&&c!=0xA7&&!(c>=0x202a&&c<=0x202e)&&!(c>=0x2066&&c<=0x2069)).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString().replace("\t","    ");
   if(safe.isEmpty()){out.add("");continue;}
   int offset=0;while(offset<safe.length()&&out.size()<3000){String part=safe.substring(offset,Math.min(safe.length(),offset+1024));String line=font.plainSubstrByWidth(part,w);if(line.isEmpty())line=part.substring(0,1);out.add(line);offset+=line.length();}
  }cached=List.copyOf(out);
 }
 @Override public void onClose(){ClientFailure101.acknowledge(report);minecraft.setScreen(previous);}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(dy<0?3:dy>0?-3:0)));return true;}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256){onClose();return true;}if(key==267)scroll=Math.min(maxScroll,scroll+12);else if(key==266)scroll=Math.max(0,scroll-12);else if(key==268)scroll=0;else if(key==269)scroll=maxScroll;else return super.keyPressed(key,scan,modifiers);return true;}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xFA12131C);g.drawCenteredString(font,title,width/2,9,0xFFFFFFFF);int w=Math.max(1,Math.min(520,width-16)),left=(width-w)/2,top=30,bottom=Math.max(top,height-34);wrap(w);int rows=Math.max(1,(bottom-top)/11);maxScroll=Math.max(0,cached.size()-rows);scroll=Math.min(scroll,maxScroll);g.enableScissor(left,top,left+w,bottom);try{for(int i=scroll;i<Math.min(cached.size(),scroll+rows);i++)g.drawString(font,cached.get(i),left,top+(i-scroll)*11,0xFFDCDCE7,false);}finally{g.disableScissor();}super.render(g,mx,my,dt);}
}
