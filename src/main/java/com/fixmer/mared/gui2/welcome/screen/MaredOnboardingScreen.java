package com.fixmer.mared.gui2.welcome.screen;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.welcome.MaredWelcomeStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Localised introduction. Planned categories are described as planned. */
public final class MaredOnboardingScreen extends MaredScreen {
    private static final int PAGES=8;
    private static final int[] ACCENTS={0xFFAAAAFF,0xFF55FFAA,0xFF55AAFF,0xFFAA55FF,0xFFFFAA55,0xFFFFFF55,0xFFFF5577,0xFFAAAAFF};
    private int index,scroll,maxScroll,focused=2,panelX,panelY,panelW,panelH,buttonW,buttonY;
    public MaredOnboardingScreen(){super(Component.literal("Genesis"));}
    private String tr(String key){return MaredLang.get("mared.onboarding."+key);}
    private void page(int value){index=Math.max(0,Math.min(PAGES-1,value));scroll=0;focused=2;}
    private void finish(){MaredWelcomeStorage.markSeen();WelcomeScreenManager.openGenesis();}
    private void action(int button){if(button==0&&index>0)page(index-1);else if(button==1)finish();else if(button==2){if(index==PAGES-1)finish();else page(index+1);}}
    private int buttonX(int button){return panelX+8+button*(buttonW+6);}
    private boolean buttonVisible(int button){return button==2||button==0&&index>0||button==1&&index<PAGES-1;}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        Font f=Minecraft.getInstance().font;
        panelW=Math.max(1,Math.min(650,width-16));panelH=Math.max(1,Math.min(380,height-16));panelX=(width-panelW)/2;panelY=(height-panelH)/2;
        buttonW=Math.max(1,(panelW-28)/3);buttonY=panelY+panelH-28;
        g.fill(0,0,width,height,0xFF0A0A12);g.fill(panelX,panelY,panelX+panelW,panelY+panelH,0xFF14141E);g.fill(panelX,panelY,panelX+panelW,panelY+2,ACCENTS[index]);
        int textW=Math.max(1,panelW-24);
        g.drawString(f,TextUtils.ellipsize(f,tr("page."+index+".title"),Math.max(1,textW-36)),panelX+12,panelY+12,0xFFE8E8F0,false);
        String counter=(index+1)+" / "+PAGES;g.drawString(f,counter,panelX+panelW-12-f.width(counter),panelY+12,0xFF9090A0,false);
        int top=panelY+32,bottom=Math.max(top,buttonY-20);var lines=new ArrayList<String>();
        lines.addAll(TextUtils.wrapLinesCached(f,tr("page."+index+".subtitle"),textW));lines.add("");
        for(int i=0;i<4;i++){lines.addAll(TextUtils.wrapLinesCached(f,"• "+tr("page."+index+".item."+i),textW));lines.add("");}
        int visible=Math.max(0,(bottom-top)/12);maxScroll=Math.max(0,lines.size()-visible);scroll=Math.max(0,Math.min(scroll,maxScroll));
        g.enableScissor(panelX+8,top,panelX+panelW-8,bottom);
        try{for(int i=scroll;i<Math.min(lines.size(),scroll+visible);i++)g.drawString(f,lines.get(i),panelX+12,top+(i-scroll)*12,0xFFE8E8F0,false);}finally{g.disableScissor();}
        if(maxScroll>0&&bottom>top){int track=bottom-top,thumb=Math.max(4,track*visible/lines.size());int sy=top+(track-thumb)*scroll/maxScroll;g.fill(panelX+panelW-5,sy,panelX+panelW-3,sy+thumb,0xFF9090A0);}
        g.drawString(f,TextUtils.ellipsize(f,tr("keys"),textW),panelX+12,buttonY-13,0xFF9090A0,false);
        for(int i=0;i<3;i++)if(buttonVisible(i)){
            int bx=buttonX(i);boolean hover=mx>=bx&&mx<bx+buttonW&&my>=buttonY&&my<buttonY+20;
            g.fill(bx,buttonY,bx+buttonW,buttonY+20,hover||focused==i?0xFF303044:0xFF202030);
            if(focused==i)g.fill(bx,buttonY+19,bx+buttonW,buttonY+20,ACCENTS[index]);
            String label=tr(i==0?"back":i==1?"skip":index==PAGES-1?"enter":"next");
            g.drawString(f,TextUtils.ellipsize(f,label,Math.max(1,buttonW-8)),bx+4,buttonY+6,0xFFE8E8F0,false);
        }
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0)for(int i=0;i<3;i++)if(buttonVisible(i)&&mx>=buttonX(i)&&mx<buttonX(i)+buttonW&&my>=buttonY&&my<buttonY+20){action(i);return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(dy<0?3:dy>0?-3:0)));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==256){finish();return true;}
        if(key==258){do{focused=Math.floorMod(focused+((mods&1)!=0?-1:1),3);}while(!buttonVisible(focused));return true;}
        if(key==257||key==335||key==32){action(focused);return true;}
        if(key==262){page(index+1);return true;}if(key==263){page(index-1);return true;}
        if(key==264||key==267){scroll=Math.min(maxScroll,scroll+(key==267?8:1));return true;}
        if(key==265||key==266){scroll=Math.max(0,scroll-(key==266?8:1));return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean isPauseScreen(){return false;}
}
