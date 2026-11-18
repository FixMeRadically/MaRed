package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.live122.WorkspaceProject126;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.technology.server.setup.LocalPickerScreen68;
import com.fixmer.mared.technology.workspace.WorkspacePickerScreen112;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.*;

/** The local scan is reviewable before one atomic server import. No local path is sent to the server. */
public final class WorkspaceTools126 extends MaredScreen {
    private final WorkspaceScreen122 previous;
    private Map<String,String> context;
    private EditBox id,query;
    private String modId="examplemod",search="",status="",packed="";
    private boolean reading,matchCase;
    private WorkspaceProject126.Scan scanned;
    private int generation,page;
    public WorkspaceTools126(WorkspaceScreen122 previous,Map<String,String> context){super(tr("title"));this.previous=previous;this.context=Map.copyOf(context);}
    static Component tr(String key,Object...args){return Component.translatable("mared.workspace126."+key,args);}
    private Button button(String key,int x,int y,int w,Runnable action){return addRenderableWidget(WorkspaceButton131.builder(tr(key),b->action.run()).bounds(x,y,Math.max(1,w),20).build());}
    private boolean ready(){return !reading&&previous.available();}
    private void collect(){if(id!=null){modId=id.getValue().trim();search=query.getValue();}}
    private void send(String op,Map<String,String> fields,java.util.function.Consumer<Map<String,String>> action){previous.send(op,fields,d->{status=d.getOrDefault("result","INVALID");if(d.containsKey("revision"))context=d;if(status.equals("OK"))action.accept(d);});}
    @Override protected void init(){collect();previous.attachClient();int w=Math.max(1,Math.min(900,width-24)),x=(width-w)/2;if(width<480||height<300){button("back",x,height-28,w,this::onClose);return;}
        id=addRenderableWidget(new EditBox(font,x,38,w-154,20,tr("mod_id")));id.setMaxLength(48);id.setValue(modId);id.setHint(tr("mod_id"));button("template",x+w-150,38,150,()->{collect();if(ready())minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes&&ready())send("TEMPLATE126",Map.of("id",modId,"rev",context.get("revision")),d->{status=tr("imported",d.get("imported")).getString();scanned=null;packed="";rebuildWidgets();});},tr("template"),tr("template_confirm",modId)));});
        int half=(w-4)/2;button("choose",x,64,half,()->{if(ready())minecraft.setScreen(new LocalPickerScreen68(this,true,minecraft.gameDirectory.toPath(),this::scan));});button("import",x+half+4,64,half,this::importFiles).active=scanned!=null;
        query=addRenderableWidget(new EditBox(font,x,90,w-220,20,tr("query")));query.setMaxLength(120);query.setValue(search);query.setHint(tr("query"));button(matchCase?"case_on":"case_off",x+w-216,90,106,()->{collect();matchCase=!matchCase;rebuildWidgets();});button("search",x+w-106,90,106,this::search);
        int rows=Math.max(1,(height-210)/22);List<String> files=scanned==null?List.of():new TreeSet<>(scanned.files().keySet()).stream().toList();int pages=Math.max(1,(files.size()+rows-1)/rows);page=Math.min(page,pages-1);
        for(int n=0;n<rows&&page*rows+n<files.size();n++){String path=files.get(page*rows+n);var row=addRenderableWidget(WorkspaceButton131.builder(Component.literal(path+" · "+scanned.files().get(path).codePointCount(0,scanned.files().get(path).length())),b->minecraft.setScreen(new WorkspaceDiffScreen126(this,Map.of("path",path,"beforeText","","afterText",scanned.files().get(path),"text126","true","revision",context.getOrDefault("revision","0"))))).bounds(x,140+n*22,w,20).build());}
        button("previous",x,height-76,100,()->{page--;rebuildWidgets();}).active=page>0;button("skipped",x+104,height-76,Math.max(1,w-208),()->{if(scanned!=null){var items=scanned.skipped().stream().map(p->new WorkspacePickerScreen112.Item(p,()->{})).toList();minecraft.setScreen(new WorkspacePickerScreen112(this,tr("skipped"),items,previous::tickClient));}});button("next",x+w-100,height-76,100,()->{page++;rebuildWidgets();}).active=page+1<pages;button("refresh",x,height-28,half,()->{if(ready())send("VIEW",Map.of(),d->status=tr("refreshed").getString());});button("back",x+half+4,height-28,half,this::onClose);
    }
    private void scan(java.nio.file.Path path){if(!ready())return;reading=true;scanned=null;packed="";status=tr("scanning").getString();int expected=++generation;
        CompletableFuture.runAsync(()->{WorkspaceProject126.Scan result=null;String wire="",error="";try{result=WorkspaceProject126.scan(path);wire=WorkspaceProject126.pack(result.files());}catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();}var value=result;var data=wire;var problem=error;Minecraft.getInstance().execute(()->{if(expected!=generation)return;reading=false;scanned=value;packed=data;status=value==null?problem:tr("preview",value.files().size(),value.skipped().size()).getString();page=0;if(minecraft.screen==this)rebuildWidgets();});});
    }
    private void importFiles(){if(!ready()||scanned==null)return;collect();String data=packed,rev=context.get("revision");int count=scanned.files().size();minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes&&ready())send("IMPORT_TEXT126",Map.of("data",data,"rev",rev),d->{scanned=null;packed="";status=tr("imported",d.get("imported")).getString();rebuildWidgets();});},tr("import"),tr("import_confirm",count)));}
    private void search(){collect();if(!ready()||search.isBlank())return;send("SEARCH126",Map.of("query",search,"matchCase",Boolean.toString(matchCase)),d->{var items=new ArrayList<WorkspacePickerScreen112.Item>();for(String row:d.getOrDefault("hits","").split("\n")){String[] p=row.split("\\|",3);if(p.length==3)items.add(new WorkspacePickerScreen112.Item(p[0]+":"+p[1]+" · "+p[2],()->previous.openAt126(p[0],Integer.parseInt(p[1]))));}minecraft.setScreen(new WorkspacePickerScreen112(this,tr(Boolean.parseBoolean(d.get("searchMore"))?"results_more":"results",items.size()),items,previous::tickClient));});}
    @Override public void tick(){previous.tickClient();if(!previous.panelAllowed131("CODE")){minecraft.setScreen(previous);return;}if(Set.of("UNKNOWN","DISCONNECTED","IDENTITY_CHANGED","STORE_CHANGED","INVALID").contains(previous.transportState126()))status=previous.transportState126();}
    @Override public void onClose(){generation++;reading=false;minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());g.drawCenteredString(font,title,width/2,10,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());super.render(g,mx,my,dt);if(width>=480&&height>=300){g.drawString(font,font.plainSubstrByWidth(tr("help").getString(),width-24),12,120,com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(),false);g.drawString(font,font.plainSubstrByWidth(status,width-24),12,height-49,0xFFE4BC78,false);}}
}
