package com.fixmer.mared.technology.scripts;

import com.fixmer.genesis.technology.scripts.GradleTasks;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.overlay.*;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.technology.editor.EditorDialogStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;
import java.util.function.Consumer;

/** Search and select a task from a captured project catalog. Selection never runs Gradle. */
public final class GradleTasksOverlay implements Overlay {
    private final String project;
    private final GradleTasks.Catalog catalog;
    private final int accent;
    private final Consumer<String> selected;
    private final boolean writable;
    private String filter="";
    private List<GradleTasks.Task> rows;
    private int index,scroll,x,y,w,h,listY,listBottom,visible=1,buttonY,buttonW;
    private boolean closeRequested,replaceFilter;
    public GradleTasksOverlay(String project,GradleTasks.Catalog catalog,int accent,Consumer<String> selected,boolean writable){this.project=Objects.requireNonNull(project);this.catalog=Objects.requireNonNull(catalog);this.accent=accent;this.selected=Objects.requireNonNull(selected);this.writable=writable;rows=catalog.tasks();}
    private static String tr(String key){return MaredLang.get("mared.scripts."+key);}
    @Override public OverlayLayer layer(){return OverlayLayer.MODAL;}
    @Override public boolean isInputBarrier(){return true;}
    @Override public boolean consumeCloseRequest(){boolean c=closeRequested;closeRequested=false;return c;}
    private void search(String value){filter=value.substring(0,Math.min(160,value.length()));rows=catalog.filter(filter);index=0;scroll=0;replaceFilter=false;}
    private void reveal(){index=Math.max(0,Math.min(index,Math.max(0,rows.size()-1)));if(index<scroll)scroll=index;if(index>=scroll+visible)scroll=index-visible+1;scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visible)));}
    private void accept(){if(writable&&!rows.isEmpty()){selected.accept(rows.get(index).path());closeRequested=true;}}
    @Override public void render(GuiGraphics g,Font font,int screenW,int screenH,int mx,int my){
        w=Math.min(680,Math.max(1,screenW-16));h=Math.min(390,Math.max(1,screenH-16));x=(screenW-w)/2;y=(screenH-h)/2;listY=y+58;buttonY=y+h-24;listBottom=Math.max(listY,buttonY-(h<220?18:42));visible=Math.max(1,(listBottom-listY)/22);reveal();
        var style=EditorDialogStyle.active(accent);g.fill(0,0,screenW,screenH,0x88000000);style.panel(g,x,y,w,h);
        g.drawString(font,TextUtils.ellipsize(font,tr("tasks_choose")+" · "+project,Math.max(1,w-16)),x+8,y+10,style.text(),false);
        g.fill(x+8,y+26,x+w-8,y+44,style.input());g.drawString(font,TextUtils.ellipsize(font,filter.isEmpty()?tr("tasks_filter"):filter,Math.max(1,w-24)),x+12,y+31,filter.isEmpty()?style.muted():style.text(),false);
        String count=rows.size()+" / "+catalog.tasks().size()+(catalog.limited()?" · "+tr("tasks_limited"):"");g.drawString(font,TextUtils.ellipsize(font,count,Math.max(1,w-16)),x+8,y+47,style.muted(),false);
        g.enableScissor(x+8,listY,x+w-8,listBottom);
        try{
            if(rows.isEmpty())g.drawString(font,TextUtils.ellipsize(font,tr("tasks_empty"),Math.max(1,w-20)),x+10,listY+4,style.muted(),false);
            for(int i=scroll;i<Math.min(rows.size(),scroll+visible);i++){int top=listY+(i-scroll)*22;var task=rows.get(i);if(i==index)g.fill(x+8,top,x+w-8,top+22,style.selection());g.drawString(font,TextUtils.ellipsize(font,task.path(),Math.max(1,w-24)),x+12,top+2,style.text(),false);g.drawString(font,TextUtils.ellipsize(font,task.description().isEmpty()?task.group():task.description(),Math.max(1,w-24)),x+12,top+12,style.muted(),false);}
        }finally{g.disableScissor();}
        if(rows.size()>visible&&listBottom>listY){int track=listBottom-listY,thumb=Math.max(4,track*visible/rows.size()),max=rows.size()-visible,top=listY+(track-thumb)*scroll/max;g.fill(x+w-6,top,x+w-4,top+thumb,style.muted());}
        String detail=rows.isEmpty()?tr("tasks_manual_hint"):rows.get(index).group()+" · "+rows.get(index).path();if(h>=220)g.drawString(font,TextUtils.ellipsize(font,detail,Math.max(1,w-16)),x+8,buttonY-32,style.muted(),false);
        g.drawString(font,TextUtils.ellipsize(font,tr("tasks_select_hint"),Math.max(1,w-16)),x+8,buttonY-(h<220?13:19),style.muted(),false);
        buttonW=Math.max(1,(w-22)/2);style.button(g,font,x+8,buttonY,buttonW,18,tr("report_close"),hit(mx,my,x+8),false,false);style.button(g,font,x+14+buttonW,buttonY,buttonW,18,tr(writable?"tasks_select":"tasks_readonly"),hit(mx,my,x+14+buttonW),writable&&!rows.isEmpty(),false);
    }
    private boolean hit(double mx,double my,int bx){return mx>=bx&&mx<bx+buttonW&&my>=buttonY&&my<buttonY+18;}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){if(hit(mx,my,x+8))closeRequested=true;else if(hit(mx,my,x+14+buttonW))accept();else if(mx>=x+8&&mx<x+w-8&&my>=listY&&my<listBottom){int row=scroll+((int)my-listY)/22;if(row<rows.size())index=row;}}return true;}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){if(!rows.isEmpty()){index+=dy<0?3:dy>0?-3:0;reveal();}return true;}
    @Override public boolean charTyped(char c,int modifiers){if(c>=32&&c!=127){if(replaceFilter)search("");if(filter.length()<160)search(filter+c);}return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256)closeRequested=true;else if(key==257||key==335)accept();
        else if((modifiers&2)!=0){if(key==65)replaceFilter=true;else if(key==86){String clip=Minecraft.getInstance().keyboardHandler.getClipboard();if(clip!=null){String cleaned=clip.chars().filter(c->c>=32&&c!=127).limit(160).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();search((replaceFilter?"":filter)+cleaned);}}else if(key==67&&!rows.isEmpty())Minecraft.getInstance().keyboardHandler.setClipboard(rows.get(index).path());}
        else if(key==259){if(replaceFilter)search("");else if(!filter.isEmpty())search(filter.substring(0,filter.length()-1));}
        else if(key==264||key==267){index+=key==267?visible:1;reveal();}else if(key==265||key==266){index-=key==266?visible:1;reveal();}else if(key==268){index=0;reveal();}else if(key==269){index=rows.size()-1;reveal();}return true;
    }
}
