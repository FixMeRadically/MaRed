package com.fixmer.mared.technology.server.setup;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.*;import net.minecraft.network.chat.Component;import com.fixmer.genesis.technology.server.remote.ServerApp104;import java.nio.file.*;
/** Advanced-only tools. Acknowledging a world review is never part of the normal start button. */
public final class InPlaceAdvanced104 extends MaredScreen {
 private final Screen previous;private final String folder;private final boolean remote;
 public InPlaceAdvanced104(Screen p,String folder,boolean remote){super(tr("advanced"));previous=p;this.folder=folder;this.remote=remote;}
 private static Component tr(String key,Object...args){return Component.translatable("mared.server104."+key,args);}
 @Override protected void init(){int w=Math.max(1,Math.min(560,width-24)),x=(width-w)/2,y=42;
  if(!remote){addRenderableWidget(Button.builder(tr("logs"),b->{try{Path logs=ServerApp104.root(Path.of(folder)).resolve("logs");Files.createDirectories(logs);net.minecraft.Util.getPlatform().openFile(logs.toFile());}catch(Exception ignored){}}).bounds(x,y,w,20).build());y+=26;}
  addRenderableWidget(Button.builder(tr("stop"),b->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes){if(remote)DedicatedPanelService75.request("SHUTDOWN","r1");else InPlaceServerService104.action(folder,"stop");}},tr("stop"),tr("stop_confirm")))).bounds(x,y,w,20).build());y+=26;
  if(!remote){addRenderableWidget(Button.builder(tr("acknowledge"),b->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)InPlaceServerService104.action(folder,"acknowledge");},tr("acknowledge"),tr("review_confirm")))).bounds(x,y,w,20).build());y+=26;}
  addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(x,height-26,w,20).build());
 }
 @Override public void onClose(){minecraft.setScreen(previous);}@Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xF0151820);g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);super.render(g,mx,my,dt);}
}
