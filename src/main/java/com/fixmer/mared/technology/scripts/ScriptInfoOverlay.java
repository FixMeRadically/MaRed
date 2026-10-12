package com.fixmer.mared.technology.scripts;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.overlay.*;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.technology.editor.EditorDialogStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Read-only, scrollable report; copying does not run code or modify project files. */
public final class ScriptInfoOverlay implements Overlay {
    private final String title,body;
    private final int accent;
    private List<String> lines=List.of();
    private Font cachedFont;
    private int cachedWidth=-1,scroll,maxScroll,x,y,w,h,copyX,closeX,buttonY,buttonW;
    private boolean closeRequested;
    public ScriptInfoOverlay(String title,String body,int accent){this.title=Objects.requireNonNull(title);this.body=Objects.requireNonNull(body);this.accent=accent;}
    @Override public OverlayLayer layer(){return OverlayLayer.MODAL;}
    @Override public boolean isInputBarrier(){return true;}
    @Override public boolean consumeCloseRequest(){boolean value=closeRequested;closeRequested=false;return value;}
    private void wrap(Font font,int width){
        if(cachedFont==font&&cachedWidth==width)return;cachedFont=font;cachedWidth=width;var wrapped=new ArrayList<String>();
        for(String paragraph:body.replace("\r","").split("\n",-1)){
            if(paragraph.isEmpty()){wrapped.add("");continue;}
            int offset=0;while(offset<paragraph.length()){
                String part=paragraph.substring(offset,Math.min(paragraph.length(),offset+1024));
                String line=font.plainSubstrByWidth(part,width);if(line.isEmpty())line=part.substring(0,1);
                wrapped.add(line);offset+=line.length();
            }
        }lines=List.copyOf(wrapped);
    }
    @Override public void render(GuiGraphics g,Font font,int screenW,int screenH,int mouseX,int mouseY){
        w=Math.min(600,Math.max(1,screenW-16));h=Math.min(340,Math.max(1,screenH-16));x=(screenW-w)/2;y=(screenH-h)/2;
        int padding=8,contentY=y+26,contentBottom=Math.max(contentY,y+h-32),visible=Math.max(1,(contentBottom-contentY)/12);
        wrap(font,Math.max(1,w-padding*2-6));maxScroll=Math.max(0,lines.size()-visible);scroll=Math.max(0,Math.min(scroll,maxScroll));
        var style=EditorDialogStyle.from(MaredThemeRegistry.active(),accent);g.fill(0,0,screenW,screenH,0x88000000);style.panel(g,x,y,w,h);
        g.drawString(font,TextUtils.ellipsize(font,title,Math.max(1,w-padding*2)),x+padding,y+13,style.text(),false);
        g.enableScissor(x+padding,contentY,x+w-padding,contentBottom);
        try{for(int i=scroll;i<Math.min(lines.size(),scroll+visible);i++)g.drawString(font,lines.get(i),x+padding,contentY+(i-scroll)*12,style.text(),false);}finally{g.disableScissor();}
        if(maxScroll>0&&contentBottom>contentY){int track=contentBottom-contentY,thumb=Math.max(4,track*visible/lines.size()),top=contentY+(track-thumb)*scroll/maxScroll;g.fill(x+w-padding-3,top,x+w-padding-1,top+thumb,style.muted());}
        buttonW=Math.max(1,(w-padding*2-6)/2);copyX=x+padding;closeX=copyX+buttonW+6;buttonY=y+h-24;
        style.button(g,font,copyX,buttonY,buttonW,18,MaredLang.get("mared.scripts.report_copy"),hit(mouseX,mouseY,copyX),false,false);
        style.button(g,font,closeX,buttonY,buttonW,18,MaredLang.get("mared.scripts.report_close"),hit(mouseX,mouseY,closeX),true,false);
    }
    private boolean hit(double mx,double my,int bx){return mx>=bx&&mx<bx+buttonW&&my>=buttonY&&my<buttonY+18;}
    private void copy(){Minecraft.getInstance().keyboardHandler.setClipboard(body);}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){if(hit(mx,my,copyX))copy();else if(hit(mx,my,closeX))closeRequested=true;}return true;}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll,scroll+(dy<0?3:dy>0?-3:0)));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256||key==257||key==335)closeRequested=true;
        else if(key==67&&(modifiers&2)!=0)copy();
        else if(key==264||key==267)scroll=Math.min(maxScroll,scroll+(key==267?12:1));
        else if(key==265||key==266)scroll=Math.max(0,scroll-(key==266?12:1));
        else if(key==268)scroll=0;else if(key==269)scroll=maxScroll;
        return true;
    }
}
