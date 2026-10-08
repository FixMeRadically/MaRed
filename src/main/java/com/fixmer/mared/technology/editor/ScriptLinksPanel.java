package com.fixmer.mared.technology.editor;
import com.fixmer.genesis.technology.links.*;
import com.fixmer.genesis.technology.links.ScriptExports.*;
import com.fixmer.genesis.technology.links.ScriptLinks.*;
import com.fixmer.mared.technology.links.*;
import com.fixmer.mared.technology.ai.GenesisAiRuntime;
import com.fixmer.mared.technology.runtime.ScriptDispatch;
import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.studio.events.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import java.util.*;
import java.util.concurrent.*;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;
/** Shared function browser and one concrete AI editor. Expressions remain manual until parameter forms. */
public final class ScriptLinksPanel extends MaredComponent implements NarratableComponent {
    // Input operates on the same immutable generation that was last drawn.
    private ScriptLinkService.Snapshot view;
    private ScriptLinkService.Snapshot displayed() {
        return view!=null?view:service.snapshot();
    }
    private record Button(int x,int y,int width,String label,Runnable action) {
    }
    private final StudioEventBus bus;
    private final Runnable openCode;
    private final ScriptLinkService service=ScriptLinkService.get();
    private final List<Button> buttons=new ArrayList<>();
    private final List<EditBox> fields=new ArrayList<>();
    private EditBox filter;
    private boolean ai,dirty,loading,enabled,formVisible;
    private Kind kind;
    private int scroll,split;
    private long revision=-1;
    private Reference selectedExport,conditionRef,actionRef;
    private UUID selectedProfile,conditionId,actionId;
    private String projectBase;
    private String message="";
    private CompletableFuture<?> task;
    private String taskLabel;
    private boolean taskSaves;
    public ScriptLinksPanel(StudioEventBus bus,Runnable openCode) {
        this.bus=bus;
        this.openCode=openCode;
    }
    public void setAi(boolean ai) {
        this.ai=ai;
        scroll=0;
        cancelInteraction();
    }
    public void cancelInteraction() {
        if(filter!=null)filter.setFocused(false);
        for(var field:fields)field.setFocused(false);
    }
    @Override public boolean focusable() {
        return true;
    }
    @Override public void onFocusLost() {
        cancelInteraction();
    }
    @Override public Component narrationText() {
        return Component.literal(EditorText.translate(ai?"AI LINKS":"SCRIPT EXPORTS"));
    }
    private boolean busy() {
        return task!=null;
    }
    private void run(CompletableFuture<?> future,String label,boolean saves) {
        task=future;
        taskLabel=label;
        taskSaves=saves;
        message=label;
    }
    private void advance() {
        if(task!=null&&task.isDone()) {
            try {
                Object result=task.join();
                message=result instanceof String text?text:taskLabel+" · OK";
                if(taskSaves)dirty=false;
            }
            catch(CompletionException error) {
                message=Objects.toString(error.getCause().getMessage(),"Operation failed");
            }
            finally {
                task=null;
            }
        }
    }
    private void init(MaredRenderContext ctx) {
        if(filter!=null)return;
        filter=new EditBox(ctx.font(),0,0,100,12,Component.literal("Search"));
        filter.setBordered(false);
        filter.setMaxLength(128);
        filter.setResponder(s->scroll=0);
        for(int i=0;i<6;i++) {
            var field=new EditBox(ctx.font(),0,0,100,12,Component.literal("AI field"));
            field.setBordered(false);
            field.setMaxLength(i>=4?8192:128);
            field.setResponder(value-> {
                if(!loading)dirty=true;
            }
            );
            fields.add(field);
        }
    }
    private List<CapabilityRegistry.Entry<ScriptLibrary.Target>> exports() {
        String query=filter==null?"":filter.getValue().toLowerCase(Locale.ROOT);
        return displayed().catalog().all(kind).stream().filter(e->(e.symbol().title()+" "+e.symbol().source()+" "+e.symbol().function()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private List<AiProfile> profiles() {
        String query=filter==null?"":filter.getValue().toLowerCase(Locale.ROOT);
        return displayed().project().profiles().stream().filter(p->(p.title()+" "+p.tag()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private AiProfile saved() {
        return displayed().project().profiles().stream().filter(p->p.id().equals(selectedProfile)).findFirst().orElse(null);
    }
    private void load(AiProfile profile) {
        loading=true;
        selectedProfile=profile.id();
        conditionId=profile.condition();
        actionId=profile.action();
        enabled=profile.enabled();
        projectBase=displayed().projectText();
        var project=displayed().project();
        var c=project.binding(conditionId);
        var a=project.binding(actionId);
        conditionRef=c.reference();
        actionRef=a.reference();
        String[] values= {
            profile.title(),profile.tag(),Integer.toString(profile.interval()),Double.toString(profile.range()),ScriptLinkCodec.arguments(c.arguments()),ScriptLinkCodec.arguments(a.arguments())
        }
        ;
        for(int i=0;i<6;i++)fields.get(i).setValue(values[i]);
        loading=false;
        dirty=false;
    }
    private void newProfile() {
        if(dirty) {
            message="Save or discard edits first";
            return;
        }
        loading=true;
        selectedProfile=UUID.randomUUID();
        conditionId=UUID.randomUUID();
        actionId=UUID.randomUUID();
        enabled=false;
        projectBase=displayed().projectText();
        conditionRef=null;
        actionRef=null;
        String[] values= {
            "New AI profile","genesis.ai."+selectedProfile.toString().substring(0,8),"10","32","{}","{}"
        }
        ;
        for(int i=0;i<6;i++)fields.get(i).setValue(values[i]);
        loading=false;
        dirty=true;
    }
    private void choose(boolean condition) {
        if(selectedProfile==null) {
            message="Create or select a profile first";
            return;
        }
        var list=displayed().catalog().all(condition?Kind.CONDITION:Kind.ACTION);
        if(list.isEmpty()) {
            message="No exported function of this kind";
            return;
        }
        var old=condition?conditionRef:actionRef;
        int index=-1;
        for(int i=0;i<list.size();i++)if(list.get(i).symbol().reference().equals(old))index=i;
        var ref=list.get((index+1)%list.size()).symbol().reference();
        if(condition)conditionRef=ref;
        else actionRef=ref;
        dirty=true;
    }
    private void save() {
        try {
            if(conditionRef==null||actionRef==null||selectedProfile==null)throw new IllegalArgumentException("Select condition and action");
            var p=new AiProfile(selectedProfile,fields.get(0).getValue(),fields.get(1).getValue(),enabled,Integer.parseInt(fields.get(2).getValue()),Double.parseDouble(fields.get(3).getValue()),conditionId,actionId);
            var c=new Binding(conditionId,p.consumer(),"condition",conditionRef,ScriptLinkCodec.arguments(fields.get(4).getValue()));
            var a=new Binding(actionId,p.consumer(),"action",actionRef,ScriptLinkCodec.arguments(fields.get(5).getValue()));
            run(service.put(p,c,a,projectBase),"Save profile",true);
        }
        catch(RuntimeException error) {
            message=Objects.toString(error.getMessage(),"Invalid profile");
        }
    }
    private void discard() {
        var profile=saved();
        if(profile!=null)load(profile);
        else {
            selectedProfile=null;
            dirty=false;
        }
    }
    private String functionLabel(Reference ref) {
        if(ref==null)return "Choose function >";
        var entry=displayed().catalog().entries().get(ref);
        return entry==null?"Missing: "+ref.export().toString().substring(0,8):entry.symbol().source()+" :: "+entry.symbol().function();
    }
    @Override protected void safeRender(MaredRenderContext ctx) {
        init(ctx);
        advance();
        view=service.snapshot();
        var snapshot=view;
        if(revision!=snapshot.revision()) {
            revision=snapshot.revision();
            if(!dirty) {
                var p=saved();
                if(p==null&&!snapshot.project().profiles().isEmpty())p=snapshot.project().profiles().get(0);
                if(p!=null)load(p);
                else selectedProfile=null;
            }
        }
        formVisible=false;
        var g=ctx.graphics();
        g.fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),panel());
        buttons.clear();
        button(ctx,bounds.x()+4,bounds.y()+3,66,"Exports",()-> {
            ai=false;
            scroll=0;
            cancelInteraction();
        }
        );
        button(ctx,bounds.x()+72,bounds.y()+3,42,"AI",()-> {
            ai=true;
            scroll=0;
            cancelInteraction();
        }
        );
        button(ctx,bounds.right()-57,bounds.y()+3,53,"Refresh",service::refresh);
        split=bounds.x()+Math.max(86,Math.min(180,bounds.width()/3));
        filter.setX(bounds.x()+7);
        filter.setY(bounds.y()+24);
        filter.setWidth(Math.max(1,split-bounds.x()-14));
        filter.setTextColor(text());
        filter.render(g,ctx.mouseX(),ctx.mouseY(),0);
        if(!ai) {
            String label=kind==null?"All kinds":kind.name();
            button(ctx,bounds.x()+4,bounds.y()+40,Math.max(1,split-bounds.x()-8),label,()-> {
                kind=kind==null?Kind.ACTION:kind==Kind.ACTION?Kind.CONDITION:null;
                scroll=0;
            }
            );
        }
        else {
            button(ctx,bounds.x()+4,bounds.y()+40,42,"New",this::newProfile);
            button(ctx,bounds.x()+48,bounds.y()+40,Math.max(1,split-bounds.x()-52),"Demo",()->run(service.installDemo(),"Install demo",false));
        }
        var profileRows=ai?profiles():List.<AiProfile>of();
        var exportRows=ai?List.<CapabilityRegistry.Entry<ScriptLibrary.Target>>of():exports();
        int count=ai?profileRows.size():exportRows.size(),visible=Math.max(0,(bounds.height()-89)/22);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,count-visible)));
        for(int i=scroll;i<Math.min(count,scroll+visible);i++) {
            int y=bounds.y()+59+(i-scroll)*22;
            String title,sub;
            boolean selected;
            if(ai) {
                var p=profileRows.get(i);
                title=p.title();
                sub=p.enabled()?"ON":"OFF";
                selected=p.id().equals(selectedProfile);
            }
            else {
                var e=exportRows.get(i);
                title=e.symbol().function();
                sub=e.symbol().reference().kind().name()+" · "+e.symbol().source();
                selected=e.symbol().reference().equals(selectedExport);
            }
            if(selected)g.fill(bounds.x()+3,y,split-3,y+21,raised());
            g.drawString(ctx.font(),ctx.font().plainSubstrByWidth(title,split-bounds.x()-16),bounds.x()+8,y+2,selected?text():dim(),false);
            g.drawString(ctx.font(),ctx.font().plainSubstrByWidth(sub,split-bounds.x()-16),bounds.x()+8,y+12,dim(),false);
        }
        g.fill(split,bounds.y()+21,split+1,bounds.bottom()-21,edge());
        if(ai)renderProfile(ctx);
        else renderExport(ctx);
        String status=busy()?taskLabel:message;
        if(status.isEmpty())status=!snapshot.loaded()?"Loading links…":!snapshot.errors().isEmpty()?snapshot.errors().entrySet().iterator().next().toString():count+" entries";
        g.drawString(ctx.font(),ctx.font().plainSubstrByWidth(EditorText.translate(status),Math.max(0,bounds.width()-14)),bounds.x()+7,bounds.bottom()-15,dim(),false);
    }
    private void renderExport(MaredRenderContext ctx) {
        var entry=selectedExport==null?null:displayed().catalog().entries().get(selectedExport);
        int x=split+9,y=bounds.y()+27,w=bounds.right()-x-8;
        if(entry==null) {
            line(ctx,"SCRIPT EXPORTS",x,y,w,text());
            line(ctx,"One function · many systems",x,y+20,w,dim());
            line(ctx,"Select a function to inspect references",x,y+37,w,dim());
            return;
        }
        var s=entry.symbol();
        line(ctx,s.title(),x,y,w,text());
        line(ctx,s.source()+" :: "+s.function()+"("+String.join(", ",s.parameters())+")",x,y+19,w,dim());
        line(ctx,s.reference().kind().name(),x,y+36,w,accent());
        line(ctx,"Module: "+s.reference().module(),x,y+55,w,dim());
        line(ctx,"Export: "+s.reference().export(),x,y+70,w,dim());
        button(ctx,x,y+89,Math.min(88,w),"Open Script",()-> {
            bus.publish(new StudioEvents.FileOpenedEvent(s.source()));
            openCode.run();
        }
        );
        button(ctx,x+92,y+89,Math.max(1,Math.min(90,w-92)),"Copy reference",()->Minecraft.getInstance().keyboardHandler.setClipboard(s.reference().module()+"/"+s.reference().export()));
        var usages=displayed().index().usages(s.reference());
        line(ctx,"Usages: "+usages.size(),x,y+115,w,text());
        int yy=y+132;
        for(var use:usages) {
            if(yy>bounds.bottom()-31)break;
            line(ctx,use.consumer()+" · "+use.slot(),x,yy,w,dim());
            yy+=14;
        }
    }
    private void renderProfile(MaredRenderContext ctx) {
        int x=split+9,y=bounds.y()+24,w=Math.max(1,bounds.right()-x-8);
        if(selectedProfile==null) {
            line(ctx,"Create profile or install the demo",x,y,w,text());
            return;
        }
        if(w<130||bounds.height()<270) {
            line(ctx,"Widen editor to edit AI",x,y,w,dim());
            return;
        }
        formVisible=true;
        String[] labels= {
            "Title","Entity tag","Interval · ticks","Search radius","Condition args · JSON expressions","Action args · JSON expressions"
        }
        ;
        for(int i=0;i<4;i++) {
            renderField(ctx,fields.get(i),labels[i],x,y,w);
            y+=27;
        }
        button(ctx,x,y,w,"IF · "+functionLabel(conditionRef),()->choose(true));
        y+=19;
        renderField(ctx,fields.get(4),labels[4],x,y,w);
        y+=27;
        button(ctx,x,y,w,"DO · "+functionLabel(actionRef),()->choose(false));
        y+=19;
        renderField(ctx,fields.get(5),labels[5],x,y,w);
        y+=28;
        button(ctx,x,y,42,"Save",this::save);
        button(ctx,x+44,y,Math.min(70,Math.max(1,w-44)),enabled?"ON":"OFF",()-> {
            enabled=!enabled;
            dirty=true;
        }
        );
        button(ctx,x+117,y,Math.min(62,Math.max(1,w-117)),"Discard",this::discard);
        y+=19;
        if(y+15<bounds.bottom()-21) {
            button(ctx,x,y,60,"Attach",()->tag(true));
            button(ctx,x+63,y,60,"Detach",()->tag(false));
            button(ctx,x+126,y,Math.min(60,Math.max(1,w-126)),"Delete",()-> {
                var p=saved();
                if(dirty) {
                    message="Save or discard edits first";
                    return;
                }
                if(p!=null)run(service.remove(p.id(),projectBase),"Delete profile",true);
            }
            );
            y+=18;
        }
        var p=saved();
        if(p!=null&&y+12<bounds.bottom()-21) {
            String error=displayed().problem(p);
            var status=GenesisAiRuntime.status(Minecraft.getInstance().getSingleplayerServer(),p.id());
            line(ctx,error!=null?error:status.attached()+" mobs · "+status.running()+" running · "+status.errors()+" errors",x,y,w,dim());
        }
        if(dirty)line(ctx,"UNSAVED PROFILE",x,bounds.y()+9,Math.max(1,w-60),accent());
    }
    private void renderField(MaredRenderContext ctx,EditBox field,String label,int x,int y,int w) {
        line(ctx,label,x,y,w,dim());
        field.setX(x);
        field.setY(y+11);
        field.setWidth(w);
        field.setTextColor(text());
        field.render(ctx.graphics(),ctx.mouseX(),ctx.mouseY(),0);
    }
    private void line(MaredRenderContext ctx,String text,int x,int y,int width,int color) {
        ctx.graphics().drawString(ctx.font(),ctx.font().plainSubstrByWidth(EditorText.translate(text),Math.max(0,width)),x,y,color,false);
    }
    private void button(MaredRenderContext ctx,int x,int y,int w,String label,Runnable action) {
        if(w<1||y+15>bounds.bottom()-19)return;
        boolean hover=ctx.mouseX()>=x&&ctx.mouseX()<x+w&&ctx.mouseY()>=y&&ctx.mouseY()<y+15;
        if(hover)ctx.graphics().fill(x,y,x+w,y+15,raised());
        line(ctx,label,x+3,y+4,w-6,busy()?dim():text());
        buttons.add(new Button(x,y,w,label,action));
    }
    private void tag(boolean attach) {
        var p=saved();
        if(p==null||dirty) {
            message="Save the profile first";
            return;
        }
        var mc=Minecraft.getInstance();
        var server=mc.getSingleplayerServer();
        if(server==null||mc.player==null) {
            message="Attach requires an integrated server · use server tags in multiplayer";
            return;
        }
        UUID playerId=mc.player.getUUID();
        var future=new CompletableFuture<String>();
        try {
            if(!ScriptDispatch.submit(server,()-> {
                try {
                    var player=server.getPlayerList().getPlayer(playerId);
                    if(player==null||!player.createCommandSourceStack().hasPermission(2))throw new IllegalStateException("Operator permission required");
                    var mobs=player.serverLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(8),m->m.isAlive()&&(attach||m.getTags().contains(p.tag())));
                    var mob=mobs.stream().min(Comparator.comparingDouble(m->m.distanceToSqr(player))).orElseThrow(()->new IllegalStateException("No mob within 8 blocks"));
                    if(attach) {
                        if(mob.getTags().stream().anyMatch(t->t.startsWith("genesis.ai.")&&!t.equals(p.tag())))throw new IllegalStateException("Mob already has another AI profile");
                        mob.addTag(p.tag());
                    }
                    else mob.removeTag(p.tag());
                    future.complete(attach?"Profile attached":"Profile detached");
                }
                catch(Exception error) {
                    future.completeExceptionally(error);
                }
            }
            ))future.completeExceptionally(new IllegalStateException("Server queue full"));
        }
        catch(RuntimeException error) {
            future.completeExceptionally(error);
        }
        run(future,attach?"Attach profile":"Detach profile",false);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(!bounds.contains(mx,my))return false;
        if(button!=0)return true;
        requestFocus();
        if(busy())return true;
        for(var b:buttons)if(mx>=b.x&&mx<b.x+b.width&&my>=b.y&&my<b.y+15) {
            b.action.run();
            return true;
        }
        cancelInteraction();
        if(filter!=null&&filter.isMouseOver(mx,my)) {
            filter.setFocused(true);
            return filter.mouseClicked(mx,my,button);
        }
        if(ai&&formVisible)for(var f:fields)if(f.isMouseOver(mx,my)) {
            f.setFocused(true);
            return f.mouseClicked(mx,my,button);
        }
        if(mx<split&&my>=bounds.y()+59&&my<bounds.bottom()-22) {
            int index=scroll+(int)((my-bounds.y()-59)/22);
            if(ai) {
                var rows=profiles();
                if(index<rows.size()) {
                    if(dirty)message="Save or discard edits first";
                    else load(rows.get(index));
                }
            }
            else {
                var rows=exports();
                if(index<rows.size())selectedExport=rows.get(index).symbol().reference();
            }
            return true;
        }
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        if(!bounds.contains(mx,my))return false;
        if(mx<split)scroll=Math.max(0,scroll+(dy<0?3:dy>0?-3:0));
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(busy())return true;
        if(key==256) {
            cancelInteraction();
            return false;
        }
        if(ai&&key==83&&(mods&2)!=0) {
            save();
            return true;
        }
        if(filter!=null&&filter.isFocused())return filter.keyPressed(key,scan,mods);
        if(ai&&formVisible)for(var f:fields)if(f.isFocused())return f.keyPressed(key,scan,mods);
        return false;
    }
    @Override public boolean charTyped(char c,int mods) {
        if(busy())return true;
        if(filter!=null&&filter.isFocused())return filter.charTyped(c,mods);
        if(ai&&formVisible)for(var f:fields)if(f.isFocused())return f.charTyped(c,mods);
        return false;
    }
}
