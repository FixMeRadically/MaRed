package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.MaredLang;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** In-game preparation, review and graceful handoff. All long directory scans are asynchronous. */
public final class ServerPanelScreen66 extends MaredScreen {
    private final Screen previous;private JsonObject prefs;private EditBox server,jar,port;
    private Button check,prepare,restart,cancel,side,mode,reports,serverBrowse,jarBrowse,details;
    private int left,top,contentWidth,statusTop,statusBottom,scroll,maxScroll;private String lastStatus="",dropError="";
    public ServerPanelScreen66(Screen previous){super(Component.translatable("mared.server66.title"));this.previous=previous;prefs=ServerPanelService66.preferencesRead();}
    private static Component tr(String key){return Component.translatable("mared.server66."+key);}
    private String pref(String key,String fallback){try{return prefs.get(key).getAsString();}catch(RuntimeException bad){return fallback;}}
    private Button button(String key,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        if(server!=null)collect();contentWidth=Math.max(240,Math.min(620,width-24));left=(width-contentWidth)/2;top=29;
        server=addRenderableWidget(new EditBox(font,left,top+13,contentWidth-63,20,tr("server")));server.setMaxLength(4096);server.setValue(ManagedApplyPanel67.available()?Path.of(System.getenv("GENESIS_FIRST58_ROOT"),"generations","server-"+System.getenv("GENESIS_FIRST58_GENERATION")).toString():pref("server",""));
        serverBrowse=button("browse",left+contentWidth-58,top+13,58,()->browse(true));
        jar=addRenderableWidget(new EditBox(font,left,top+49,contentWidth-63,20,tr("jar")));jar.setMaxLength(4096);jar.setValue(pref("jar",""));
        jarBrowse=button("browse",left+contentWidth-58,top+49,58,()->browse(false));
        int bw=(contentWidth-12)/3;
        side=button("side."+pref("side","BOTH"),left,top+76,bw,()->{String current=pref("side","BOTH");prefs.addProperty("side",current.equals("BOTH")?"SERVER":current.equals("SERVER")?"CLIENT":"BOTH");side.setMessage(tr("side."+pref("side","BOTH")));});
        mode=button("mode."+pref("authMode","LOCAL_OFFLINE"),left+bw+6,top+76,bw,()->{prefs.addProperty("authMode",pref("authMode","LOCAL_OFFLINE").equals("ONLINE")?"LOCAL_OFFLINE":"ONLINE");mode.setMessage(tr("mode."+pref("authMode","LOCAL_OFFLINE")));});
        port=addRenderableWidget(new EditBox(font,left+2*(bw+6)+32,top+76,bw-32,20,tr("port")));port.setMaxLength(5);port.setValue(pref("port","27690"));
        int y=top+103;check=button("check",left,y,bw,()->start(true));prepare=button("prepare",left+bw+6,y,bw,()->start(false));restart=button(ManagedApplyPanel67.available()?"apply_short":"enter_short",left+2*(bw+6),y,bw,()->ServerPanelService66.command("ARM"));
        int bottom=height-27,fw=(contentWidth-15)/4;
        cancel=button("cancel",left,bottom,fw,()->ServerPanelService66.command("CANCEL"));
        details=button("details",left+fw+5,bottom,fw,()->minecraft.setScreen(new ServerChangesScreen68(this)));
        reports=button("reports",left+2*(fw+5),bottom,fw,()->{var path=ServerPanelService66.reportDirectory();if(path!=null)net.minecraft.Util.getPlatform().openFile(path.toFile());});
        button("back",left+3*(fw+5),bottom,fw,this::onClose);
        button("dedicated75",left,top+130,(contentWidth-5)/2,()->minecraft.setScreen(new DedicatedPanelScreen75(this)));
        button("bootstrap76",left+(contentWidth+5)/2,top+130,(contentWidth-5)/2,()->minecraft.setScreen(new BootstrapScreen76(this,RemoteService69.game(),RemoteService69.endpoint(),null,false)));
        addRenderableWidget(Button.builder(Component.translatable("mared.history97.open"),b->minecraft.setScreen(new com.fixmer.mared.dev.client.RestartHistoryScreen97(this))).bounds(12,7,120,20).build());
        button("remote",width-78,7,66,()->minecraft.setScreen(new RemoteScreen69(this)));
        statusTop=top+156;statusBottom=bottom-9;refresh();
    }
    private void collect(){if(!ManagedApplyPanel67.available())prefs.addProperty("server",server.getValue().trim());prefs.addProperty("jar",jar.getValue().trim());try{prefs.addProperty("port",Integer.parseInt(port.getValue()));}catch(NumberFormatException bad){prefs.addProperty("port",0);}}
    private void browse(boolean folder){
        if(ServerPanelService66.busy()||folder&&ManagedApplyPanel67.available())return;collect();Path initial;
        try{String value=folder?server.getValue():jar.getValue();initial=value.isBlank()?minecraft.gameDirectory.toPath():Path.of(value);}catch(RuntimeException bad){initial=minecraft.gameDirectory.toPath();}
        minecraft.setScreen(new LocalPickerScreen68(this,folder,initial,path->{if(folder){server.setValue(path.toString());prefs.addProperty("server",path.toString());}else{jar.setValue(path.toString());prefs.addProperty("jar",path.toString());}dropError="";}));
    }
    private void start(boolean checkOnly){dropError="";collect();scroll=0;ServerPanelService66.start(prefs,checkOnly);refresh();}
    private void refresh(){
        if(check==null)return;boolean busy=ServerPanelService66.busy(),managed=ManagedApplyPanel67.available();String phase=ServerPanelService66.phase();
        check.active=prepare.active=!busy;restart.active=busy&&phase.equals("READY");cancel.active=busy&&!Set.of("STARTING","ARMED","RESTARTING","CANCELLING").contains(phase);
        reports.active=ServerPanelService66.reportDirectory()!=null;JsonObject state=ServerPanelService66.state();details.active=state.has("delta");
        server.setEditable(!busy&&!managed);jar.setEditable(!busy);port.setEditable(!busy&&!managed);side.active=!busy;mode.active=!busy&&!managed;serverBrowse.active=!busy&&!managed;jarBrowse.active=!busy;
        if(!lastStatus.equals(phase)){scroll=0;lastStatus=phase;}
    }
    private List<Component> content(){
        List<Component> result=new ArrayList<>();String phase=ServerPanelService66.phase();if(ManagedApplyPanel67.available()&&Set.of("FAILED","CANCELLED","READY").contains(phase))phase+="_MANAGED";
        result.add(Component.literal(MaredLang.get("mared.server66.phase."+phase)));JsonObject state=ServerPanelService66.state();
        if(!dropError.isEmpty())result.add(tr("error."+dropError));
        if(state.has("failureKey"))result.add(tr("error."+state.get("failureKey").getAsString()));
        if(state.has("failureReason")&&!state.get("failureReason").getAsString().isBlank())result.add(Component.literal(state.get("failureReason").getAsString()));
        try{JsonObject changes=state.getAsJsonObject("delta");if(changes!=null)for(String role:new String[]{"server","client"})if(changes.has(role)){
            JsonObject d=changes.getAsJsonObject(role);result.add(Component.translatable("mared.server66.delta",MaredLang.get("mared.server66."+role),d.getAsJsonArray("added").size(),d.getAsJsonArray("changed").size(),d.getAsJsonArray("removed").size()));
        }}catch(RuntimeException bad){}
        result.add(tr(ManagedApplyPanel67.available()?"managed_hint":"hint"));return result;
    }
    @Override public void tick(){refresh();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){if(server!=null){collect();ServerPanelService66.save(prefs);}}
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(x>=left&&x<=left+contentWidth&&y>=statusTop&&y<statusBottom){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*24)));return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){if((key==266||key==267)&&!server.isFocused()&&!jar.isFocused()&&!port.isFocused()){scroll=Math.max(0,Math.min(maxScroll,scroll+(key==266?-48:48)));return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onFilesDrop(List<Path> paths){
        if(ServerPanelService66.busy()){dropError="busy_drop";return;}if(paths.size()!=1){dropError="one_drop";return;}
        try{Path accepted=com.fixmer.genesis.technology.server.firstlaunch.LocalFileBrowser68.select(paths.get(0),false);jar.setValue(accepted.toString());dropError="";collect();ServerPanelService66.save(prefs);}catch(Exception bad){dropError="drop_jar";}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fillGradient(0,0,width,height,0xFF11141B,0xFF1A1D29);g.drawCenteredString(font,title,width/2,12,0xFFF0F1FC);
        g.drawString(font,tr("port_short"),left+2*((contentWidth-12)/3+6),top+82,0xFFBEC2D5,false);
        g.drawString(font,tr("server"),left,top+1,0xFFBEC2D5,false);g.drawString(font,tr("jar"),left,top+37,0xFFBEC2D5,false);
        List<net.minecraft.util.FormattedCharSequence> wrapped=new ArrayList<>();for(Component line:content())wrapped.addAll(font.split(line,contentWidth-10));int row=font.lineHeight+3;
        maxScroll=Math.max(0,wrapped.size()*row-(statusBottom-statusTop));scroll=Math.min(maxScroll,scroll);
        g.enableScissor(left,statusTop,left+contentWidth,statusBottom);int y=statusTop-scroll;
        for(var line:wrapped){if(y+font.lineHeight>=statusTop&&y<statusBottom)g.drawString(font,line,left,y,0xFFCCD0DC,false);y+=row;}g.disableScissor();
        if(maxScroll>0){int h=Math.max(8,(statusBottom-statusTop)*(statusBottom-statusTop)/(maxScroll+statusBottom-statusTop));int sy=statusTop+(statusBottom-statusTop-h)*scroll/maxScroll;g.fill(left+contentWidth-3,sy,left+contentWidth,sy+h,0xFFB8A5F2);}
        super.render(g,mx,my,delta);
    }
}
