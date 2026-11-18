package com.fixmer.mared.technology.server.setup;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.genesis.technology.scripts.BuildSnapshot84;
import com.fixmer.mared.technology.workspace.live122.WorkspaceClient122;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.screens.*;import net.minecraft.network.chat.Component;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;

/** Upload to the connected dedicated server, review its inventory, then request its supervised apply/restart. */
public final class RemoteTestScreen152 extends MaredScreen {
    private final Screen previous;private final BuildSnapshot84.Snapshot snapshot;private final WorkspaceClient122 client=new WorkspaceClient122();
    private final ExecutorService io=Executors.newSingleThreadExecutor(run->{Thread thread=new Thread(run,"MaRed-remote-test152");thread.setDaemon(true);return thread;});
    private CompletableFuture<byte[]> preparation;private byte[] bytes;private int offset;private String token="",status="",target="",hash="",generation="";private boolean started,reviewed,applied,failed;private Button apply;
    public RemoteTestScreen152(Screen previous,BuildSnapshot84.Snapshot snapshot){super(tr("title"));this.previous=previous;this.snapshot=snapshot;}
    private static Component tr(String key,Object...args){return Component.translatable("mared.remote152."+key,args);}
    public static String unavailable154(){
        var mc=Minecraft.getInstance();
        if(mc.getConnection()==null||mc.getSingleplayerServer()!=null)return "mared.remote152.need_connection154";
        if(!DedicatedPanelService75.hasRemote())return "mared.remote152.need_panel154";
        if(com.fixmer.mared.technology.permissions.MaopClient153.required()&&!com.fixmer.mared.technology.permissions.MaopClient153.state().equals("READY"))return "mared.remote152.need_device154";
        if(!"true".equals(DedicatedPanelService75.remote().get("test152")))return "mared.remote152.need_controller154";
        if(!DedicatedPanelService75.canRemote88("STAGE")||!DedicatedPanelService75.canRemote88("RESTART")||!DedicatedPanelService75.canRemote88("COMMIT"))return "mared.remote152.need_rights154";
        return "";
    }
    public static boolean available(){return unavailable154().isEmpty();}
    @Override protected void init(){client.attach();int w=Math.max(1,Math.min(560,width-24)),x=(width-w)/2;
        apply=addRenderableWidget(Button.builder(tr("apply"),button->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)apply();},title,tr("confirm",target,hash,generation)))).bounds(x,Math.max(0,height-52),w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),button->onClose()).bounds(x,Math.max(0,height-26),w,20).build());
        if(!started){started=true;status=tr("preparing").getString();preparation=CompletableFuture.supplyAsync(()->{try{var checked=BuildSnapshot84.verify(snapshot.directory(),()->Thread.currentThread().isInterrupted());if(!checked.seal().equals(snapshot.seal()))throw new IllegalStateException("BUILD_CHANGED");return Files.readAllBytes(checked.directory().resolve("artifact.jar"));}catch(Exception failure){throw new CompletionException(failure);}},io).whenComplete((value,error)->io.shutdown());}
    }
    private void send(String op,Map<String,String> fields,java.util.function.Consumer<Map<String,String>> complete){client.send(WorkspaceClient122.request(op,"",fields),answer->{if(!"OK".equals(answer.get("result"))){failed=true;status=answer.getOrDefault("result","UNKNOWN");}else complete.accept(answer);});}
    @Override public void tick(){client.tick();if(apply!=null)apply.active=reviewed&&!failed&&!applied&&client.readyForSend152();
        if(failed||applied||client.busy())return;
        if(preparation!=null&&preparation.isDone()){try{bytes=preparation.join();preparation=null;send("TEST_BEGIN152",Map.of("size",Integer.toString(bytes.length),"hash",snapshot.artifact().sha256()),answer->{token=answer.get("token152");status=tr("uploading").getString();});}catch(Exception error){failed=true;status="BUILD_CHANGED";}return;}
        if(token.isEmpty()||reviewed)return;
        if(bytes!=null&&offset<bytes.length){int count=Math.min(12288,bytes.length-offset);int next=offset+count;String chunk=Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,offset,next));send("TEST_PUT152",Map.of("token",token,"offset",Integer.toString(offset),"chunk",chunk),answer->{if(Integer.parseInt(answer.get("offset152"))!=next){failed=true;status="REMOTE152_OFFSET";}else offset=next;});return;}
        bytes=null;send("TEST_REVIEW152",Map.of("token",token),answer->{target=answer.get("target152");hash=answer.get("hash152");if(!snapshot.artifact().sha256().equals(hash)){failed=true;status="AUTH153_PREVIEW_CHANGED";return;}generation=answer.get("generation152");reviewed=true;status=tr("ready").getString();});
    }
    private void apply(){if(!reviewed||applied||failed||!client.readyForSend152())return;
        var request=WorkspaceClient122.request("TEST_APPLY152","",com.fixmer.mared.technology.permissions.MaopClient153.required()?Map.of("token",token,"hash153",snapshot.artifact().sha256(),"target153",target,"generation153",generation):Map.of("token",token));UUID id=UUID.fromString(request.get("request"));var ticket=ServerReturnClient108.begin(id);
        if(ticket==null){status="REMOTE152_RETURN_BUSY";return;}applied=true;status=tr("applying").getString();
        client.send(request,answer->{String result=answer.getOrDefault("result","UNKNOWN");boolean accepted="OK".equals(result)&&"true".equals(answer.get("queued152"));boolean unknown=Set.of("UNKNOWN","DISCONNECTED").contains(result);ServerReturnClient108.answer(id,accepted||unknown);status=accepted?tr("queued").getString():result;if(!accepted&&!unknown){applied=false;failed=true;}});
    }
    @Override public void removed(){} // ConfirmScreen temporarily owns the UI; transport remains attached.
    @Override public void onClose(){if(applied&&client.busy())return;if(!applied&&!token.isEmpty()&&!client.busy())client.send(WorkspaceClient122.request("TEST_CANCEL152","",Map.of("token",token)),answer->{});client.detach();io.shutdownNow();bytes=null;minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg());int x=12,y=12,w=Math.max(1,width-24);g.drawString(font,title,x,y,0xffffffff,false);y+=24;
        String[] lines={status,snapshot.projectId()+" · "+snapshot.task(),offset+" / "+snapshot.artifact().size()+" B",target,hash,tr("help").getString()};
        g.enableScissor(x,32,width-12,Math.max(32,height-58));try{for(String line:lines)for(var text:font.split(Component.literal(line),w)){g.drawString(font,text,x,y,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(),false);y+=font.lineHeight+4;}}finally{g.disableScissor();}super.render(g,mx,my,dt);
    }
}
