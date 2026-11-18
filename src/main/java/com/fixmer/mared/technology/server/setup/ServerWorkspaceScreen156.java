package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.workbench156.ServerFiles156;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.gui2.framework.components.editor.MaredMultiLineEditBox;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** Server folder is the scope of every page, independent of Scripts projects. */
public final class ServerWorkspaceScreen156 extends MaredScreen {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MaRed-server-files156");t.setDaemon(true);return t;});
 private final Screen previous;private final String section;private ServerFiles156 files;private List<ServerFiles156.Entry> catalog=List.of();private ServerFiles156.Document document;private String draft="",group="",query="",status="";private int page;private boolean busy,loaded,closed,raw;private String phase156="",empty156="";private boolean joinAvailable,serverWorking;private EditBox search;private MaredMultiLineEditBox rawBox;
 public ServerWorkspaceScreen156(Screen previous,String section){super(tr("title"));this.previous=previous;this.section=section;}
 private static Component tr(String key,Object...args){return Component.translatable("mared.server156."+key,args);}
 private void capture(){if(search!=null)query=search.getValue();if(rawBox!=null)draft=rawBox.getValue();}
 private Button button(String label,int x,int y,int w,Runnable action){
  var widget=button151(Component.literal(label),b->{if(!busy)action.run();}).bounds(x,y,Math.max(1,w),20).build();
  if(font.width(label)>w-8)widget.setTooltip(Tooltip.create(Component.literal(label)));
  return addRenderableWidget(widget);
 }
 private void work(Callable<Runnable> job){if(busy)return;busy=true;if(search!=null)search.setEditable(false);if(rawBox!=null)rawBox.setEditable(false);IO.execute(()->{try{Runnable done=job.call();minecraft.execute(()->{busy=false;if(!closed){done.run();rebuildWidgets();}});}catch(Exception error){minecraft.execute(()->{busy=false;if(!closed){status=Objects.toString(error.getMessage(),"SERVER156_IO");rebuildWidgets();}});}});}
 private void load(){loaded=true;String folder=InPlaceServerService104.folder();if(folder.isBlank())return;work(()->{var next=new ServerFiles156(Path.of(folder));var rows=next.catalog(section.equals("server_settings156"));return ()->{files=next;catalog=rows;status="";};});}
 private void choose(){capture();Path start;try{start=Path.of(InPlaceServerService104.folder());}catch(Exception e){start=minecraft.gameDirectory.toPath();}open151(new LocalPickerScreen68(parent151(this),true,start,p->{InPlaceServerService104.folder(p.toString());files=null;document=null;group="";query="";page=0;raw=false;loaded=false;load();}));}
 private void open(ServerFiles156.Entry entry){capture();work(()->{var next=files.read(entry.path());var recovered=files.draft(entry.path());return ()->{document=recovered==null?next:new ServerFiles156.Document(next.path(),next.text(),recovered.base());draft=recovered==null?next.text():recovered.text();status=recovered!=null&&!recovered.base().equals(next.hash())?"SERVER156_CHANGED_RELOAD":"";query="";page=0;raw=ConfigValues156.list(next.path(),draft).isEmpty();};});}
 private void save(){capture();var expected=document;String text=draft;if(expected.path().endsWith(".json"))try{com.google.gson.JsonParser.parseString(text);}catch(Exception error){status="SERVER156_INVALID_JSON";return;}work(()->{var next=files.save(expected,text);files.remember(next,next.text());return ()->{document=next;draft=next.text();status="SAVED_RESTART_REQUIRED";};});}
 @Override protected void init(){
  capture();search=null;rawBox=null;empty156="";
  int x=12,w=Math.max(1,width-24);
  if(!loaded)load();
  if(section.equals("server")){
   serverControls(x,w);
   return;
  }
  button(tr("folder").getString(),x,32,w,this::choose);
  if(files==null){
   if(!embedded151())button(tr("back").getString(),x,height-26,w,this::onClose);
   return;
  }
  int y=62;button(tr("back").getString(),x,y,90,()->{capture();if(document!=null){if(!draft.equals(document.text())){minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{minecraft.setScreen(this);if(yes){var storage=files;var before=document;IO.execute(()->{try{storage.remember(before,before.text());}catch(Exception e){com.fixmer.mared.Mared.LOGGER.warn("[Server156] Could not clear draft",e);}});document=null;draft="";query="";page=0;rebuildWidgets();}},tr("discard"),tr("discard_hint")));return;}document=null;query="";}else if(!group.isEmpty()){group="";query="";}else{onClose();return;}page=0;rebuildWidgets();});
  if(document!=null){button(tr("save").getString(),x+96,y,90,this::save);button(tr(raw?"form":"raw").getString(),x+192,y,Math.max(60,w-192),()->{capture();raw=!raw;rebuildWidgets();});}
  if(document!=null&&raw){rawBox=addRenderableWidget(new MaredMultiLineEditBox(x,94,w,Math.max(30,height-146),0xFFFFCC55,()->{}));rawBox.setValue(draft);return;}
  search=addRenderableWidget(new EditBox(font,x,90,w,20,tr("filter")));search.setMaxLength(200);search.setValue(query);search.setResponder(value->{query=value;page=0;});
  // Filter is applied when confirmed, keeping normal text input stable.
  button(tr("filter").getString(),x,height-50,Math.max(30,w/3),()->rebuildWidgets());button("<",x+w/3+4,height-50,40,()->{page=Math.max(0,page-1);rebuildWidgets();});button(">",x+w/3+48,height-50,40,()->{page++;rebuildWidgets();});
  var actions=new ArrayList<Runnable>();var labels=new ArrayList<String>();String needle=query.toLowerCase(Locale.ROOT);
  if(document!=null){for(var value:ConfigValues156.list(document.path(),draft))if((value.key()+" "+value.value()).toLowerCase(Locale.ROOT).contains(needle)){labels.add(value.key()+" = "+value.value());actions.add(()->{if(value.value().equals("true")||value.value().equals("false")){draft=ConfigValues156.replace(document.path(),draft,value.key(),Boolean.toString(!Boolean.parseBoolean(value.value())));rebuildWidgets();}else minecraft.setScreen(new ValueScreen(this,value));});}}
  else if(group.isEmpty()){
   var groups=new TreeMap<String,Integer>();for(var entry:catalog)if((entry.group()+" "+entry.path()).toLowerCase(Locale.ROOT).contains(needle))groups.merge(entry.group(),1,Integer::sum);
   groups.forEach((name,count)->{labels.add(name+" · "+count);actions.add(()->{group=name;query="";page=0;rebuildWidgets();});});
  }else for(var entry:catalog)if(entry.group().equals(group)&&entry.path().toLowerCase(Locale.ROOT).contains(needle)){labels.add(entry.path());actions.add(()->open(entry));}
  if(labels.isEmpty())empty156=tr(query.isBlank()?"empty":"no_results").getString();
  int count=Math.max(1,(height-174)/24),pages=Math.max(1,(labels.size()+count-1)/count);page=Math.min(page,pages-1);
  for(int i=0;i<count&&page*count+i<labels.size();i++){int index=page*count+i;button(labels.get(index),x,118+i*24,w,actions.get(index));}
 }
 private void serverControls(int x,int width){
  phase156=InPlaceServerService104.state().getOrDefault("phase","");
  joinAvailable=files!=null&&InPlaceServerService104.canJoin128();
  serverWorking=InPlaceServerService104.working();
  if(files==null){
   button(tr("folder").getString(),x,48,width,this::choose).active=!busy;
  }else{
   // Keep the primary operations next to the server path and current phase.
   int gap=6,left=Math.max(1,(width-gap)/2),right=Math.max(1,width-gap-left);
   int other=x+left+gap;
   boolean running=phase156.equals("RUNNING");
   button(tr(running?"stop":"start").getString(),x,49,left,()->{
    if(InPlaceServerService104.working())return;
    String phase=InPlaceServerService104.state().getOrDefault("phase","");
    InPlaceServerService104.action(files.root().toString(),phase.equals("RUNNING")?"stop":"start");
   }).active=!busy&&!serverWorking;
   button(tr("join").getString(),other,49,right,()->InPlaceServerService104.join128(parent151(this))).active=joinAvailable;
   button(tr("deployment").getString(),x,77,width,()->open151(new AutoDeploymentScreen156(parent151(this),null)));
   button(tr("folder").getString(),x,105,left,this::choose);
   button(tr("connection").getString(),other,105,right,()->open151(new ServerConnectionScreen156(parent151(this))));
  }
  if(!embedded151())button(tr("back").getString(),x,height-26,width,this::onClose);
 }
 @Override public boolean keyPressed(int key,int scan,int modifiers){
  if(!busy&&!section.equals("server")){
   if((modifiers&15)==2&&key==83&&document!=null){save();return true;}
   if((modifiers&15)==2&&key==70&&search!=null){
    setFocused(search);
    search.setFocused(true);
    return true;
   }
   if((key==257||key==335)&&search!=null&&search.isFocused()){
    page=0;
    rebuildWidgets();
    return true;
   }
  }
  return super.keyPressed(key,scan,modifiers);
 }
 @Override public void tick(){
  if(files==null||!section.equals("server"))return;
  InPlaceServerService104.poll(files.root().toString());
  String phase=InPlaceServerService104.state().getOrDefault("phase","");
  boolean available=InPlaceServerService104.canJoin128();
  if(!phase.equals(phase156)||available!=joinAvailable||serverWorking!=InPlaceServerService104.working()){
   rebuildWidgets();
  }
 }
 @Override public void removed(){capture();if(files!=null&&document!=null){var storage=files;var before=document;String text=draft;IO.execute(()->{try{storage.remember(before,text);}catch(Exception e){com.fixmer.mared.Mared.LOGGER.warn("[Server156] Could not retain configuration draft",e);}});}}
 @Override public void onClose(){capture();closed=true;open151(previous);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,panelBackground151());g.drawCenteredString(font,tr(section.equals("server")?"title":section.equals("server_settings156")?"settings":"configs"),width/2,10,0xFFFFFFFF);super.render(g,mx,my,dt);if(section.equals("server")&&files!=null){String target=files.root()+" · "+phase156;g.drawString(font,font.plainSubstrByWidth(target,Math.max(1,width-24)),12,31,0xFFCCD0DC,false);}if(!empty156.isEmpty()){int y=122;for(var line:font.split(Component.literal(empty156),Math.max(1,width-24))){if(y>=height-62)break;g.drawString(font,line,12,y,0xFFCCD0DC,false);y+=font.lineHeight+3;}}String detail=busy?tr("working").getString():status.equals("SAVED_RESTART_REQUIRED")?tr("saved156").getString():status;if(detail.isEmpty()&&section.equals("server")){String actionResult=InPlaceServerService104.result();if(InPlaceServerService104.working())detail=tr("working").getString();else if(!actionResult.isBlank()&&!Set.of("DONE","QUEUED").contains(actionResult))detail=actionResult;}if(detail.isEmpty()&&files!=null&&!section.equals("server"))detail=document==null?files.root().toString()+(section.equals("server")?" · "+InPlaceServerService104.state().getOrDefault("phase",""):""):document.path();g.drawString(font,font.plainSubstrByWidth(detail,Math.max(1,width-24)),12,height-18,0xFFCCD0DC,false);}
 private static final class ValueScreen extends MaredScreen {
  private final ServerWorkspaceScreen156 parent;private final ConfigValues156.Value value;private EditBox input;private String error="";
  ValueScreen(ServerWorkspaceScreen156 parent,ConfigValues156.Value value){super(Component.literal(value.key()));this.parent=parent;this.value=value;}
  @Override protected void init(){input=addRenderableWidget(new EditBox(font,12,50,Math.max(1,width-24),20,title));input.setMaxLength(16384);input.setValue(value.value());addRenderableWidget(Button.builder(tr("apply"),b->{try{parent.draft=ConfigValues156.replace(parent.document.path(),parent.draft,value.key(),input.getValue());minecraft.setScreen(parent);}catch(Exception e){error=Objects.toString(e.getMessage(),"INVALID");}}).bounds(12,height-50,Math.max(1,width-24),20).build());addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(12,height-26,Math.max(1,width-24),20).build());}
  @Override public void onClose(){minecraft.setScreen(parent);}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xFF151820);g.drawString(font,font.plainSubstrByWidth(value.key(),width-24),12,20,0xFFFFFFFF,false);g.drawString(font,error,12,80,0xFFFF9999,false);super.render(g,mx,my,dt);}
 }
}
