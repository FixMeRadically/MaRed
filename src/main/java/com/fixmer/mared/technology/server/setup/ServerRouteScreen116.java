package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
import java.nio.file.Path;import java.util.*;
/** Resolves the actual owner before opening its native UI; local OS ownership is separate from a player's role. */
public final class ServerRouteScreen116 extends MaredScreen {
 private Button deviceTrust153;private final Screen previous;private java.util.function.Consumer<Screen> destination151;public void destination151(java.util.function.Consumer<Screen> value){destination151=value;}private void show151(Screen value){if(destination151==null)minecraft.setScreen(value);else destination151.accept(value);}private Object connection;private String phase="WAITING";private boolean checked,match,checking;private long since;
 public ServerRouteScreen116(Screen p){super(tr("title"));previous=p;}
 static Component tr(String key,Object...args){return Component.translatable("mared.integration116."+key,args);}
 @Override protected void init(){int w=Math.max(80,Math.min(480,width-24)),x=(width-w)/2;
  deviceTrust153=addRenderableWidget(button151(Component.translatable("mared.auth153.copy"),button->minecraft.keyboardHandler.setClipboard(com.fixmer.mared.technology.permissions.MaopClient153.command())).bounds(x,height-128,w,20).build());
  addRenderableWidget(button151(Component.translatable("mared.auth153.retry"),button->com.fixmer.mared.technology.permissions.MaopClient153.retry()).bounds(x,height-102,w,20).build());
  addRenderableWidget(button151(tr("local_owner"),b->local()).bounds(x,height-76,w,20).build());if(!embedded151())addRenderableWidget(button151(tr("tools"),b->show151(new ServerToolsScreen116(previous,false))).bounds(x,height-50,w,20).build());if(!embedded151())addRenderableWidget(button151(tr("back"),b->onClose()).bounds(x,height-26,w,20).build());tick();
 }
 private void local(){com.fixmer.mared.Mared.LOGGER.info("[Genesis integration116] route=LOCAL_FOLDER");show151(new InPlaceServerScreen104(previous,true));}
 private void checkLocal(){checked=true;String folder=InPlaceServerService104.folder();var data=minecraft.getCurrentServer();if(folder.isBlank()||data==null||data.ip==null)return;
  ServerLocalTarget109.Endpoint endpoint;try{endpoint=ServerLocalTarget109.parse(data.ip);}catch(Exception bad){return;}Object captured=connection;checking=true;
  Thread t=new Thread(()->{boolean yes=false;try{Path path=Path.of(folder);if(ServerLocal105.observe(path).getOrDefault("phase","").equals("RUNNING"))yes=ServerReturnClient108.matchesLocal(new ServerReturnClient108.Ticket(UUID.randomUUID(),endpoint.host(),endpoint.port()),path);}catch(Exception ignored){}boolean result=yes;minecraft.execute(()->{if(connection==captured){match=result;checking=false;}});},"genesis-route116");t.setDaemon(true);t.start();
 }
 @Override public void tick(){if(deviceTrust153!=null)deviceTrust153.active=!com.fixmer.mared.technology.permissions.MaopClient153.fingerprint().isEmpty();Object now=minecraft.getConnection();if(connection!=now||since==0){connection=now;since=System.nanoTime();checked=match=checking=false;}if(!DedicatedPanelService75.hasRemote()){local();return;}if(!checked)checkLocal();if(checking&&System.nanoTime()-since<8_000_000_000L)return;if(match){local();return;}
  DedicatedPanelService75.poll(false,"");var route=ServerNavigation116.remote(true,DedicatedPanelService75.remote(),DedicatedPanelService75.result(),System.currentTimeMillis());phase=route.name();
  if(route==ServerNavigation116.Destination.REMOTE_FOLDER){com.fixmer.mared.Mared.LOGGER.info("[Genesis integration116] route=REMOTE_FOLDER");show151(new InPlaceServerScreen104(previous));}
  else if(route==ServerNavigation116.Destination.REMOTE_GENERATIONS){com.fixmer.mared.Mared.LOGGER.info("[Genesis integration116] route=REMOTE_GENERATIONS");show151(new DedicatedPanelScreen75(previous));}
  else if(route==ServerNavigation116.Destination.WAITING&&System.nanoTime()-since>8_000_000_000L)phase="UNAVAILABLE";
 }
 @Override public void onClose(){minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,panelBackground151());g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);int w=Math.max(1,Math.min(480,width-24)),x=(width-w)/2,y=42;for(var row:font.split(tr("route."+phase),w)){g.drawString(font,row,x,y,0xFFCCD0DC,false);y+=font.lineHeight+3;}if(com.fixmer.mared.technology.permissions.MaopClient153.required()){
   var state=Component.translatable("mared.auth153.state."+(com.fixmer.mared.technology.permissions.MaopClient153.state().equals("READY")&&!com.fixmer.mared.technology.permissions.MaopClient153.controllerReady()?"CONTROLLER_REQUIRED":com.fixmer.mared.technology.permissions.MaopClient153.state()));for(var row:font.split(state,w)){if(y>height-138)break;g.drawString(font,row,x,y,0xffe4bc78,false);y+=font.lineHeight+3;}
   if(y<height-138){g.drawString(font,Component.translatable("mared.auth153.role",com.fixmer.mared.technology.permissions.MaopClient153.role()),x,y,0xffccd0dc,false);y+=font.lineHeight+3;}String fingerprint=com.fixmer.mared.technology.permissions.MaopClient153.fingerprint();if(!fingerprint.isEmpty())for(var row:font.split(Component.literal(fingerprint),w)){if(y>height-138)break;g.drawString(font,row,x,y,0xffccd0dc,false);y+=font.lineHeight+3;}
  }super.render(g,mx,my,dt);}
}
