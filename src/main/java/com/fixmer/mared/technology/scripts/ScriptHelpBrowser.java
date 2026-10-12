package com.fixmer.mared.technology.scripts;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import java.util.*;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Offline Help with independent navigation/search, copyable examples and illustrative steps. */
public final class ScriptHelpBrowser extends MaredComponent {
    private record Hit(MaredBounds bounds,Runnable action) {}
    private record Line(int y,String text,boolean heading,boolean code) {}
    private record Action(int y,String label,Runnable action) {}
    private record Diagram(int y,int height,String caption,String kind) {}
    private ScriptHelpManual manual;
    private String language="",selected="start",query="",group="";
    private List<ScriptHelpManual.Article> results=List.of();
    private final Deque<String> back=new ArrayDeque<>(),forward=new ArrayDeque<>();
    private final Set<String> bookmarks=new LinkedHashSet<>();
    private final Map<String,Integer> scrolls=new HashMap<>();
    private boolean searchFocus,indexOpen,bookmarksOnly,selectQuery;
    private int scroll,indexScroll,maxScroll,bodyY,articleX,articleW,sidebarW,contentHeight,tutorialStep;
    private int cachedWidth=-1;
    private Font cachedFont;
    private String cachedArticle="";
    private List<Line> lines=List.of();private List<Action> actions=List.of();private List<Diagram> diagrams=List.of();
    private List<Hit> hits=List.of();
    private static String tr(String key){return MaredLang.get("mared.scripts.help_ui."+key);}
    private void ensure(){String code=MaredLang.getCurrentCode();if(manual!=null&&language.equals(code))return;language=code;manual=ScriptHelpManual.load(code);selected=manual.article(selected).id();if(manual.articles().stream().noneMatch(a->a.group().equals(group)))group="";cachedWidth=-1;filter();}
    public ScriptHelpManual.Article article(){ensure();return manual.article(selected);}
    public void openArticle(String id){ensure();if(selected.equals(id)){navigate(id);return;}back.addLast(selected);if(back.size()>64)back.removeFirst();forward.clear();navigate(id);}
    private void navigate(String id){scrolls.put(selected,scroll);selected=manual.article(id).id();scroll=scrolls.getOrDefault(selected,0);cachedWidth=-1;indexOpen=false;searchFocus=false;hits=List.of();}
    private void filter(){results=manual.search(query).stream().filter(a->(group.isEmpty()||a.group().equals(group))&&(!bookmarksOnly||bookmarks.contains(a.id()))).toList();indexScroll=0;hits=List.of();}
    private void search(String text){query=text.substring(0,Math.min(160,text.length()));selectQuery=false;if(bounds.width()<430)indexOpen=true;filter();}
    private void copy(String text){Minecraft.getInstance().keyboardHandler.setClipboard(text);}
    @Override public boolean focusable(){return true;}
    @Override public void onFocusLost(){searchFocus=false;}
    private void button(MaredRenderContext ctx,List<Hit> into,int x,int y,int w,String label,Runnable action){if(w<=0)return;ctx.graphics().fill(x,y,x+w,y+17,raised());ctx.graphics().drawString(ctx.font(),ctx.font().plainSubstrByWidth(label,Math.max(1,w-6)),x+3,y+4,text(),false);into.add(new Hit(new MaredBounds(x,y,w,17),action));}
    private void layoutText(Font font,int width){
        if(cachedFont==font&&cachedWidth==width&&cachedArticle.equals(selected))return;cachedFont=font;cachedWidth=width;cachedArticle=selected;var ls=new ArrayList<Line>();var as=new ArrayList<Action>();var ds=new ArrayList<Diagram>();int y=0;
        var a=article();y=wrap(ls,font,a.title(),width,y,true,false);y=wrap(ls,font,a.summary(),width,y,false,false)+10;
        for(var b:a.blocks())switch(b.kind()){
            case HEADING -> y=wrap(ls,font,b.text(),width,y+8,true,false)+4;
            case TEXT -> y=wrap(ls,font,b.text(),width,y,false,false)+8;
            case CODE -> {as.add(new Action(y,tr("copy_code"),()->copy(b.text())));y+=21;y=wrap(ls,font,b.text(),width-8,y,false,true)+8;}
            case LINK -> {as.add(new Action(y,b.text(),()->openArticle(b.target())));y+=23;}
            case DIAGRAM -> {int h=b.target().equals("tutorial")?130:Math.max(64,b.text().split(" → ").length*28+12);ds.add(new Diagram(y,h,b.text(),b.target()));y+=h+8;}
        }
        lines=List.copyOf(ls);actions=List.copyOf(as);diagrams=List.copyOf(ds);contentHeight=y;
    }
    private static int wrap(List<Line> out,Font f,String text,int width,int y,boolean heading,boolean code){for(String paragraph:text.split("\n",-1)){if(paragraph.isEmpty()){y+=12;continue;}int at=0;while(at<paragraph.length()){String part=f.plainSubstrByWidth(paragraph.substring(at),Math.max(1,width));if(part.isEmpty())part=paragraph.substring(at,at+1);if(!code&&at+part.length()<paragraph.length()){int space=part.lastIndexOf(' ');if(space>0)part=part.substring(0,space+1);}out.add(new Line(y,part,heading,code));y+=12;at+=part.length();}}return y;}
    @Override protected void safeRender(MaredRenderContext ctx){
        ensure();var g=ctx.graphics();var f=ctx.font();var hs=new ArrayList<Hit>();int x=bounds.x()+5,y=bounds.y()+4,w=Math.max(1,bounds.width()-10);
        g.fill(x,y,x+w,y+17,raised());g.drawString(f,f.plainSubstrByWidth(query.isEmpty()?tr("search"):query,Math.max(1,w-8)),x+4,y+4,query.isEmpty()?dim():selectQuery?accent():text(),false);hs.add(new Hit(new MaredBounds(x,y,w,17),()->{searchFocus=true;selectQuery=false;if(bounds.width()<430)indexOpen=true;requestFocus();}));
        int navY=y+21,bw=Math.max(1,Math.min(90,(w-12)/4));
        button(ctx,hs,x,navY,bw,tr("back"),()->{if(!back.isEmpty()){forward.addLast(selected);navigate(back.removeLast());}});button(ctx,hs,x+bw+3,navY,bw,tr("next"),()->{if(!forward.isEmpty()){back.addLast(selected);navigate(forward.removeLast());}});
        button(ctx,hs,x+(bw+3)*2,navY,bw,tr("contents"),()->{if(sidebarW>0){group="";bookmarksOnly=false;search("");}else indexOpen=!indexOpen;searchFocus=false;hits=List.of();});button(ctx,hs,x+(bw+3)*3,navY,bw,tr("copy_page"),()->copy(article().copyText()));
        bodyY=navY+22;sidebarW=bounds.width()>=430?Math.min(170,bounds.width()/3):0;boolean index=sidebarW>0||indexOpen;
        if(index){int iw=sidebarW>0?sidebarW:bounds.width()-10;button(ctx,hs,x,bodyY,Math.max(1,iw/2-2),tr(bookmarksOnly?"all":"bookmarks"),()->{bookmarksOnly=!bookmarksOnly;filter();});button(ctx,hs,x+iw/2,bodyY,Math.max(1,iw/2-2),group.isEmpty()?tr("groups"):group,()->{var groups=manual.articles().stream().map(ScriptHelpManual.Article::group).distinct().toList();int at=groups.indexOf(group)+1;group=at>=groups.size()?"":groups.get(at);filter();});
            int iy=bodyY+23,visible=Math.max(1,(bounds.bottom()-iy)/25);indexScroll=Math.max(0,Math.min(indexScroll,Math.max(0,results.size()-visible)));g.enableScissor(x,iy,Math.max(x,x+iw),Math.max(iy,bounds.bottom()));
            try{if(results.isEmpty())g.drawString(f,tr("empty"),x+3,iy+4,dim(),false);for(int i=indexScroll;i<Math.min(results.size(),indexScroll+visible);i++){var a=results.get(i);int ry=iy+(i-indexScroll)*25;if(a.id().equals(selected))g.fill(x,ry,x+iw,ry+24,raised());g.drawString(f,f.plainSubstrByWidth((bookmarks.contains(a.id())?"* ":"")+a.title(),Math.max(1,iw-7)),x+3,ry+3,a.id().equals(selected)?accent():text(),false);g.drawString(f,f.plainSubstrByWidth(a.group(),Math.max(1,iw-7)),x+3,ry+14,dim(),false);hs.add(new Hit(new MaredBounds(x,ry,iw,24),()->openArticle(a.id())));}}finally{g.disableScissor();}
        }
        if(sidebarW==0&&indexOpen){hits=List.copyOf(hs);return;}
        articleX=x+(sidebarW>0?sidebarW+8:0);articleW=Math.max(1,bounds.right()-articleX-5);int starW=Math.min(90,articleW);button(ctx,hs,articleX,bodyY,starW,tr(bookmarks.contains(selected)?"unbookmark":"bookmark"),()->{if(!bookmarks.remove(selected)&&bookmarks.size()<64)bookmarks.add(selected);filter();});int top=bodyY+23;
        layoutText(f,Math.max(1,articleW-6));maxScroll=Math.max(0,contentHeight-Math.max(0,bounds.bottom()-top-4));scroll=Math.max(0,Math.min(scroll,maxScroll));
        g.enableScissor(articleX,top,Math.max(articleX,articleX+articleW),Math.max(top,bounds.bottom()));
        try{for(var line:lines){int ly=top+line.y-scroll;if(ly<top-12||ly>=bounds.bottom())continue;if(line.code)g.fill(articleX,ly,articleX+articleW,ly+12,panel());g.drawString(f,line.text,articleX+(line.code?4:0),ly,line.heading?accent():text(),false);}
            int contentHitStart=hs.size();
            for(var a:actions){int ay=top+a.y-scroll;if(ay>=top-17&&ay<bounds.bottom())button(ctx,hs,articleX,ay,articleW,a.label,a.action);}
            for(var d:diagrams){int dy=top+d.y-scroll;if(dy+d.height>=top&&dy<bounds.bottom())diagram(ctx,hs,d,dy);}
            for(int i=hs.size()-1;i>=contentHitStart;i--){var hit=hs.get(i);var b=hit.bounds;int cy=Math.max(top,b.y()),bottom=Math.min(bounds.bottom(),b.bottom());if(bottom<=cy)hs.remove(i);else hs.set(i,new Hit(new MaredBounds(b.x(),cy,b.width(),bottom-cy),hit.action));}
        }finally{g.disableScissor();}hits=List.copyOf(hs);
    }
    private void diagram(MaredRenderContext ctx,List<Hit> hs,Diagram d,int y){var g=ctx.graphics();var f=ctx.font();int x=articleX,w=articleW;g.fill(x,y,x+w,y+d.height,raised());
        if(d.kind.equals("tutorial")){g.drawString(f,f.plainSubstrByWidth(tr("tutorial_label")+" · "+(tutorialStep+1)+" / 5",Math.max(1,w-8)),x+4,y+5,accent(),false);button(ctx,hs,x+4,y+21,Math.max(1,(w-12)/2),tr("back"),()->tutorialStep=Math.max(0,tutorialStep-1));button(ctx,hs,x+w/2+2,y+21,Math.max(1,(w-12)/2),tr("next"),()->tutorialStep=Math.min(4,tutorialStep+1));g.drawString(f,f.plainSubstrByWidth(tr("step."+tutorialStep),Math.max(1,w-8)),x+4,y+45,text(),false);
            String[] frames={"src/Main.java","System.out.println(\"Hello, Genesis\");",tr("frame.2"),tr("frame.3"),"Hello, Genesis"};g.fill(x+4,y+60,x+w-4,y+90,panel());illustrate(ctx,x+4,y+60,w-8,frames[tutorialStep]);g.drawString(f,f.plainSubstrByWidth(tr("illustration"),Math.max(1,w-8)),x+4,y+105,dim(),false);
        }else{String[] nodes=d.caption.split(" → ");for(int i=0;i<nodes.length;i++){int ny=y+6+i*28;if(i>0)g.fill(x+w/2,ny-6,x+w/2+1,ny,accent());g.fill(x+6,ny,x+w-6,ny+22,panel());g.fill(x+6,ny,x+8,ny+22,accent());g.drawString(f,f.plainSubstrByWidth((i+1)+". "+nodes[i],Math.max(1,w-20)),x+12,ny+7,text(),false);}}
    }
    private void illustrate(MaredRenderContext ctx,int x,int y,int w,String label){
        var g=ctx.graphics();var f=ctx.font();
        // Pixel diagrams are drawn locally, with no textures, network or timer.
        if(w<110){g.drawString(f,f.plainSubstrByWidth(label,Math.max(1,w-8)),x+4,y+10,text(),false);return;}
        if(tutorialStep==0){
            g.fill(x+7,y+5,x+19,y+11,edge());g.fill(x+7,y+4,x+12,y+6,edge());
            g.fill(x+12,y+13,x+13,y+25,dim());g.fill(x+12,y+22,x+22,y+23,dim());
            g.fill(x+24,y+17,x+31,y+26,text());g.drawString(f,f.plainSubstrByWidth(label,w-40),x+36,y+18,text(),false);
        }else if(tutorialStep==1){
            g.fill(x+7,y+5,x+8,y+25,edge());g.drawString(f,"1",x+11,y+6,dim(),false);g.drawString(f,"2",x+11,y+18,dim(),false);
            g.drawString(f,f.plainSubstrByWidth(label,w-36),x+29,y+6,text(),false);g.fill(x+29,y+21,x+Math.min(w-6,70),y+22,accent());
        }else if(tutorialStep==2){
            for(int i=0;i<6;i++)g.fill(x+8+i,y+7+i,x+9+i,y+20-i,accent());
            g.fill(x+21,y+6,x+22,y+24,edge());g.drawString(f,f.plainSubstrByWidth(label,w-31),x+27,y+11,text(),false);
        }else if(tutorialStep==3){
            int mid=w/2;g.fill(x+6,y+5,x+mid-7,y+25,raised());g.fill(x+mid+7,y+5,x+w-6,y+25,raised());
            g.fill(x+mid-7,y+14,x+mid+7,y+15,accent());g.drawString(f,f.plainSubstrByWidth("Main",Math.max(1,mid-20)),x+10,y+11,text(),false);
            g.drawString(f,f.plainSubstrByWidth("JVM",Math.max(1,mid-20)),x+mid+12,y+11,text(),false);
        }else{
            g.drawString(f,">",x+7,y+10,accent(),false);g.drawString(f,f.plainSubstrByWidth(label,w-31),x+23,y+10,text(),false);
            g.fill(x+7,y+25,x+14,y+26,dim());
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(!contains(x,y))return false;requestFocus();if(button==0){for(var hit:hits)if(hit.bounds.contains(x,y)){hit.action.run();return true;}searchFocus=false;}return true;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!contains(x,y))return false;if((sidebarW>0&&x<articleX)||(sidebarW==0&&indexOpen))indexScroll+=dy<0?3:dy>0?-3:0;else scroll=Math.max(0,Math.min(maxScroll,scroll+(dy<0?36:dy>0?-36:0)));return true;}
    @Override public boolean charTyped(char c,int mods){if(!isFocused()||!searchFocus)return false;ensure();if(c>=32&&c!=127)search((selectQuery?"":query)+c);return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==256)return false;if(!isFocused())return false;ensure();if((mods&2)!=0){if(key==70){searchFocus=true;if(bounds.width()<430)indexOpen=true;return true;}if(key==67){copy(article().copyText());return true;}if(key==65&&searchFocus){selectQuery=true;return true;}if(key==86&&searchFocus){String clip=Minecraft.getInstance().keyboardHandler.getClipboard();if(clip!=null)search((selectQuery?"":query)+clip.substring(0,Math.min(160,clip.length())).replaceAll("[\\p{Cntrl}]",""));return true;}}
        if(searchFocus){if(key==259&&!query.isEmpty())search(selectQuery?"":query.substring(0,query.length()-1));else if(key==257&&!results.isEmpty())openArticle(results.get(0).id());return true;}if(key==268)scroll=0;else if(key==269)scroll=maxScroll;else if(key==267)scroll=Math.min(maxScroll,scroll+120);else if(key==266)scroll=Math.max(0,scroll-120);else if(key==264)scroll=Math.min(maxScroll,scroll+36);else if(key==265)scroll=Math.max(0,scroll-36);else return false;return true;}
}
