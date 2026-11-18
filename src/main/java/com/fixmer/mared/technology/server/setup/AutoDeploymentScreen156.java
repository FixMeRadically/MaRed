package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.scripts.BuildSnapshot84;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.screens.*;import net.minecraft.network.chat.Component;
/** Two actions: Apply prepares the signed release; Launch offers it and commits after participants prepare. */
public final class AutoDeploymentScreen156 extends MaredScreen {
 private final boolean once;private final Screen previous;private final BuildSnapshot84.Snapshot snapshot;private Button prepare,launch,sideButton;private boolean started;private int detailsScroll,detailsLimit;
 public AutoDeploymentScreen156(Screen previous,BuildSnapshot84.Snapshot snapshot){this(previous,snapshot,false);}
 public AutoDeploymentScreen156(Screen previous,BuildSnapshot84.Snapshot snapshot,boolean once){super(tr(once?"once.title":"title"));this.previous=previous;this.snapshot=snapshot;this.once=once;}
 private static Component tr(String key,Object...args){return Component.translatable("mared.apply156."+key,args);}
 @Override protected void init(){int x=12,w=Math.max(1,width-24),half=Math.max(1,(w-6)/2);
  if(snapshot!=null)sideButton=addRenderableWidget(Button.builder(tr("side."+AutoDeploymentService156.side(snapshot)),b->{String current=AutoDeploymentService156.side(snapshot),next=current.equals("BOTH")?"SERVER":current.equals("SERVER")?"CLIENT":"BOTH";var prefs=DedicatedPanelService75.preferences();var sides=prefs.has("projectSides156")?prefs.getAsJsonObject("projectSides156"):new com.google.gson.JsonObject();if(sides.size()>=128&&!sides.has(snapshot.projectId()))return;sides.addProperty(snapshot.projectId(),next);prefs.add("projectSides156",sides);DedicatedPanelService75.save(prefs);AutoDeploymentService156.prepare(snapshot);rebuildWidgets();}).bounds(x,34,w,20).build());
  if(snapshot!=null)prepare=addRenderableWidget(Button.builder(tr("prepare"),b->AutoDeploymentService156.prepare(snapshot)).bounds(x,height-50,w,20).build());
  launch=addRenderableWidget(Button.builder(tr("launch"),b->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)AutoDeploymentService156.launch(once);},tr("launch"),tr(once?"once.consent":"consent")))).bounds(x+half+6,height-26,Math.max(1,w-half-6),20).build());
  if(!AutoDeploymentService156.available())addRenderableWidget(Button.builder(Component.translatable("mared.server156.choose_folder"),b->minecraft.setScreen(new ServerWorkspaceScreen156(this,"server"))).bounds(x,height-74,w,20).build());
  if(AutoDeploymentService156.available())addRenderableWidget(Button.builder(Component.translatable("mared.server156.connection"),b->minecraft.setScreen(new ServerConnectionScreen156(this))).bounds(x,height-74,w,20).build());
  addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(x,height-26,half,20).build());
  launch.active=AutoDeploymentService156.canLaunch();
  if(snapshot!=null&&!started){started=true;AutoDeploymentService156.prepare(snapshot);}
 }
 @Override public void tick(){AutoDeploymentService156.refresh();if(sideButton!=null)sideButton.active=!AutoDeploymentService156.busy()&&!AutoDeploymentService156.state().equals("COMMIT_QUEUED");if(prepare!=null)prepare.active=!AutoDeploymentService156.busy();if(launch!=null){launch.active=AutoDeploymentService156.canLaunch();}}
 @Override public void onClose(){minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}
 private static String error156(){String error=AutoDeploymentService156.error(),key="mared.apply156.error."+error;return net.minecraft.client.resources.language.I18n.exists(key)?Component.translatable(key).getString():error;}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
  int top=snapshot==null?36:60;
  if(y<top||y>=height-78||detailsLimit==0)return super.mouseScrolled(x,y,dx,dy);
  detailsScroll=Math.clamp(detailsScroll-(int)(dy*20),0,detailsLimit);
  return true;
 }
 @Override public void render(GuiGraphics g,int mx,int my,float dt){
  g.fill(0,0,width,height,0xFF151820);
  g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);
  int top=snapshot==null?36:60, bottom=Math.max(top,height-78), textWidth=Math.max(1,width-28);
  var values=new java.util.ArrayList<Component>();
  for(String row:new String[]{AutoDeploymentService156.root(),AutoDeploymentService156.label(),tr("state."+AutoDeploymentService156.state()).getString(),error156()}){
   if(!row.isBlank())values.add(Component.literal(row));
  }
  if(AutoDeploymentService156.error().equals("AUTO156_DISTRIBUTION_SETUP_REQUIRED"))values.add(tr("distribution"));
  values.add(tr("help"));
  var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
  for(var value:values)lines.addAll(font.split(value,textWidth));
  int lineHeight=font.lineHeight+3;
  detailsLimit=Math.max(0,lines.size()*lineHeight-(bottom-top));
  detailsScroll=Math.clamp(detailsScroll,0,detailsLimit);
  g.enableScissor(12,top,Math.max(13,width-12),bottom);
  try{
   for(int i=0;i<lines.size();i++){
    int y=top+i*lineHeight-detailsScroll;
    if(y+lineHeight>top&&y<bottom)g.drawString(font,lines.get(i),12,y,0xFFCCD0DC,false);
   }
  }finally{g.disableScissor();}
  super.render(g,mx,my,dt);
 }
}
