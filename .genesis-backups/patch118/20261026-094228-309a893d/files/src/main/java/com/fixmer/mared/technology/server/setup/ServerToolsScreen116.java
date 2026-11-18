package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
import java.nio.file.Path;import java.util.*;
/** Existing tools share one native entry; host paths never become game packet fields. */
public final class ServerToolsScreen116 extends MaredScreen {
 private final Screen previous;private final boolean remoteOnly;private Object connection;private ServerNavigation116.LocalOwner local;private boolean reading,read;
 private Button publication,control,https,health,recovery,transition,access;private int left,w,body,scroll,maxScroll;
 public ServerToolsScreen116(Screen p,boolean remote){super(tr("tools"));previous=p;remoteOnly=remote;}
 private static Component tr(String k,Object...a){return ServerRouteScreen116.tr(k,a);}
 private Button button(String k,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(tr(k),b->action.run()).bounds(x,y,width,20).build());}
 @Override protected void init(){w=Math.max(80,Math.min(620,width-24));left=(width-w)/2;int half=(w-4)/2,y=36;
  button("client_update",left,y,half,this::clientUpdate);button("restart_history",left+half+4,y,half,()->minecraft.setScreen(new com.fixmer.mared.dev.client.RestartHistoryScreen97(this)));y+=26;
  access=button("access",left,y,half,()->minecraft.setScreen(new ServerAccessScreen88(this)));control=button("control",left+half+4,y,half,()->minecraft.setScreen(new DedicatedPanelScreen75(this,local!=null)));y+=26;
  publication=button("publication",left,y,half,()->{if(local!=null)minecraft.setScreen(new PublicationScreen79(this,local.root().toString()));});https=button("https",left+half+4,y,half,()->{if(local!=null)minecraft.setScreen(new HttpsScreen78(this,local.root().toString()));});y+=26;
  health=button("health",left,y,half,()->{if(local!=null)minecraft.setScreen(new PublicationHealthScreen80(this,local.root().toString(),this::adopt));});recovery=button("recovery",left+half+4,y,half,()->{if(local!=null)minecraft.setScreen(new RecoveryScreen77(this,local.root().toString(),this::adopt));});y+=26;
  transition=button("transition",left,y,w,()->minecraft.setScreen(new TransitionScreen81(this,local!=null,local==null?"":local.root().toString(),label())));body=y+30;button("back",left,height-26,w,this::onClose);tick();
 }
 private String label(){try{return DedicatedPanelService75.preferences().get("label").getAsString();}catch(Exception ignored){return "r1";}}
 private void adopt(String value){var p=DedicatedPanelService75.preferences();p.addProperty("root",value);DedicatedPanelService75.save(p);local=null;read=false;}
 private void inspect(){read=true;if(remoteOnly)return;String root;try{root=DedicatedPanelService75.preferences().get("root").getAsString();}catch(Exception ignored){return;}if(root.isBlank())return;reading=true;
  Thread t=new Thread(()->{ServerNavigation116.LocalOwner found=null;try{var owner=ServerNavigation116.owner(Path.of(root));if(ServerNavigation116.publication(owner.owner()))found=owner;}catch(Exception ignored){}var result=found;minecraft.execute(()->{local=result;reading=false;});},"genesis-tools116");t.setDaemon(true);t.start();
 }
 private void clientUpdate(){var d=minecraft.getCurrentServer();String game=Objects.toString(d==null?RemoteService69.game():d.ip,"");String endpoint="";try{endpoint=ServerDirectory76.find(RemoteService69.root(),game);}catch(Exception ignored){}if(endpoint==null)endpoint="";if(endpoint.isEmpty()&&Objects.equals(game,RemoteService69.game()))endpoint=RemoteService69.endpoint();minecraft.setScreen(new BootstrapScreen76(this,game,endpoint,null,false));}
 @Override public void tick(){if(access==null)return;if(connection!=minecraft.getConnection()){connection=minecraft.getConnection();if(remoteOnly){local=null;read=false;}}if(!read)inspect();if(DedicatedPanelService75.hasRemote())DedicatedPanelService75.poll(false,"");
  boolean remote=DedicatedPanelService75.hasRemote(),legacy=ServerNavigation116.remote(remote,DedicatedPanelService75.remote(),DedicatedPanelService75.result(),System.currentTimeMillis())==ServerNavigation116.Destination.REMOTE_GENERATIONS,host=!remoteOnly&&local!=null&&!reading,busy=DedicatedPanelService75.busy();
  publication.active=https.active=health.active=recovery.active=host&&!busy;control.active=transition.active=!busy&&(host||legacy);access.active=remote&&DedicatedPanelService75.canRemote88("PING");
 }
 @Override public void onClose(){minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}@Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*22)));return true;}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xF0151820);g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);var text=new ArrayList<Component>();text.add(tr("tools_help"));if(reading)text.add(tr("route.WAITING"));else if(local==null)text.add(tr("publisher_context"));else text.add(tr("owner_selected",local.root().toString()));var rows=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:text)rows.addAll(font.split(line,Math.max(1,w-8)));int bottom=Math.max(body,height-34),step=font.lineHeight+3;maxScroll=Math.max(0,rows.size()*step-(bottom-body));scroll=Math.min(scroll,maxScroll);if(bottom>body){g.enableScissor(left,body,left+w,bottom);int y=body-scroll;for(var row:rows){if(y+step>=body&&y<bottom)g.drawString(font,row,left,y,0xFFCCD0DC,false);y+=step;}g.disableScissor();}super.render(g,mx,my,dt);}
}
