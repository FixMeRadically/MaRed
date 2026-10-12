package com.fixmer.mared.technology.editor;

import com.fixmer.genesis.technology.editor.DocumentSearch;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.technology.search.DocumentSearchTask;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Client-only saved-file search. Results and widgets are owned exclusively by the UI thread. */
public final class ProjectSearchPanel extends MaredComponent implements Disposable {
    private static final int LIST_Y = 72, ROW_H = 26;
    private final DocumentSearchTask task = new DocumentSearchTask();
    private final Predicate<DocumentSearch.Hit> open;
    private final Runnable back;
    private EditBox query;
    private DocumentSearch.Mode mode = DocumentSearch.Mode.CONTENT;
    private boolean matchCase, disposed, focusOnRender, showErrors;
    private java.util.List<java.util.Map.Entry<String,String>> errors=java.util.List.of();
    private Future<DocumentSearch.Result> pending;
    private DocumentSearch.Result result;
    private String message = "";
    private int scroll, selected = -1;

    public ProjectSearchPanel(Predicate<DocumentSearch.Hit> open, Runnable back) { this.open = open; this.back = back; }
    @Override public boolean focusable() { return true; }
    public void focusQuery() { focusOnRender=true; if(query!=null){query.setFocused(true);focusOnRender=false;} }
    @Override public void onFocusLost() { focusOnRender=false; if (query != null) query.setFocused(false); }
    public void cancelInteraction() { onFocusLost(); task.cancel(); pending = null; }
    @Override public void dispose() { disposed = true; cancelInteraction(); task.close(); }
    private void invalidate() { task.cancel(); pending = null; result = null; message = ""; scroll = 0; selected = -1; showErrors=false; errors=java.util.List.of(); }
    private void start() {
        if (disposed || query == null) return;
        invalidate();
        if (query.getValue().isEmpty()) return;
        try { pending = task.start(query.getValue(), mode, matchCase); }
        catch (RuntimeException error) { message = MaredLang.get("mared.project_search.failed"); }
    }
    private void poll() {
        if (pending == null || !pending.isDone()) return;
        var completed = pending; pending = null;
        try { result = completed.get(); selected = result.hits().isEmpty() ? -1 : 0; errors=result.errors().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList(); }
        catch (CancellationException ignored) { }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); message = MaredLang.get("mared.project_search.failed"); }
        catch (ExecutionException error) { message = MaredLang.get("mared.project_search.failed") + ": " + error.getCause().getMessage(); }
    }
    private int visibleRows() { return Math.max(1, (bounds.height() - LIST_Y) / ROW_H); }
    private int resultCount(){return result==null?0:showErrors?errors.size():result.hits().size();}
    private void openSelected() {
        if(showErrors)return;
        if (result == null || selected < 0 || selected >= result.hits().size()) return;
        if (open.test(result.hits().get(selected))) back.run();
        else message = MaredLang.get("mared.project_search.stale");
    }
    private static String fit(net.minecraft.client.gui.Font font, String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }
    @Override protected void safeRender(MaredRenderContext ctx) {
        poll(); var g = ctx.graphics(); var font = ctx.font();
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), panel());
        if (bounds.width() < 150 || bounds.height() < 90) {
            onFocusLost(); g.drawString(font, MaredLang.get("mared.project_search.enlarge"), bounds.x()+4, bounds.y()+4, dim(), false); return;
        }
        g.drawString(font, fit(font,MaredLang.get("mared.project_search.title"),bounds.width()-52), bounds.x()+4, bounds.y()+3, text(), false);
        g.drawString(font, MaredLang.get("mared.project_search.back"), bounds.right()-38, bounds.y()+3, accent(), false);
        if (query == null) {
            query = new EditBox(font, 0, 0, 80, 14, Component.literal(MaredLang.get("mared.project_search.query")));
            query.setMaxLength(DocumentSearch.MAX_QUERY); query.setBordered(false);
            query.setHint(Component.literal(MaredLang.get("mared.project_search.query")));
            query.setResponder(value -> invalidate());
        }
        query.setX(bounds.x()+5); query.setY(bounds.y()+20); query.setWidth(Math.max(20,bounds.width()-10));
        if(focusOnRender){query.setFocused(true);focusOnRender=false;}
        query.setTextColor(text()); query.setTextColorUneditable(dim());
        g.fill(bounds.x()+3,bounds.y()+18,bounds.right()-3,bounds.y()+35,raised()); query.render(g,ctx.mouseX(),ctx.mouseY(),0);
        g.drawString(font, MaredLang.get("mared.project_search.names"), bounds.x()+4,bounds.y()+40,mode==DocumentSearch.Mode.PATH?accent():dim(),false);
        g.drawString(font, MaredLang.get("mared.project_search.content"), bounds.x()+58,bounds.y()+40,mode==DocumentSearch.Mode.CONTENT?accent():dim(),false);
        g.drawString(font,"Aa",bounds.x()+126,bounds.y()+40,matchCase?accent():dim(),false);
        String status = pending != null ? MaredLang.get("mared.project_search.loading") : MaredLang.get("mared.project_search.saved");
        if (result != null) status = MaredLang.format("mared.project_search.summary",result.hits().size(),result.scanned(),result.errors().size())
            + (result.limited()?" · "+MaredLang.get("mared.project_search.limit"):"");
        if (!message.isEmpty()) status = message;
        g.drawString(font,fit(font,status,bounds.width()-(errors.isEmpty()?8:28)),bounds.x()+4,bounds.y()+57,dim(),false);
        if (result == null) return;
        if(!errors.isEmpty())g.drawString(font,"!",bounds.right()-15,bounds.y()+57,showErrors?accent():dim(),false);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,resultCount()-visibleRows())));
        int end = Math.min(resultCount(),scroll+visibleRows());
        for (int i=scroll;i<end;i++) {
            int y=bounds.y()+LIST_Y+(i-scroll)*ROW_H;
            if(showErrors){
                var error=errors.get(i);
                g.drawString(font,fit(font,error.getKey(),bounds.width()-12),bounds.x()+6,y+2,text(),false);
                g.drawString(font,fit(font,error.getValue(),bounds.width()-12),bounds.x()+6,y+13,dim(),false);
                continue;
            }
            var hit=result.hits().get(i);
            if(i==selected)g.fill(bounds.x()+2,y,bounds.right()-2,y+ROW_H-1,raised());
            String location=hit.path()+(hit.line()<0?"":":"+(hit.line()+1)+":"+(hit.column()+1));
            g.drawString(font,fit(font,location,bounds.width()-12),bounds.x()+6,y+2,i==selected?accent():text(),false);
            g.drawString(font,fit(font,hit.preview(),bounds.width()-12),bounds.x()+6,y+13,dim(),false);
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(!bounds.contains(x,y)||button!=0||bounds.width()<150||bounds.height()<90)return false;
        requestFocus();
        int relativeY=(int)y-bounds.y();
        if(relativeY<16&&x>=bounds.right()-42){back.run();return true;}
        if(query!=null&&query.isMouseOver(x,y)){query.setFocused(true);query.mouseClicked(x,y,button);return true;}
        if(query!=null)query.setFocused(false);
        if(relativeY>=36&&relativeY<51){
            if(x>=bounds.x()+120)matchCase=!matchCase;
            else mode=x<bounds.x()+55?DocumentSearch.Mode.PATH:DocumentSearch.Mode.CONTENT;
            invalidate();start();return true;
        }
        if(relativeY>=52&&relativeY<LIST_Y&&x>=bounds.right()-24&&!errors.isEmpty()){showErrors=!showErrors;scroll=0;return true;}
        if(relativeY>=LIST_Y&&result!=null&&!showErrors){int row=scroll+(relativeY-LIST_Y)/ROW_H;if(row<result.hits().size()){selected=row;openSelected();}return true;}
        return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if(!bounds.contains(x,y)||result==null||y<bounds.y()+LIST_Y)return false;
        int max=Math.max(0,resultCount()-visibleRows());scroll=Math.max(0,Math.min(max,scroll+(dy<0?3:dy>0?-3:0)));return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(bounds.width()<150||bounds.height()<90)return false;
        if(key==256){back.run();return true;}
        if(key==257||key==335){if(query!=null&&query.isFocused())start();else openSelected();return true;}
        if(key==258){if(query!=null)query.setFocused(!query.isFocused());return true;}
        if((key==264||key==265)&&result!=null&&!showErrors&&!result.hits().isEmpty()){
            boolean fromQuery=query!=null&&query.isFocused();
            if(query!=null)query.setFocused(false);
            selected=fromQuery?0:Math.max(0,Math.min(result.hits().size()-1,selected+(key==264?1:-1)));
            scroll=Math.max(0,Math.min(scroll,selected));if(selected>=scroll+visibleRows())scroll=selected-visibleRows()+1;return true;
        }
        return query!=null&&query.isFocused()&&query.keyPressed(key,scan,mods);
    }
    @Override public boolean charTyped(char c,int mods) { return bounds.width()>=150&&bounds.height()>=90&&query!=null&&query.isFocused()&&query.charTyped(c,mods); }
}
