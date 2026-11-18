package com.fixmer.mared.technology.server.setup;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.*;import net.minecraft.network.chat.Component;
import java.nio.file.*;import java.util.*;
/** Main server workflow. Internal generations/keys/releases belong in advanced tools. */
public final class SimpleServerScreen103 extends MaredScreen {
 private final Screen previous;private boolean local,layoutConfigured;private EditBox source;private Button main,stop,update,journal;private int left,w,body,scroll,maxScroll;private String lastAction="";private long submitted;private String oldGeneration="";
 public SimpleServerScreen103(Screen previous){super(tr("title"));this.previous=previous;local=!DedicatedPanelService75.hasRemote();}
 private static Component tr(String key,Object...args){return Component.translatable("mared.server103."+key,args);}
 private String pref(String k){return SimpleServerService103.pref(k);}
 private boolean configured(){return !pref("root").isBlank();}
 private boolean busy(){return SimpleServerService103.busy();}
 private Map<String,String> state(){if(!local)return DedicatedPanelService75.remote();var result=new HashMap<String,String>();for(var e:DedicatedPanelService75.local().entrySet())try{result.put(e.getKey(),e.getValue().getAsString());}catch(Exception ignored){}return result;}
 private boolean active(Map<String,String> s){return Set.of("ACTIVE_BASELINE","ACTIVE_UPDATED","PREPARED","PREPARATION_FAILED").contains(s.getOrDefault("phase",""))&&!s.getOrDefault("busy","false").equals("true");}
 private Button button(Component text,int y,Runnable fn){return addRenderableWidget(Button.builder(text,b->fn.run()).bounds(left,y,w,20).build());}
 @Override protected void init(){layoutConfigured=configured();w=Math.max(1,Math.min(560,width-24));left=(width-w)/2;int y=34;
  button(tr(local?"local":"connected"),y,()->{saveSource();local=!local;submitted=0;rebuildWidgets();});y+=26;source=null;
  if(local&&!configured()){
   String value=pref("source");if(value.isBlank())try{value=ServerPanelService66.preferencesRead().get("server").getAsString();}catch(Exception ignored){}
   source=addRenderableWidget(new EditBox(font,left,y,w-38,20,tr("folder")));source.setMaxLength(4096);source.setValue(value);source.setHint(tr("folder"));
   addRenderableWidget(Button.builder(Component.literal("…"),b->{saveSource();Path initial;try{initial=source.getValue().isBlank()?minecraft.gameDirectory.toPath():Path.of(source.getValue());}catch(Exception e){initial=minecraft.gameDirectory.toPath();}minecraft.setScreen(new LocalPickerScreen68(this,true,initial,p->{source.setValue(p.toString());saveSource();}));}).bounds(left+w-32,y,32,20).build());y+=26;
  }
  main=button(tr("prepare_start"),y,this::primary);y+=24;
  update=button(tr("update"),y,()->{saveSource();minecraft.setScreen(new PublicationScreen79(this,pref("root")));});y+=24;
  stop=button(tr("stop"),y,()->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)action("SHUTDOWN");},tr("stop"),tr("stop_confirm"))));y+=26;body=y;
  addRenderableWidget(Button.builder(tr("advanced"),b->{saveSource();minecraft.setScreen(new DedicatedPanelScreen75(this));}).bounds(left,height-50,(w-6)/2,20).build());
  journal=addRenderableWidget(Button.builder(tr("journal"),b->{try{Path file=DedicatedPanelService75.bundle().resolve("panel75-error.log");if(Files.isRegularFile(file))net.minecraft.Util.getPlatform().openFile(file.toFile());}catch(Exception ignored){}}).bounds(left+(w+6)/2,height-50,(w-6)/2,20).build());button(tr("back"),height-26,this::onClose);tick();
 }
 private void saveSource(){if(source!=null){var p=DedicatedPanelService75.preferences();p.addProperty("source",source.getValue().trim());DedicatedPanelService75.save(p);}}
 private void primary(){saveSource();var s=state();if(busy())return;
  if(active(s)){if("true".equals(s.get("restart103")))action("RESTART");return;}
  if(!local)return;
  if(SimpleServerService103.state().equals("LAUNCHER")){net.minecraft.Util.getPlatform().openFile(Path.of(pref("root")).toFile());return;}
  if(!configured()){if(source!=null&&!source.getValue().isBlank())SimpleServerService103.setup(source.getValue().trim());return;}
  if(Set.of("STOPPED_SAVED","STOP_NOT_CONFIRMED","FAILED","OWNER_UNAVAILABLE").contains(s.getOrDefault("phase",""))){minecraft.setScreen(new RecoveryScreen77(this,pref("root"),created->{var p=DedicatedPanelService75.preferences();p.addProperty("root",created);DedicatedPanelService75.save(p);}));return;}
  SimpleServerService103.start();
 }
 private void action(String action){if(busy())return;submitted=System.nanoTime();oldGeneration=state().getOrDefault("generation","");lastAction=action;if(local)DedicatedPanelService75.host("command",pref("root"),action.equals("RESTART")?"restart":"stop","",pref("label").isBlank()?"r1":pref("label"));else DedicatedPanelService75.request(action,"r1");}
 @Override public void tick(){if(layoutConfigured!=configured()&&main!=null){rebuildWidgets();return;}DedicatedPanelService75.poll(local,pref("root"));if(main==null)return;var s=state();String phase=s.getOrDefault("phase","");if(submitted!=0&&(!oldGeneration.equals(s.getOrDefault("generation",""))||phase.startsWith("RESTART_")||System.nanoTime()-submitted>10_000_000_000L))submitted=0;
  boolean running=active(s),pending=busy()||submitted!=0||s.getOrDefault("busy","false").equals("true");String label=running?"restart":SimpleServerService103.state().equals("LAUNCHER")?"launcher":!configured()?"prepare_start":Set.of("STOPPED_SAVED","STOP_NOT_CONFIRMED","FAILED","OWNER_UNAVAILABLE").contains(phase)?"recover":"start";
  main.setMessage(tr(label));main.active=!pending&&(local||running&&DedicatedPanelService75.canRemote88("RESTART"))&&(!running||"true".equals(s.get("restart103")))&&(source==null||!source.getValue().isBlank());
  stop.active=!pending&&running&&(local||DedicatedPanelService75.canRemote88("SHUTDOWN"));update.active=!pending&&configured()&&local&&running;if(source!=null)source.setEditable(!busy());if(journal!=null)journal.active=local&&!busy()&&(SimpleServerService103.state().equals("ERROR")||DedicatedPanelService75.result().equals("REFUSED"));
 }
 @Override public void onClose(){saveSource();minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*22)));return true;}
 private List<Component> lines(){var out=new ArrayList<Component>();var s=state();String phase=s.getOrDefault("phase","");
  if(!local&&!DedicatedPanelService75.hasRemote())out.add(tr("connect_hint"));
  else if(!local&&Set.of("ACCESS88_DENIED","DENIED").contains(DedicatedPanelService75.result()))out.add(tr("no_access"));
  else if(!local&&DedicatedPanelService75.result().equals("NOT_MANAGED"))out.add(tr("not_managed"));
  else if(busy())out.add(tr("working"));else if(submitted!=0)out.add(tr(lastAction.equals("RESTART")?"requested_restart":"requested_stop"));else if(phase.startsWith("RESTART_"))out.add(tr("phase."+phase));
  else if(active(s)){out.add(tr("running"));if(!"true".equals(s.get("restart103")))out.add(tr("old_owner"));}
  else if(SimpleServerService103.state().equals("ERROR")){out.add(tr("error"));out.add(tr("error_hint."+errorHint(SimpleServerService103.error())));out.add(Component.literal(SimpleServerService103.error()));}
  else if(SimpleServerService103.state().equals("LAUNCHER"))out.add(tr("launcher_hint"));
  else if(Set.of("STOPPED_SAVED","STOP_NOT_CONFIRMED","FAILED","OWNER_UNAVAILABLE").contains(phase))out.add(tr("recover_hint"));else out.add(tr(configured()?"starting_hint":"setup_hint"));
  if(s.containsKey("failureCode")&&!s.get("failureCode").isBlank()){out.add(tr("error"));out.add(tr("error_hint."+errorHint(s.get("failureCode"))));out.add(Component.literal(s.get("failureCode")));}
  if(local&&!pref("source").isBlank())out.add(tr("selected",pref("source")));
  out.add(tr("restart_hint"));out.add(tr("update_hint"));return out;
 }
 private static String errorHint(String code){return switch(code){case "DEDICATED75_BOOTSTRAP","DEDICATED76_BOOTSTRAP","DEDICATED75_INSTALL_NEW_BOOTSTRAP","DEDICATED76_INSTALL_NEW_BOOTSTRAP"->"mod";case "DEDICATED74_WORLD_IN_USE","DEDICATED74_WORLD_LOCKED"->"live";case "DEDICATED74_EXISTING_WORLD_LOCK_REQUIRED"->"first";case "SERVER103_GENERATION_LIMIT"->"limit";default->"generic";};}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xF0151820);g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(Component c:lines())lines.addAll(font.split(c,Math.max(1,w-8)));int bottom=Math.max(body,height-56),step=font.lineHeight+3;maxScroll=Math.max(0,lines.size()*step-(bottom-body));scroll=Math.min(scroll,maxScroll);if(bottom>body){g.enableScissor(left,body,left+w,bottom);int y=body-scroll;for(var line:lines){if(y+step>=body&&y<bottom)g.drawString(font,line,left,y,0xFFCCD0DC,false);y+=step;}g.disableScissor();}super.render(g,mx,my,dt);}
}
