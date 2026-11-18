package com.fixmer.mared.technology.scripts;

import com.fixmer.genesis.technology.scripts.ScriptSearch;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.overlay.*;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.technology.editor.EditorDialogStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Keyboard-first search over a captured project list or open-document snapshot. */
public final class ScriptSearchOverlay implements Overlay {
    @FunctionalInterface public interface SearchJob {ScriptSearch.Result run(String query,java.util.function.BooleanSupplier cancelled)throws Exception;}
    public record Mode(String label, boolean enabled, Consumer<String> select) {}
    private List<Mode> modes=List.of();
    private int modeIndex,modeY;
    public ScriptSearchOverlay modes(int current,List<Mode> values){modeIndex=current;modes=List.copyOf(values);return this;}
    private final SearchJob job;
    private final String title;
    private final int accent;
    private final Consumer<ScriptSearch.Hit> selected;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Genesis Scripts search");t.setDaemon(true);return t;});
    private final EditBox query;
    private Future<ScriptSearch.Result> pending;
    private List<ScriptSearch.Hit> rows=List.of();
    private String pendingQuery="",message="";
    private boolean dirty=true,limited,closeRequested;
    private volatile boolean closed;
    private long due;
    private int index,scroll,x,y,w,h,listY,listBottom,visible=1,buttonY,buttonW;
    public ScriptSearchOverlay(String title,List<String> files,String path,String source,String initial,int accent,Consumer<ScriptSearch.Hit> selected) {
        this(title,snapshotJob(files,path,source),source==null,initial,accent,selected);
    }
    private static SearchJob snapshotJob(List<String> files,String path,String source){
        var snapshot=List.copyOf(files);
        return source==null?(value,stop)->ScriptSearch.files(snapshot,value,stop):(value,stop)->ScriptSearch.text(path,source,value,stop);
    }
    private final boolean browse;
    public ScriptSearchOverlay(String title,SearchJob job,boolean browse,String initial,int accent,Consumer<ScriptSearch.Hit> selected) {
        this.title=title;this.job=job;this.browse=browse;this.accent=accent;this.selected=selected;
        query=new EditBox(Minecraft.getInstance().font,0,0,80,18,Component.literal(tr("search_query")));
        query.setMaxLength(ScriptSearch.MAX_QUERY);query.setBordered(false);query.setHint(Component.literal(tr("search_query")));
        query.setResponder(value->{dirty=true;due=System.nanoTime()+120_000_000L;rows=List.of();index=scroll=0;limited=false;message="";if(pending!=null){pending.cancel(true);pending=null;}});
        query.setValue(initial);query.setFocused(true);
    }
    private static String tr(String key){return MaredLang.get("mared.scripts."+key);}
    @Override public OverlayLayer layer(){return OverlayLayer.MODAL;}
    @Override public boolean isInputBarrier(){return true;}
    @Override public boolean consumeCloseRequest(){boolean value=closeRequested;closeRequested=false;return value;}
    @Override public void onClose(){if(closed)return;closed=true;if(pending!=null)pending.cancel(true);worker.shutdownNow();}
    private void poll() {
        if(closed)return;
        if(dirty&&System.nanoTime()>=due) {
            dirty=false;String value=query.getValue();pendingQuery=value;
            pending=worker.submit(()->job.run(value,()->closed));
        }
        if(pending!=null&&pending.isDone()) {
            var task=pending;pending=null;
            try {var result=task.get();if(pendingQuery.equals(query.getValue())){rows=result.hits();limited=result.limited();message=result.errors().isEmpty()?"":tr("search_read_errors")+": "+result.errors().size()+" · "+result.errors().keySet().iterator().next();reveal();}}
            catch(CancellationException ignored){}
            catch(InterruptedException bad){Thread.currentThread().interrupt();}
            catch(ExecutionException bad){message=Objects.toString(bad.getCause().getMessage(),bad.getCause().toString());}
        }
    }
    private void reveal(){index=Math.max(0,Math.min(index,Math.max(0,rows.size()-1)));if(index<scroll)scroll=index;if(index>=scroll+visible)scroll=index-visible+1;scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visible)));}
    private void accept(){poll();if(!rows.isEmpty()){selected.accept(rows.get(index));closeRequested=true;}}
    @Override public void render(GuiGraphics g,Font font,int screenW,int screenH,int mx,int my) {
        poll();w=Math.min(680,Math.max(1,screenW-16));y=Math.min(25,Math.max(8,screenH/4));h=Math.min(500,Math.max(1,screenH-y-8));x=(screenW-w)/2;
        var style=EditorDialogStyle.active(accent);g.fill(0,0,screenW,screenH,0x88000000);style.panel(g,x,y,w,h);
        
        if(w<120||h<(modes.isEmpty()?125:205)){g.drawString(font,TextUtils.ellipsize(font,tr("search_resize"),Math.max(1,w-16)),x+8,y+28,style.muted(),false);return;}
        query.setX(x+12);query.setY(y+4);query.setWidth(Math.max(1,w-24));query.setTextColor(style.text());query.setTextColorUneditable(style.muted());
        g.fill(x+8,y+1,x+w-8,y+23,style.input());query.render(g,mx,my,0);
        modeY=y+26;
        for(int i=0;i<modes.size();i++){
            var mode=modes.get(i);int top=modeY+i*20;
            if(i==modeIndex)g.fill(x+8,top,x+w-8,top+20,style.selection());
            g.drawString(font,TextUtils.ellipsize(font,mode.label(),Math.max(1,w-24)),x+12,top+6,mode.enabled()?style.text():style.muted(),false);
        }
        int statusY=modeY+modes.size()*20+3;
        String status=dirty||pending!=null?tr("search_loading"):rows.size()+""+(limited?" · "+tr("search_limit"):"");
        if(!message.isEmpty())status=message;
        else status=title+" · "+status;
        g.drawString(font,TextUtils.ellipsize(font,status,Math.max(1,w-16)),x+8,statusY,style.muted(),false);
        listY=statusY+15;buttonY=y+h-25;listBottom=Math.max(listY,buttonY-19);visible=Math.max(1,(listBottom-listY)/23);reveal();
        g.enableScissor(x+8,listY,x+w-8,listBottom);
        try {
            if(rows.isEmpty()&&!dirty&&pending==null)g.drawString(font,TextUtils.ellipsize(font,tr(!browse&&query.getValue().isEmpty()?"search_query":"search_empty"),Math.max(1,w-24)),x+12,listY+4,style.muted(),false);
            for(int i=scroll;i<Math.min(rows.size(),scroll+visible);i++) {
                int top=listY+(i-scroll)*23;var hit=rows.get(i);if(i==index)g.fill(x+8,top,x+w-8,top+23,style.selection());
                String label=hit.path()+(hit.line()<0?"":":"+(hit.line()+1)+":"+(hit.column()+1));
                g.drawString(font,TextUtils.ellipsize(font,label,Math.max(1,w-24)),x+12,top+2,style.text(),false);
                if(hit.line()>=0)g.drawString(font,TextUtils.ellipsize(font,hit.preview(),Math.max(1,w-24)),x+12,top+12,style.muted(),false);
            }
        }finally{g.disableScissor();}
        g.drawString(font,TextUtils.ellipsize(font,tr("search_keys"),Math.max(1,w-16)),x+8,buttonY-13,style.muted(),false);
        buttonW=Math.max(1,(w-22)/2);style.button(g,font,x+8,buttonY,buttonW,18,tr("report_close"),buttonHit(mx,my,x+8),false,false);
        style.button(g,font,x+14+buttonW,buttonY,buttonW,18,tr("search_open"),buttonHit(mx,my,x+14+buttonW),!rows.isEmpty(),false);
    }
    private boolean buttonHit(double mx,double my,int bx){return mx>=bx&&mx<bx+buttonW&&my>=buttonY&&my<buttonY+18;}
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(mx<x||mx>=x+w||my<y||my>=y+h){closeRequested=true;return true;}
        if(w<120||h<(modes.isEmpty()?125:205))return true;
        if(button==0){
            if(mx>=x+8&&mx<x+w-8&&my>=modeY&&my<modeY+modes.size()*20){
                var mode=modes.get(((int)my-modeY)/20);if(mode.enabled()){closeRequested=true;mode.select().accept(query.getValue());}return true;
            }
            if(buttonHit(mx,my,x+8))closeRequested=true;else if(buttonHit(mx,my,x+14+buttonW))accept();else if(mx>=x+8&&mx<x+w-8&&my>=listY&&my<listBottom){int row=scroll+((int)my-listY)/23;if(row<rows.size())index=row;}else query.mouseClicked(mx,my,button);query.setFocused(true);}return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){index+=dy<0?3:dy>0?-3:0;reveal();return true;}
    @Override public boolean charTyped(char c,int modifiers){query.charTyped(c,modifiers);return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        poll();
        if((modifiers&2)!=0&&!modes.isEmpty()){
            int target=key==80?0:key==70?((modifiers&1)!=0?2:1):key==71?3:key==79?4:-1;
            if(target>=0){var mode=modes.get(target);if(mode.enabled()){closeRequested=true;mode.select().accept(query.getValue());}return true;}
        }
        if(key==256)closeRequested=true;else if(key==257||key==335)accept();
        else if((modifiers&3)==0&&(key==264||key==265||key==266||key==267)){index+=(key==264?1:key==265?-1:key==267?visible:-visible);reveal();}
        else if(key==258&&!modes.isEmpty()){
            int step=(modifiers&1)!=0?-1:1;
            for(int i=1;i<=modes.size();i++){int next=Math.floorMod(modeIndex+step*i,modes.size());var mode=modes.get(next);if(mode.enabled()){closeRequested=true;mode.select().accept(query.getValue());break;}}
        }else query.keyPressed(key,scan,modifiers);return true;
    }
}
