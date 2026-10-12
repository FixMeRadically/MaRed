package com.fixmer.mared.technology.editor;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.overlay.*;
import com.fixmer.mared.technology.storage.ProjectTransfers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.*;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Modal local-project exchange. Work runs off the render thread; preview never writes documents. */
public final class ProjectTransferOverlay implements Overlay {
    private final Runnable imported;
    private final java.util.function.Predicate<String> occupied;
    private final java.util.function.Consumer<String> logger;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"Genesis project transfers");t.setDaemon(true);return t;});
    private Future<?> pending;
    private String operation="", message="";
    private List<String> archives=List.of();
    private ProjectTransfers.Preview preview;
    private EditBox destination;
    private int x,y,w,h,scroll,archiveIndex;
    private String selected;
    private boolean close,disposed;
    public ProjectTransferOverlay(Runnable imported,java.util.function.Consumer<String> logger,java.util.function.Predicate<String> occupied){this.imported=imported;this.logger=logger;this.occupied=occupied;}
    @Override public OverlayLayer layer(){return OverlayLayer.MODAL;}
    @Override public boolean isInputBarrier(){return true;}
    @Override public boolean closeOnEscape(){return pending==null;}
    @Override public boolean consumeCloseRequest(){boolean result=close;close=false;return result;}
    @Override public void onOpen(){refresh();}
    @Override public void onClose(){disposed=true;worker.shutdown();if(destination!=null)destination.setFocused(false);}
    private void refresh(){submit("list",ProjectTransfers::list);}
    private void submit(String kind,Callable<?> job){
        if(disposed||pending!=null)return;
        operation=kind;message=MaredLang.get("mared.transfer.busy");
        try{pending=worker.submit(job);}catch(RuntimeException error){message=error.toString();operation="";}
    }
    private void poll(){
        if(pending==null||!pending.isDone())return;
        var done=pending;pending=null;String kind=operation;operation="";
        try{
            Object result=done.get();
            if(kind.equals("list")){@SuppressWarnings("unchecked") var names=(List<String>)result;archives=names;preview=null;selected=null;scroll=0;archiveIndex=0;message=MaredLang.get("mared.transfer.choose");}
            else if(kind.equals("preview")){preview=(ProjectTransfers.Preview)result;
                var conflicts=new TreeSet<>(preview.conflicts());
                for(String path:preview.plan().documents().keySet())if(occupied.test(path))conflicts.add(path+" [open]");
                preview=new ProjectTransfers.Preview(preview.plan(),new ArrayList<>(conflicts));scroll=0;
                message=preview.conflicts().isEmpty()?MaredLang.format("mared.transfer.ready",preview.plan().documents().size(),preview.plan().directories().size(),preview.plan().bytes()):MaredLang.get("mared.transfer.conflicts");}
            else if(kind.equals("export")){message=MaredLang.format("mared.transfer.exported",result);logger.accept(message);}
            else if(kind.equals("import")){message=MaredLang.get("mared.transfer.imported");preview=null;imported.run();logger.accept(message);}
        }catch(InterruptedException e){Thread.currentThread().interrupt();message=e.toString();}
        catch(ExecutionException|CancellationException e){message=MaredLang.get("mared.transfer.failed")+": "+(e instanceof ExecutionException?e.getCause().getMessage():e.getMessage());logger.accept(message);preview=null;}
    }
    private void choose(String name){selected=name;preview=null;scroll=0;String prefix=destination.getValue();submit("preview",()->ProjectTransfers.preview(name,prefix));}
    private void openFolder(String name){try{net.minecraft.Util.getPlatform().openUri(ProjectTransfers.folder(name).toUri());}catch(Exception e){message=e.getMessage();}}
    private static String fit(Font font,String value,int width){return font.plainSubstrByWidth(value,Math.max(0,width));}
    private boolean usable(){return w>=260&&h>=190;}
    private boolean button(double mx,double my,int bx,int by,int bw){return mx>=bx&&mx<bx+bw&&my>=by&&my<by+18;}
    private void drawButton(GuiGraphics g,Font font,int bx,int by,int bw,String key,boolean enabled){g.fill(bx,by,bx+bw,by+18,enabled?raised():panel());g.drawString(font,fit(font,MaredLang.get(key),bw-8),bx+4,by+5,enabled?text():dim(),false);}
    @Override public void render(GuiGraphics g,Font font,int screenW,int screenH,int mx,int my){
        poll();w=Math.min(760,screenW-24);h=Math.min(480,screenH-24);x=(screenW-w)/2;y=(screenH-h)/2;
        g.fill(0,0,screenW,screenH,0x99000000);g.fill(x,y,x+w,y+h,panel());
        if(!usable()){if(destination!=null)destination.setFocused(false);g.drawString(font,MaredLang.get("mared.editor.replace.enlarge"),x+8,y+8,dim(),false);return;}
        g.fill(x,y,x+w,y+2,accent());
        g.drawString(font,MaredLang.get("mared.transfer.title"),x+10,y+10,text(),false);
        drawButton(g,font,x+10,y+27,90,"mared.transfer.export",pending==null);
        drawButton(g,font,x+104,y+27,80,"mared.transfer.refresh",pending==null);
        drawButton(g,font,x+188,y+27,80,"mared.transfer.inbox",true);
        if(w>=360)drawButton(g,font,x+272,y+27,80,"mared.transfer.outbox",true);
        g.drawString(font,fit(font,MaredLang.get("mared.transfer.saved"),w-20),x+10,y+50,dim(),false);
        if(destination==null){destination=new EditBox(Minecraft.getInstance().font,0,0,80,16,Component.literal(MaredLang.get("mared.transfer.destination")));destination.setMaxLength(256);destination.setBordered(false);destination.setValue("imported");destination.setResponder(value->{preview=null;message=MaredLang.get("mared.transfer.choose");});}
        g.drawString(font,fit(font,MaredLang.get("mared.transfer.destination"),w-20),x+10,y+65,dim(),false);
        destination.setX(x+10);destination.setY(y+78);destination.setWidth(w-20);destination.setHeight(16);destination.setTextColor(text());destination.render(g,mx,my,0);
        g.drawString(font,fit(font,message,w-20),x+10,y+100,preview!=null&&!preview.conflicts().isEmpty()?warn():dim(),false);
        int rows=Math.max(1,(h-157)/16);List<String> list;
        if(preview==null)list=archives;else{var lines=new ArrayList<String>();for(String conflict:preview.conflicts())lines.add("! "+conflict);for(String folder:preview.plan().directories())lines.add(folder+"/");lines.addAll(preview.plan().documents().keySet());list=lines;}
        scroll=Math.max(0,Math.min(scroll,Math.max(0,list.size()-rows)));
        g.enableScissor(x+8,y+117,x+w-8,y+h-34);
        for(int i=0;i<rows&&i+scroll<list.size();i++){
            if(preview==null&&i+scroll==archiveIndex)g.fill(x+9,y+117+i*16,x+w-9,y+133+i*16,raised());
            g.drawString(font,fit(font,list.get(i+scroll),w-24),x+12,y+118+i*16,text(),false);
        }
        g.disableScissor();
        drawButton(g,font,x+10,y+h-26,100,"mared.transfer.install",pending==null&&preview!=null&&preview.conflicts().isEmpty());
        drawButton(g,font,x+w-90,y+h-26,80,preview==null?"mared.transfer.close":"mared.transfer.back",pending==null);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0||!usable()||mx<x||mx>=x+w||my<y||my>=y+h)return true;
        if(button(mx,my,x+188,y+27,80)){openFolder("inbox");return true;}
        if(w>=360&&button(mx,my,x+272,y+27,80)){openFolder("outbox");return true;}
        if(pending!=null)return true;
        if(button(mx,my,x+10,y+27,90)){submit("export",ProjectTransfers::export);return true;}
        if(button(mx,my,x+104,y+27,80)){refresh();return true;}
        if(button(mx,my,x+w-90,y+h-26,80)){if(preview==null)close=true;else{preview=null;selected=null;scroll=0;}return true;}
        if(button(mx,my,x+10,y+h-26,100)&&preview!=null&&preview.conflicts().isEmpty()){
            if(preview.plan().documents().keySet().stream().anyMatch(occupied)){message=MaredLang.get("mared.transfer.conflicts");preview=null;return true;}
            var captured=preview;submit("import",()->{ProjectTransfers.apply(captured);return null;});return true;
        }
        if(my>=y+78&&my<y+94){destination.setFocused(true);destination.mouseClicked(mx,my,button);return true;}
        destination.setFocused(false);
        if(preview==null&&my>=y+117&&my<y+h-34){int index=scroll+(int)(my-y-117)/16;if(index<archives.size()){archiveIndex=index;choose(archives.get(index));}}
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){if(pending==null&&my>=y+117&&my<y+h-34)scroll=Math.max(0,scroll+(dy<0?3:dy>0?-3:0));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(pending!=null)return true;
        if(key==256){close=true;return true;}
        if(!usable()||destination==null)return true;
        if(key==257||key==335){if(selected!=null)choose(selected);else if(!archives.isEmpty())choose(archives.get(archiveIndex));return true;}
        if((key==264||key==265)&&!destination.isFocused()){
            if(preview==null&&!archives.isEmpty()){
                archiveIndex=Math.max(0,Math.min(archives.size()-1,archiveIndex+(key==264?1:-1)));
                selected=null;int rows=Math.max(1,(h-157)/16);if(archiveIndex<scroll)scroll=archiveIndex;if(archiveIndex>=scroll+rows)scroll=archiveIndex-rows+1;
            }else scroll=Math.max(0,scroll+(key==264?1:-1));
            return true;
        }
        if(key==258){destination.setFocused(!destination.isFocused());return true;}
        if(destination.isFocused())destination.keyPressed(key,scan,mods);
        return true;
    }
    @Override public boolean charTyped(char c,int mods){if(pending==null&&usable()&&destination!=null&&destination.isFocused())destination.charTyped(c,mods);return true;}
}
