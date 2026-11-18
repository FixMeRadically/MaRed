package com.fixmer.mared.technology.server.setup;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.*;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
/** One-time transport setup, independent of release names and mod set preparation. */
public final class ServerConnectionScreen156 extends MaredScreen {
 private final Screen previous;private String owner156="";private final java.util.List<EditBox> fields=new java.util.ArrayList<>();private String error="";
 private final String[] values=new String[5];
 private int page,pageSize,first;
 private void capture(){for(int i=0;i<fields.size();i++)values[first+i]=fields.get(i).getValue();}

 public static com.google.gson.JsonObject forRoot(String owner){var prefs=DedicatedPanelService75.preferences();try{return prefs.getAsJsonObject("connections156").getAsJsonObject(owner).deepCopy();}catch(Exception missing){if(prefs.has("root")&&prefs.get("root").getAsString().equals(owner))return prefs;return new com.google.gson.JsonObject();}}
 private static final String[] KEYS={"publication79game","publication79endpoint","auto156cert","auto156key","auto156bind"};
 public ServerConnectionScreen156(Screen previous){super(Component.translatable("mared.server156.connection"));this.previous=previous;try{owner156=com.fixmer.genesis.technology.server.remote.ServerApp104.root(java.nio.file.Path.of(InPlaceServerService104.folder())).toString();}catch(Exception ignored){}}
 @Override protected void init(){
  capture();
  fields.clear();
  if(values[0]==null){
   var prefs=forRoot(owner156);
   String[] defaults={"localhost:25565","https://localhost:8443/","","","127.0.0.1"};
   for(int i=0;i<KEYS.length;i++)values[i]=prefs.has(KEYS[i])?prefs.get(KEYS[i]).getAsString():defaults[i];
  }
  pageSize=Math.clamp((height-108)/29,1,KEYS.length);
  int pages=(KEYS.length+pageSize-1)/pageSize;
  page=Math.clamp(page,0,pages-1);
  first=page*pageSize;
  int w=Math.max(1,Math.min(580,width-24)),x=(width-w)/2;
  for(int i=first;i<Math.min(KEYS.length,first+pageSize);i++){
   var box=addRenderableWidget(new EditBox(font,x,40+(i-first)*29,w,17,fieldName(i)));
   box.setMaxLength(4096);box.setValue(values[i]);fields.add(box);
  }
  if(pages>1){
   addRenderableWidget(Button.builder(Component.literal("‹"),b->{page--;rebuildWidgets();}).bounds(x,height-55,24,18).build()).active=page>0;
   addRenderableWidget(Button.builder(Component.literal("›"),b->{page++;rebuildWidgets();}).bounds(x+w-24,height-55,24,18).build()).active=page+1<pages;
  }
  int half=Math.max(1,(w-6)/2);
  addRenderableWidget(Button.builder(Component.translatable("mared.server156.back"),b->onClose()).bounds(x,height-26,half,20).build());
  addRenderableWidget(Button.builder(Component.translatable("mared.server156.save"),b->save()).bounds(x+half+6,height-26,Math.max(1,w-half-6),20).build());
 }
 private static Component fieldName(int index){return Component.translatable("mared.server156.network."+index);}
 private void save(){
  capture();
  try{
   var uri=java.net.URI.create(values[1].trim());
   if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("AUTO156_HTTPS_ADDRESS");
   if(!values[0].matches("[^\\s/]+:[0-9]{1,5}"))throw new IllegalArgumentException("AUTO156_GAME_ADDRESS");
   var p=DedicatedPanelService75.preferences();
   var byServer=p.has("connections156")?p.getAsJsonObject("connections156"):new com.google.gson.JsonObject();
   if(!byServer.has(owner156)&&byServer.size()>=16)throw new IllegalStateException("AUTO156_PROFILE_LIMIT");
   var result=new com.google.gson.JsonObject();
   for(int i=0;i<KEYS.length;i++){
    String value=values[i].trim();
    result.addProperty(KEYS[i],i==1&&!value.endsWith("/")?value+"/":value);
   }
   byServer.add(owner156,result);p.add("connections156",byServer);DedicatedPanelService75.save(p);onClose();
  }catch(Exception e){error=java.util.Objects.toString(e.getMessage(),"INVALID");}
 }
 @Override public boolean keyPressed(int key,int scan,int mods){
  if(key==83&&(mods&15)==2){save();return true;}
  return super.keyPressed(key,scan,mods);
 }
 @Override public void onClose(){minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){
  g.fill(0,0,width,height,0xFF151820);
  g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);
  int w=Math.max(1,Math.min(580,width-24)),x=(width-w)/2;
  for(int i=0;i<fields.size();i++)g.drawString(font,font.plainSubstrByWidth(fieldName(first+i).getString(),w),x,30+i*29,0xFFCCD0DC,false);
  int pages=(KEYS.length+pageSize-1)/pageSize;
  if(pages>1)g.drawCenteredString(font,(page+1)+" / "+pages,width/2,height-50,0xFFCCD0DC);
  if(!error.isBlank())g.drawString(font,font.plainSubstrByWidth(error,w),x,height-68,0xFFFFAAAA,false);
  super.render(g,mx,my,dt);
 }
}
