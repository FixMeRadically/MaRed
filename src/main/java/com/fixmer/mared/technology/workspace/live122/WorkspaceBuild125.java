package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.workspace.live122.WorkspaceSources125;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.technology.server.setup.*;
import com.fixmer.mared.technology.workspace.WorkspacePickerScreen112;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit local build of the server-approved input, then the existing paired publication workflow. */
public final class WorkspaceBuild125 extends MaredScreen {
    private final Screen previous;
    private final Map<String,String> context;
    private final WorkspaceClient122 client=new WorkspaceClient122();
    private final GradleBuildRunner runner=new GradleBuildRunner();
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"workspace-build125");t.setDaemon(true);return t;});
    private WorkspaceSources125.Download download;
    private Path project;
    private GradleBuildRunner.Result result;
    private EditBox jdk,wrapper,task;
    private Button start,publish,back,remote152;private boolean remoteChoice152;
    private String buildTask="";
    private String jdkValue=System.getProperty("java.home"),wrapperValue="",taskValue="build",status="IDLE",log="";
    private boolean transferring,working;
    private volatile boolean closed;
    private int scroll;
    public WorkspaceBuild125(Screen previous,Map<String,String> context){super(tr("title"));this.previous=previous;this.context=Map.copyOf(context);var p=DedicatedPanelService75.preferences();if(p.has("workspaceJdk125"))jdkValue=p.get("workspaceJdk125").getAsString();if(p.has("workspaceWrapper125"))wrapperValue=p.get("workspaceWrapper125").getAsString();}
    private static Component tr(String key,Object...args){return Component.translatable("mared.workspace125."+key,args);}
    private void collect(){if(jdk!=null){jdkValue=jdk.getValue().trim();wrapperValue=wrapper.getValue().trim();taskValue=task.getValue().trim();}}
    private EditBox field(int y,String key,String value){var box=addRenderableWidget(new EditBox(font,140,y,Math.max(1,width-150),20,tr(key)));box.setMaxLength(2048);box.setValue(value);return box;}
    private Button button(int x,int y,int w,String key,Runnable action){return addRenderableWidget(WorkspaceButton131.builder(tr(key),b->action.run()).bounds(x,y,Math.max(1,w),20).build());}
    @Override protected void init(){collect();client.attach();if(width<460||height<260){back=button(10,height-30,width-20,"back",this::onClose);return;}jdk=field(44,"jdk",jdkValue);wrapper=field(70,"wrapper",wrapperValue);wrapper.setHint(tr("wrapper_auto126"));task=field(96,"task",taskValue);int w=(width-28)/3;start=button(10,height-30,w,"start",()->{collect();minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)begin();},tr("start"),tr("confirm")));});publish=button(14+w,height-30,w,"publish",()->{remoteChoice152=false;selectArtifact();});remote152=addRenderableWidget(WorkspaceButton131.builder(Component.translatable("mared.scripts.remote_test152"),button->{remoteChoice152=true;selectArtifact();}).bounds(10,height-56,width-20,20).build());back=button(18+2*w,height-30,w,"back",this::onClose);}
    boolean running131(){return busy();}
    private boolean busy(){return working||transferring||runner.busy()||client.busy();}
    private void fail(String text){working=transferring=false;status=text;}
    private void send(String op,Map<String,String> fields,java.util.function.Consumer<Map<String,String>> done){if(!client.connected()){fail("DISCONNECTED");return;}client.send(WorkspaceClient122.request(op,context.get("project"),fields),d->{if(!d.getOrDefault("result","").equals("OK")){fail(d.getOrDefault("result","INVALID"));return;}if(!Objects.equals(context.get("store"),d.get("store"))||!Objects.equals(context.get("actor"),d.get("actor"))){fail("IDENTITY_CHANGED");return;}try{done.accept(d);}catch(Exception e){fail(e.toString());}});}
    private void begin(){
        if(busy()||closed)return;result=null;project=null;download=null;log="";
        try{BuildTools.javaHome(jdkValue);if(!wrapperValue.isBlank())BuildTools.wrapperJar(wrapperValue);if(!GradleBuildRunner.validTask(taskValue))throw new IllegalArgumentException("TASK");}catch(Exception e){fail(e.toString());return;}
        var prefs=DedicatedPanelService75.preferences();prefs.addProperty("workspaceJdk125",jdkValue);prefs.addProperty("workspaceWrapper125",wrapperValue);DedicatedPanelService75.save(prefs);
        buildTask=taskValue;transferring=true;status="DOWNLOADING";send("BUILD_SOURCE",Map.of(),d->{download=new WorkspaceSources125.Download(d.get("sourceHash"),d.get("sourceFiles"));next();});
    }
    private void next(){
        if(closed)return;var entry=download.next();if(entry==null){
            send("BUILD_SOURCE",Map.of(),d->{if(!download.hash().equals(d.get("sourceHash"))){fail("STALE_RELEASE");return;}transferring=false;working=true;status="MATERIALIZING";String selectedJdk=jdkValue,selectedWrapper=wrapperValue,selectedTask=buildTask;
                io.execute(()->{try{Path p=download.materialize(Minecraft.getInstance().gameDirectory.toPath().resolve("genesis/workspace-builds125"));var tools=new GradleBuildRunner.Settings(BuildTools.javaHome(selectedJdk),selectedWrapper.isBlank()?com.fixmer.genesis.technology.workspace.live122.WorkspaceProject126.wrapper(Minecraft.getInstance().gameDirectory.toPath().resolve("config/mared/workspace-wrapper126")):BuildTools.wrapperJar(selectedWrapper));Minecraft.getInstance().execute(()->{working=false;if(closed)return;project=p;status="BUILDING";if(!runner.start(p,tools,selectedTask))fail("BUILD_BUSY");});}catch(Exception e){Minecraft.getInstance().execute(()->fail(e.toString()));}});
            });return;
        }
        int offset=download.offset();send("BUILD_CHUNK",Map.of("hash",download.hash(),"path",entry.path(),"offset",Integer.toString(offset)),d->{if(!download.hash().equals(d.get("sourceHash"))||!Integer.toString(offset).equals(d.get("offset")))throw new IllegalArgumentException("CHUNK");download.accept(d.get("path"),offset,d.get("chunk"));next();});
    }
    private void selectArtifact(){if(busy()||result==null)return;var items=new ArrayList<WorkspacePickerScreen112.Item>();for(var a:result.inventory().artifacts())if(Set.of("mod","mod_embedded").contains(a.kind()))items.add(new WorkspacePickerScreen112.Item(a.path(),()->snapshot(a)));if(items.isEmpty()){fail("NO_MOD_ARTIFACT");return;}minecraft.setScreen(new WorkspacePickerScreen112(this,tr("artifact"),items));}
    private void snapshot(BuildArtifacts.Artifact artifact){
        if(busy()||closed)return;transferring=true;status="VERIFYING";
        send("PUBLISH_CHECK131",Map.of("hash",download.hash()),d->{if(!download.hash().equals(d.get("sourceHash"))){fail("STALE_RELEASE");return;}
            transferring=false;working=true;io.execute(()->{try{for(var f:download.verified().values())if(!Arrays.equals(f.bytes(),Files.readAllBytes(project.resolve(f.path()))))throw new IllegalStateException("APPROVED_SOURCE_CHANGED");var snapshot=BuildSnapshot84.create(project,context.get("project"),buildTask,result,artifact,project.getParent().resolve("exports"),()->closed);Minecraft.getInstance().execute(()->{working=false;if(!closed){status="SNAPSHOT_READY";minecraft.setScreen(remoteChoice152?new RemoteTestScreen152(this,snapshot):new AutoDeploymentScreen156(this,snapshot));}});}catch(Exception e){Minecraft.getInstance().execute(()->fail(e.toString()));}});
        });
    }
    @Override public void tick(){client.tick();if(transferring&&!client.busy()&&!client.connected()){fail("DISCONNECTED");}else if(transferring&&!client.busy()&&Set.of("UNKNOWN","INVALID","IDENTITY_CHANGED","STORE_CHANGED").contains(client.result))fail(client.result);String output=runner.drainOutput();if(!output.isEmpty()){log+=output;if(log.length()>256000)log=log.substring(log.length()-256000);}runner.poll(r->{result=r;status=r.status().name()+" / "+r.exitCode();log=r.output();});if(start!=null){boolean busy=busy();start.active=!busy&&client.connected();publish.active=!busy&&result!=null&&result.status()==GradleBuildRunner.Status.SUCCESS&&Boolean.parseBoolean(context.getOrDefault("canPublish131","true"));remote152.active=publish.active&&RemoteTestScreen152.available();back.active=!working;for(var f:new EditBox[]{jdk,wrapper,task})f.setEditable(!busy);}}
    @Override public void removed(){collect();if(transferring||working||runner.busy()){closed=true;transferring=false;runner.close();io.shutdown();}client.detach();}
    @Override public void onClose(){if(working)return;closed=true;transferring=false;runner.close();io.shutdown();minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,scroll+(int)(-dy*3));return true;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());g.drawCenteredString(font,title,width/2,10,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());if(jdk!=null&&width>=460&&height>=260){g.drawString(font,tr("jdk"),10,50,0xFFCCD0DC,false);g.drawString(font,tr("wrapper"),10,76,0xFFCCD0DC,false);g.drawString(font,tr("task"),10,102,0xFFCCD0DC,false);g.drawString(font,font.plainSubstrByWidth(status,width-20),10,124,0xFFE4BC78,false);var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();lines.addAll(font.split(tr("help"),width-20));if(download!=null)lines.addAll(font.split(Component.literal(download.hash()),width-20));for(String line:log.split("\n"))lines.addAll(font.split(Component.literal(line),width-20));int count=Math.max(1,(height-210)/12);scroll=Math.min(scroll,Math.max(0,lines.size()-count));g.enableScissor(10,144,width-10,height-62);for(int n=0;n<count&&scroll+n<lines.size();n++)g.drawString(font,lines.get(scroll+n),10,144+n*12,0xFFCCD0DC,false);g.disableScissor();}super.render(g,mx,my,dt);}
}
