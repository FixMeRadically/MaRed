package com.fixmer.mared.technology.server.setup;

import com.google.gson.*;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A bounded snapshot of a candidate. A stale review never authorizes another candidate. */
public final class ServerChangesScreen68 extends MaredScreen {
    private final Screen previous;private final JsonObject snapshot;private final String token;private final List<Component> lines=new ArrayList<>();
    private int scroll,maxScroll,left,bodyWidth;private Button apply;
    public ServerChangesScreen68(Screen previous){super(tr("title"));this.previous=previous;snapshot=ServerPanelService66.state();token=ServerPanelService66.reviewToken();}
    private static Component tr(String key,Object... args){return Component.translatable("mared.changes68."+key,args);}
    @Override protected void init(){
        bodyWidth=Math.max(200,Math.min(660,width-32));left=(width-bodyWidth)/2;lines.clear();
        try{
            JsonObject delta=snapshot.getAsJsonObject("delta");
            if(delta!=null)for(String role:List.of("server","client"))if(delta.has(role)){
                lines.add(tr(role));JsonObject group=delta.getAsJsonObject(role);boolean any=false;
                for(String category:List.of("added","changed","removed")){JsonArray files=group.getAsJsonArray(category);if(files==null||files.size()==0)continue;any=true;lines.add(tr(category,files.size()));
                    for(int i=0;i<Math.min(256,files.size());i++){String name=files.get(i).getAsString();lines.add(Component.literal("  "+name));}
                    if(files.size()>256)lines.add(tr("limit"));
                }
                if(!any)lines.add(tr("unchanged"));lines.add(Component.empty());
            }
        }catch(RuntimeException bad){lines.clear();lines.add(tr("invalid"));}
        if(lines.isEmpty())lines.add(tr("empty"));
        apply=addRenderableWidget(Button.builder(tr("apply"),b->{if(current()){ServerPanelService66.command("ARM");minecraft.setScreen(previous);}}).bounds(left,height-28,(bodyWidth-6)/2,20).build());
        addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(left+(bodyWidth+6)/2,height-28,(bodyWidth-6)/2,20).build());clamp();tick();
    }
    private boolean current(){return !token.isEmpty()&&token.equals(ServerPanelService66.reviewToken())&&ServerPanelService66.busy();}
    @Override public void tick(){if(apply!=null)apply.active=current();}
    private int contentHeight(){int height=0;for(Component line:lines)height+=Math.max(1,font.split(line,bodyWidth-8).size())*(font.lineHeight+3);return height;}
    private void clamp(){maxScroll=Math.max(0,contentHeight()-Math.max(20,height-105));scroll=Math.max(0,Math.min(maxScroll,scroll));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll+=(int)(-dy*30);clamp();return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==266||key==267){scroll+=key==266?-80:80;clamp();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fillGradient(0,0,width,height,0xFF11141B,0xFF1A1D29);g.drawCenteredString(font,title,width/2,12,0xFFF0F1FC);
        g.drawString(font,tr(token.isEmpty()?"read_only":current()?"candidate":"stale"),left,29,0xFFB8A5F2,false);
        g.enableScissor(left,46,left+bodyWidth,height-48);int y=48-scroll;
        for(Component line:lines)for(var wrapped:font.split(line,bodyWidth-8)){if(y+font.lineHeight>=46&&y<height-48)g.drawString(font,wrapped,left+2,y,0xFFCCD0DC,false);y+=font.lineHeight+3;}
        g.disableScissor();if(maxScroll>0){int h=Math.max(12,(height-96)*(height-96)/(maxScroll+height-96));int sy=46+(height-96-h)*scroll/maxScroll;g.fill(left+bodyWidth-3,sy,left+bodyWidth,sy+h,0xFFB8A5F2);}
        super.render(g,mx,my,delta);
    }
}
