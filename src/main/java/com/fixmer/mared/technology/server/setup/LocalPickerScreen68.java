package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.firstlaunch.LocalFileBrowser68;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Minecraft-native, read-only picker. Bounded scans run off the render thread. */
public final class LocalPickerScreen68 extends MaredScreen {
    private final Screen previous;private final boolean directories;private final boolean tls;private final Consumer<Path> chosen;
    private final AtomicInteger ticket=new AtomicInteger();private ExecutorService io;
    private Path current,selected;private List<LocalFileBrowser68.Entry> entries=List.of();
    private EditBox location,filter;private final List<Button> rows=new ArrayList<>();private Button choose,backPage,nextPage;
    private int left,bodyWidth,listTop,page,pageSize=1;private String status="loading",query="";private boolean loading;
    private long searchDue,lastClick;private Path clicked;
    public LocalPickerScreen68(Screen previous,boolean directories,Path initial,Consumer<Path> chosen){
        this(previous,directories,initial,chosen,false);
    }
    public LocalPickerScreen68(Screen previous,boolean directories,Path initial,Consumer<Path> chosen,boolean tls){
        super(tls?Component.translatable("mared.https78.pick_file"):tr(directories?"folder_title":"jar_title"));this.tls=tls;this.previous=previous;this.directories=directories;this.chosen=chosen;
        Path candidate=initial==null?Path.of(System.getProperty("user.home")):initial.toAbsolutePath().normalize();
        current=Files.isDirectory(candidate,LinkOption.NOFOLLOW_LINKS)?candidate:candidate.getParent();
        if(current==null)current=Path.of(System.getProperty("user.home"));
    }
    private static Component tr(String key,Object...args){return Component.translatable("mared.browser68."+key,args);}
    private Button button(Component label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(label,b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        if(io==null||io.isShutdown())io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"genesis-file-browser68");t.setDaemon(true);return t;});
        bodyWidth=Math.max(240,Math.min(680,width-24));left=(width-bodyWidth)/2;rows.clear();
        location=addRenderableWidget(new EditBox(font,left,29,bodyWidth-61,20,tr("path")));location.setMaxLength(4096);location.setValue(current.toString());
        button(tr("go"),left+bodyWidth-56,29,56,this::go);
        int gap=5,bw=(bodyWidth-3*gap)/4;
        button(tr("up"),left,54,bw,()->navigate(current.getParent()));
        button(tr("home"),left+bw+gap,54,bw,()->navigate(Path.of(System.getProperty("user.home"))));
        button(tr("downloads"),left+2*(bw+gap),54,bw,()->navigate(Path.of(System.getProperty("user.home"),"Downloads")));
        button(tr("game"),left+3*(bw+gap),54,bw,()->navigate(minecraft.gameDirectory.toPath()));
        filter=addRenderableWidget(new EditBox(font,left,79,bodyWidth,20,tr("search")));filter.setMaxLength(256);filter.setValue(query);filter.setHint(tr("search"));filter.setResponder(value->{query=value;searchDue=System.nanoTime()+200_000_000L;});
        listTop=105;pageSize=Math.max(1,(height-listTop-83)/22);
        for(int n=0;n<pageSize;n++){final int row=n;rows.add(button(Component.empty(),left,listTop+22*n,bodyWidth,()->click(row)));}
        int bottom=height-27;choose=button(tls?Component.translatable("mared.https78.choose_file"):tr(directories?"choose_folder":"choose_jar"),left,bottom,(bodyWidth-5)/2,this::select);
        button(tr("cancel"),left+(bodyWidth+5)/2,bottom,(bodyWidth-5)/2,this::onClose);
        backPage=button(tr("previous"),left,height-53,80,()->page(-1));nextPage=button(tr("next"),left+bodyWidth-80,height-53,80,()->page(1));
        load();
    }
    private void go(){try{navigate(Path.of(location.getValue().trim()));}catch(RuntimeException bad){status="unavailable";}}
    private void navigate(Path path){if(path==null)return;current=path.toAbsolutePath().normalize();location.setValue(current.toString());query="";filter.setValue("");searchDue=0;page=0;selected=null;load();}
    private void load(){
        int id=ticket.incrementAndGet();Path requested=current;String search=query;loading=true;status="loading";selected=null;refresh();
        io.execute(()->{try{
            var result=tls?LocalFileBrowser68.listTypes(requested,Set.of(".pem",".crt",".cer",".key"),search,()->ticket.get()!=id||Thread.currentThread().isInterrupted()):LocalFileBrowser68.list(requested,!directories,search,()->ticket.get()!=id||Thread.currentThread().isInterrupted());
            minecraft.execute(()->{if(ticket.get()!=id||minecraft.screen!=this)return;current=result.directory();entries=result.entries();page=0;loading=false;status=result.truncated()?"limited":result.skipped()>0?"skipped":entries.isEmpty()?"empty":"ready";refresh();});
        }catch(Exception bad){minecraft.execute(()->{if(ticket.get()!=id||minecraft.screen!=this)return;entries=List.of();loading=false;status="unavailable";refresh();});}});
    }
    private void page(int delta){page=Math.max(0,Math.min(Math.max(0,(entries.size()-1)/pageSize),page+delta));selected=null;refresh();}
    private void click(int row){int index=page*pageSize+row;if(loading||index>=entries.size())return;var entry=entries.get(index);selected=entry.path();long now=System.nanoTime();boolean twice=selected.equals(clicked)&&now-lastClick<350_000_000L;clicked=selected;lastClick=now;refresh();if(twice){if(entry.directory())navigate(selected);else if(!directories)select();}}
    private void select(){
        if(loading)return;Path target=selected==null&&directories?current:selected;if(target==null)return;
        try{Path accepted=tls?LocalFileBrowser68.selectTls(target):LocalFileBrowser68.select(target,directories);chosen.accept(accepted);minecraft.setScreen(previous);}catch(Exception bad){status="BROWSER_JAR_NAME".equals(bad.getMessage())?"selection_name":"selection_failed";refresh();}
    }
    private void refresh(){
        if(choose==null)return;
        for(int n=0;n<rows.size();n++){int index=page*pageSize+n;Button b=rows.get(n);b.visible=index<entries.size()&&!loading;b.active=b.visible;if(!b.visible)continue;var e=entries.get(index);String label=(e.path().equals(selected)?"> ":"")+(e.directory()?"[+] ":"")+e.name();b.setMessage(Component.literal(font.plainSubstrByWidth(label,bodyWidth-12)));b.setTooltip(Tooltip.create(Component.literal(e.path().toString())));}
        choose.setTooltip(Tooltip.create(Component.literal((selected==null?current:selected).toString())));
        choose.active=!loading&&(directories?(selected==null||Files.isDirectory(selected,LinkOption.NOFOLLOW_LINKS)):selected!=null&&!Files.isDirectory(selected,LinkOption.NOFOLLOW_LINKS));backPage.active=!loading&&page>0;nextPage.active=!loading&&(page+1)*pageSize<entries.size();
    }
    @Override public void tick(){if(searchDue!=0&&System.nanoTime()>=searchDue){searchDue=0;load();}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){ticket.incrementAndGet();if(io!=null)io.shutdownNow();}
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==257||key==335){if(location.isFocused())go();else if(filter.isFocused()){searchDue=0;load();}else return super.keyPressed(key,scan,mods);return true;}
        if(key==266||key==267){page(key==266?-1:1);return true;}return super.keyPressed(key,scan,mods);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(y>=listTop&&y<height-58&&dy!=0){page(dy<0?1:-1);return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void onFilesDrop(List<Path> paths){if(paths.size()!=1){status="one_drop";return;}Path path=paths.get(0);if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))navigate(path);else if(!directories){ticket.incrementAndGet();loading=false;selected=path;select();}else status="selection_failed";}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fillGradient(0,0,width,height,0xFF11141B,0xFF1A1D29);g.drawCenteredString(font,title,width/2,12,0xFFF0F1FC);
        g.enableScissor(left,listTop,left+bodyWidth,Math.max(listTop,height-57));if(loading||entries.isEmpty())g.drawString(font,tr(status),left+5,listTop+6,0xFFB8A5F2,false);g.disableScissor();
        g.drawCenteredString(font,tr("page",page+1,Math.max(1,(entries.size()+pageSize-1)/pageSize)),width/2,height-47,0xFFBEC2D5);
        g.drawString(font,font.plainSubstrByWidth(tr(status).getString(),bodyWidth),left,height-67,status.equals("unavailable")||status.equals("selection_failed")?0xFFE8A7A7:0xFFBEC2D5,false);
        super.render(g,mx,my,delta);
    }
}
