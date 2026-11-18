package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.MaredLang;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** In-game preparation, review and explicit graceful handoff. No launcher JSON editing required. */
public final class ServerPanelScreen66 extends MaredScreen {
    private final Screen previous;private JsonObject prefs;private EditBox server,jar,port;
    private Button check,prepare,restart,cancel,side,mode,reports;private int left,top,contentWidth;
    public ServerPanelScreen66(Screen previous){super(Component.translatable("mared.server66.title"));this.previous=previous;prefs=ServerPanelService66.preferencesRead();}
    private static Component tr(String key){return Component.translatable("mared.server66."+key);}
    private String pref(String key,String fallback){try{return prefs.get(key).getAsString();}catch(RuntimeException bad){return fallback;}}
    @Override protected void init(){
        if(server!=null)collect();
        contentWidth=Math.max(240,Math.min(580,width-32));left=(width-contentWidth)/2;top=Math.max(26,(height-260)/2);
        server=addRenderableWidget(new EditBox(font,left,top+23,contentWidth,20,tr("server")));server.setMaxLength(4096);server.setValue(ManagedApplyPanel67.available()?java.nio.file.Path.of(System.getenv("GENESIS_FIRST58_ROOT"),"generations","server-"+System.getenv("GENESIS_FIRST58_GENERATION")).toString():pref("server",""));
        jar=addRenderableWidget(new EditBox(font,left,top+61,contentWidth,20,tr("jar")));jar.setMaxLength(4096);jar.setValue(pref("jar",""));
        int bw=(contentWidth-12)/3;
        side=addRenderableWidget(Button.builder(tr("side."+pref("side","BOTH")),b->{String current=pref("side","BOTH");prefs.addProperty("side",current.equals("BOTH")?"SERVER":current.equals("SERVER")?"CLIENT":"BOTH");b.setMessage(tr("side."+pref("side","BOTH")));}).bounds(left,top+90,bw,20).build());
        mode=addRenderableWidget(Button.builder(tr("mode."+pref("authMode","LOCAL_OFFLINE")),b->{prefs.addProperty("authMode",pref("authMode","LOCAL_OFFLINE").equals("ONLINE")?"LOCAL_OFFLINE":"ONLINE");b.setMessage(tr("mode."+pref("authMode","LOCAL_OFFLINE")));}).bounds(left+bw+6,top+90,bw,20).build());
        port=addRenderableWidget(new EditBox(font,left+2*(bw+6)+32,top+90,bw-32,20,tr("port")));port.setMaxLength(5);port.setValue(pref("port","27690"));
        int y=top+121;
        check=addRenderableWidget(Button.builder(tr("check"),b->start(true)).bounds(left,y,bw,20).build());
        prepare=addRenderableWidget(Button.builder(tr("prepare"),b->start(false)).bounds(left+bw+6,y,bw,20).build());
        restart=addRenderableWidget(Button.builder(tr("restart"),b->ServerPanelService66.command("ARM")).bounds(left+2*(bw+6),y,bw,20).build());
        int bottom=Math.min(height-26,top+239);
        cancel=addRenderableWidget(Button.builder(tr("cancel"),b->ServerPanelService66.command("CANCEL")).bounds(left,bottom,bw,20).build());
        reports=addRenderableWidget(Button.builder(tr("reports"),b->{var path=ServerPanelService66.reportDirectory();if(path!=null)net.minecraft.Util.getPlatform().openFile(path.toFile());}).bounds(left+bw+6,bottom,bw,20).build());
        addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(left+2*(bw+6),bottom,bw,20).build());
        refresh();
    }
    private void collect(){if(!ManagedApplyPanel67.available())prefs.addProperty("server",server.getValue().trim());prefs.addProperty("jar",jar.getValue().trim());try{prefs.addProperty("port",Integer.parseInt(port.getValue()));}catch(NumberFormatException bad){prefs.addProperty("port",0);}}
    private void start(boolean checkOnly){collect();ServerPanelService66.start(prefs,checkOnly);refresh();}
    private void refresh(){
        if(check==null)return;boolean busy=ServerPanelService66.busy();check.active=prepare.active=!busy;restart.active=busy&&ServerPanelService66.phase().equals("READY");cancel.active=busy&&!ServerPanelService66.phase().equals("STARTING")&&!ServerPanelService66.phase().equals("ARMED")&&!ServerPanelService66.phase().equals("RESTARTING");
        reports.active=ServerPanelService66.reportDirectory()!=null;
        boolean managed=ManagedApplyPanel67.available();
        server.setEditable(!busy&&!managed);jar.setEditable(!busy);port.setEditable(!busy&&!managed);side.active=!busy;mode.active=!busy&&!managed;
    }
    @Override public void tick(){refresh();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){if(server!=null){collect();ServerPanelService66.save(prefs);}}
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fillGradient(0,0,width,height,0xF511141B,0xF51A1D29);g.drawCenteredString(font,title,width/2,top-16,0xFFF0F1FC);
        g.drawString(font,tr("port_short"),left+2*((contentWidth-12)/3+6),top+96,0xFFBEC2D5,false);
        g.drawString(font,tr("server"),left,top+10,0xFFBEC2D5,false);g.drawString(font,tr("jar"),left,top+48,0xFFBEC2D5,false);
        g.enableScissor(left,top+148,left+contentWidth,Math.min(height-32,top+233));int y=top+151;
        String statusKey=ServerPanelService66.phase();if(ManagedApplyPanel67.available()&&java.util.Set.of("FAILED","CANCELLED").contains(statusKey))statusKey+="_MANAGED";
        String status=MaredLang.get("mared.server66.phase."+statusKey);
        for(var line:font.split(Component.literal(status),contentWidth)){g.drawString(font,line,left,y,0xFFB8A5F2,false);y+=font.lineHeight+2;}
        JsonObject state=ServerPanelService66.state();
        if(state.has("failureReason"))for(var line:font.split(Component.literal(state.get("failureReason").getAsString()),contentWidth)){g.drawString(font,line,left,y,0xFFE8A7A7,false);y+=font.lineHeight+2;}
        try{JsonObject changes=state.getAsJsonObject("delta");if(changes!=null)for(String role:new String[]{"server","client"})if(changes.has(role)){
            JsonObject d=changes.getAsJsonObject(role);Component info=Component.translatable("mared.server66.delta",MaredLang.get("mared.server66."+role),d.getAsJsonArray("added").size(),d.getAsJsonArray("changed").size(),d.getAsJsonArray("removed").size());
            for(var line:font.split(info,contentWidth)){g.drawString(font,line,left,y,0xFFCCD0DC,false);y+=font.lineHeight+2;}
        }}catch(RuntimeException bad){}
        for(var line:font.split(tr(ManagedApplyPanel67.available()?"managed_hint":"hint"),contentWidth)){g.drawString(font,line,left,y+3,0xFF858C9F,false);y+=font.lineHeight+2;}
        g.disableScissor();super.render(g,mx,my,delta);
    }
}
