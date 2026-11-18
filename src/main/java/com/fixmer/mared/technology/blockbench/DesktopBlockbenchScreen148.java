package com.fixmer.mared.technology.blockbench;

import com.fixmer.genesis.technology.creation.*;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import com.fixmer.genesis.technology.workspace.live122.WorkspaceModels147;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.gui2.shell.MaredShellScreen;
import com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Desktop Blockbench remains its own window; only this captured model is shared with MaRed. */
public final class DesktopBlockbenchScreen148 extends MaredScreen {
    private final MaredShellScreen parent;
    private final SourceProjects storage;
    private final String project,initialPath;
    private final Runnable returned;
    private final BiConsumer<String,String> saved;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MaRed desktop model I/O");t.setDaemon(true);return t;});
    private final DesktopConnection150 connection150=new DesktopConnection150();
    private boolean reconnect150;
    private DesktopBridge148 bridge;
    private BlockbenchDocument146 document;
    private WorkspaceModelClient147 shared;
    private WorkspaceModelClient147.Model baseline,pending;
    private EditBox filename,executable;
    private String path="",executableText="",status="",pluginPath="";
    private boolean initialized,closed,busy,ready,sent,temporary,leaving,bound,compactAck149,launching149,diagnostic150;
    private long nextWatch=System.nanoTime(),lastSequence,loadDeadline149;
    private static final String EMPTY="{\"meta\":{\"format_version\":\"4.10\",\"model_format\":\"free\"},\"name\":\"model\",\"resolution\":{\"width\":16,\"height\":16},\"elements\":[],\"outliner\":[],\"textures\":[],\"animations\":[]}";
    public DesktopBlockbenchScreen148(MaredShellScreen parent,SourceProjects storage,String project,String path,BiConsumer<String,String> saved,Runnable returned){
        super(Component.literal("MaRed · Blockbench Desktop"));this.parent=parent;this.storage=storage;this.project=project;initialPath=path;this.path=path.isEmpty()?"models/model.bbmodel":path;this.saved=saved;this.returned=returned;
    }
    public DesktopBlockbenchScreen148(MaredShellScreen parent,WorkspaceScreen122 workspace,String path,Runnable returned){
        this(parent,null,workspace.context132().getOrDefault("title","Workspace"),path,(file,text)->{},returned);
        shared=new WorkspaceModelClient147(workspace.context132(),workspace::modelMetadata147);
    }
    private static String tr(String key){return MaredLang.get("mared.desktop148."+key);}
    private void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(tr(label)),b->action.run()).bounds(x,y,Math.max(1,w),20).build());}
    @Override protected void init(){
        super.init();int w=Math.max(40,width-24);
        filename=new EditBox(font,12,43,w,20,Component.literal(tr("path")));filename.setMaxLength(240);filename.setValue(path);filename.setResponder(v->path=v);addRenderableWidget(filename);
        executable=new EditBox(font,12,82,w,20,Component.literal(tr("executable")));executable.setMaxLength(1024);executable.setValue(executableText);executable.setResponder(v->executableText=v);addRenderableWidget(executable);
        int half=Math.max(40,(w-6)/2);button("launch",12,108,half,this::launch);button("plugin",18+half,108,half,()->minecraft.keyboardHandler.setClipboard(pluginPath));
        button("pair",12,134,w,()->{if(bridge!=null)minecraft.keyboardHandler.setClipboard(bridge.pairing());});
        button("back",12,height-28,w,this::onClose);
        if(!initialized){initialized=true;prepare();}
        filename.setEditable(!bound&&initialPath.isEmpty());executable.setFocused(false);
    }
    private void prepare(){
        busy=true;status=tr("preparing");
        io.execute(()->{try{
            Path folder=Minecraft.getInstance().gameDirectory.toPath().resolve("config/mared/blockbench-desktop148");Files.createDirectories(folder);
            Path target=folder.resolve("mared_desktop148.js");
            try(var resource=DesktopBlockbenchScreen148.class.getResourceAsStream("/assets/mared/blockbench/mared_desktop148.js")){if(resource==null)throw new IOException("PLUGIN_RESOURCE");Files.write(target,resource.readAllBytes());}
            if(Files.exists(folder.resolve("executable.txt"))&&Files.size(folder.resolve("executable.txt"))>4096)throw new IOException("EXECUTABLE_PATH_LIMIT");
            String pref=Files.exists(folder.resolve("executable.txt"))?Files.readString(folder.resolve("executable.txt")).strip():"";
            BlockbenchDocument146 local=storage==null?null:initialPath.isEmpty()?BlockbenchDocument146.create(storage,project):BlockbenchDocument146.open(storage,project,initialPath);
            var server=new DesktopBridge148();minecraft.execute(()->{if(closed){server.close();return;}bridge=server;document=local;pluginPath=target.toAbsolutePath().toString();executableText=pref;if(executable!=null)executable.setValue(pref);
                if(shared==null){baseline=new WorkspaceModelClient147.Model(local.content()==null?EMPTY:local.content(),"",true);busy=false;status=tr("instructions");}
                else if(initialPath.isEmpty()){baseline=new WorkspaceModelClient147.Model(EMPTY,"",true);busy=false;status=tr("instructions");}
                else shared.open(path).whenComplete((model,error)->{busy=false;if(closed)return;if(error!=null)fail(error);else{baseline=model;status=tr("instructions");}});
            });
        }catch(Exception error){minecraft.execute(()->{busy=false;if(!closed)fail(error);});}});
    }
    private void launch(){
        if(closed||launching149)return;launching149=true;
        String configured=executableText.strip();io.execute(()->{try{
            String command=configured;
            if(command.isEmpty()){
                var candidates=new ArrayList<String>();
                for(String env:List.of("LOCALAPPDATA","APPDATA","ProgramFiles")){String base=System.getenv(env);if(base!=null){candidates.add(base+"/Programs/Blockbench/Blockbench.exe");candidates.add(base+"/Blockbench/Blockbench.exe");}}
                candidates.add("/Applications/Blockbench.app/Contents/MacOS/Blockbench");
                command=candidates.stream().filter(p->Files.isRegularFile(Path.of(p))).findFirst().orElse("blockbench");
            }
            new ProcessBuilder(command).start();
            Path pref=Minecraft.getInstance().gameDirectory.toPath().resolve("config/mared/blockbench-desktop148/executable.txt");Files.createDirectories(pref.getParent());Files.writeString(pref,configured);
        }catch(Exception error){minecraft.execute(()->{if(!closed)fail(error);});}finally{minecraft.execute(()->launching149=false);}});
    }
    private void fail(Throwable error){diagnostic150=true;while(error.getCause()!=null)error=error.getCause();status=tr("error")+": "+String.valueOf(error.getMessage());}
    private boolean current150(DesktopConnection150.Ticket ticket){return !closed&&connection150.accepts(ticket)&&bridge!=null&&bridge.connection150()==ticket.generation();}
    private boolean send150(DesktopConnection150.Ticket ticket,JsonObject message){return current150(ticket)&&message.has("session")&&ticket.session().equals(message.get("session").getAsString())&&bridge.send150(ticket.generation(),message.toString());}
    private boolean send(JsonObject message){return send150(connection150.ticket(),message);}
    private JsonObject packet(String type){var q=new JsonObject();q.addProperty("type",type);q.addProperty("session",connection150.ticket().session());return q;}
    private void load(WorkspaceModelClient147.Model model){
        if(!BlockbenchDocument146.validPath(path)){status=tr("invalid_path");return;}
        var q=packet("load");q.addProperty("path",path);q.addProperty("content",model.text()==null||model.text().isBlank()?EMPTY:model.text());q.addProperty("editable",model.editable());
        diagnostic150=false;boolean wasBound=bound;busy=true;pending=model;sent=true;bound=true;loadDeadline149=System.nanoTime()+15_000_000_000L;ready=false;filename.setEditable(false);status=tr("loading");
        var ticket150=connection150.ticket();io.execute(()->{boolean accepted=send150(ticket150,q);minecraft.execute(()->{busy=false;if(!current150(ticket150))return;if(!accepted){bound=wasBound;filename.setEditable(!bound&&initialPath.isEmpty());sent=ready=false;pending=null;status=tr("disconnected");}});});
    }

    @Override public void tick(){
        if(closed||bridge==null)return;
        long generation=bridge.connection150();if(connection150.observe(generation)){boolean hadModel=bound;sent=ready=false;pending=null;lastSequence=0;compactAck149=false;reconnect150=hadModel;if(generation!=0){diagnostic150=false;status=tr("loading");}else if(!diagnostic150)status=tr("disconnected");}
        if(generation==0)return;
        if(sent&&!ready&&pending!=null&&!busy&&System.nanoTime()-loadDeadline149>=0){sent=false;pending=null;fail(new IOException("BLOCKBENCH_LOAD_TIMEOUT"));bridge.close();return;}
        if(!busy&&baseline!=null&&!sent){if(reconnect150){reconnect150=false;reload150();}else load(baseline);}
        if(!busy){var incoming150=bridge.poll150();if(incoming150!=null){var ticket150=connection150.ticket();String raw=incoming150.json();if(incoming150.generation()!=ticket150.generation())return;busy=true;io.execute(()->{try{
            JsonObject message=JsonParser.parseString(raw).getAsJsonObject();if("save".equals(message.get("type").getAsString())){String text=message.get("content").getAsString();WorkspaceModels147.validate(WorkspaceModels147.encode(text));var model=JsonParser.parseString(text).getAsJsonObject();if(!model.has("meta")||!model.get("meta").isJsonObject())throw new IOException("MODEL_FORMAT");}
            minecraft.execute(()->{busy=false;if(!current150(ticket150))return;try{receive(message);}catch(Exception error){sent=ready=false;fail(error);bridge.close();}});
        }catch(Exception error){minecraft.execute(()->{busy=false;if(current150(ticket150)){sent=ready=false;fail(error);bridge.close();}});}});}}
        if(shared!=null&&ready&&!busy&&System.nanoTime()-nextWatch>=0){nextWatch=System.nanoTime()+500_000_000L;busy=true;var ticket150=connection150.ticket();
            shared.watch(path).whenComplete((stat,error)->{busy=false;if(!current150(ticket150))return;if(error!=null){fail(error);return;}if(!stat.hash().equals(baseline.hash())){busy=true;shared.open(path).whenComplete((model,problem)->{busy=false;if(!current150(ticket150))return;if(problem!=null)fail(problem);else load(model);});}else if(stat.editable()!=baseline.editable()){baseline=new WorkspaceModelClient147.Model(baseline.text(),baseline.hash(),stat.editable());var rights=packet("rights");rights.addProperty("editable",stat.editable());send(rights);status=stat.editable()?tr("live"):tr("readonly");diagnostic150=false;}else if(diagnostic150){diagnostic150=false;status=stat.editable()?tr("live"):tr("readonly");}});
        }
    }
    private void reload150(){
        var ticket150=connection150.ticket();busy=true;
        if(shared!=null){
            if(baseline.hash().isEmpty()&&initialPath.isEmpty())shared.stat(path).whenComplete((remote,error)->{busy=false;if(!current150(ticket150))return;if(error!=null){sent=true;fail(error);}else if(!remote.hash().isEmpty()){ready=false;sent=true;status=tr("conflict");}else load(baseline);});
            else shared.open(path).whenComplete((model,error)->{busy=false;if(!current150(ticket150))return;if(error!=null){sent=true;fail(error);}else{baseline=model;load(model);}});
        }else if(document.path().isEmpty()){busy=false;load(baseline);}
        else io.execute(()->{try{var reopened=BlockbenchDocument146.open(storage,project,document.path());minecraft.execute(()->{busy=false;if(!current150(ticket150))return;document=reopened;baseline=new WorkspaceModelClient147.Model(reopened.content(),"",true);load(baseline);});}catch(Exception error){minecraft.execute(()->{busy=false;if(current150(ticket150)){sent=true;fail(error);}});}});
    }
    private void receive(JsonObject q)throws Exception{
        String type=q.get("type").getAsString();if(type.equals("ping"))return;
        if(!connection150.ticket().session().equals(q.get("session").getAsString()))throw new IOException("BRIDGE_SESSION");
        if(type.equals("conflict")){ready=false;status=tr("conflict");return;}
        if(type.equals("loaded")){compactAck149=q.has("protocol149")&&q.get("protocol149").getAsBoolean();if(pending==null)throw new IOException("BRIDGE_STATE");baseline=pending;pending=null;lastSequence=0;ready=true;diagnostic150=false;status=baseline.editable()?tr("live"):tr("readonly");return;}
        if(!type.equals("save"))throw new IOException("BRIDGE_MESSAGE");
        long sequence=q.get("sequence").getAsLong();if(sequence<=lastSequence||sequence<1)throw new IOException("BRIDGE_SEQUENCE");lastSequence=sequence;
        String text=q.get("content").getAsString();
        if(!ready||!baseline.editable()){reject(sequence,tr("readonly"));return;}
        busy=true;status=tr("saving");var ticket150=connection150.ticket();
        if(shared!=null)shared.save(path,baseline.hash(),text).whenComplete((accepted,error)->{busy=false;if(closed)return;if(error!=null){if(current150(ticket150)){fail(error);reject(sequence,status);}}else{baseline=accepted;if(current150(ticket150))ack(sequence,text);}});
        else io.execute(()->{Exception error=null;try{document.save(path,text);}catch(Exception failure){error=failure;}Exception result=error;
            minecraft.execute(()->{busy=false;if(closed)return;if(result!=null){if(current150(ticket150)){fail(result);reject(sequence,status);}}else{baseline=new WorkspaceModelClient147.Model(text,"",true);saved.accept(path,text);if(current150(ticket150))ack(sequence,text);}});
        });
    }
    private void ack(long sequence,String text){var q=packet("saved");q.addProperty("sequence",sequence);if(!compactAck149)q.addProperty("content",text);if(!send(q)){status=tr("disconnected");ready=false;}else{diagnostic150=false;status=tr("live");}}
    private void reject(long sequence,String message){var q=packet("error");q.addProperty("sequence",sequence);q.addProperty("message",message);send(q);ready=false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());super.render(g,mx,my,dt);
        g.drawString(font,title,12,10,0xffffffff,false);g.drawString(font,tr("path"),12,30,0xffbbbbbb,false);g.drawString(font,tr("executable"),12,69,0xffbbbbbb,false);
        int y=162;for(var line:font.split(Component.literal(status),Math.max(20,width-24))){if(y>=height-33)break;g.drawString(font,line,12,y,0xffdddddd,false);y+=12;}
    }
    private void closeResources(){if(closed)return;closed=true;if(bridge!=null)bridge.close();if(shared!=null)shared.close();io.shutdown();}
    @Override public void onClose(){if(busy){temporary=true;minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{temporary=false;minecraft.setScreen(this);if(yes)back();},Component.literal(tr("disconnect_title")),Component.literal(tr("disconnect_body"))){@Override public boolean isPauseScreen(){return false;}});}else back();}
    private void back(){leaving=true;closeResources();minecraft.setScreen(parent);returned.run();}
    @Override public void removed(){if(!temporary){closeResources();if(!leaving)parent.closeSuspendedTool146();}}
    @Override public boolean isPauseScreen(){return false;}
}
