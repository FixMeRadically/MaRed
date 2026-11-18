package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.mared.Mared;
import com.fixmer.genesis.technology.workspace.live122.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;import java.util.function.Consumer;

/** Inbox polling and HUD notifications run for ordinary players, outside the developer/workspace UI. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class WorkspaceSocial131 {
    private WorkspaceSocial131(){}
    private static Object connection;private static WorkspaceClient122 client;private static long nextPoll,toastUntil;private static String[] toast;
    private static List<String[]> inbox=List.of();private static final Set<String> seen=new HashSet<>();private static long transferGeneration;private static boolean transferring;private static WorkspaceJoinScreen131 activeJoin;private static Map<String,String> awaitingAccept;private static Screen acceptPrevious;
    static List<String[]> inbox(){return inbox;}
    static boolean ready(){return client!=null&&client.connected();}
    static void call(String op,String project,Map<String,String> fields,Consumer<Map<String,String>> done){if(ready())client.send(WorkspaceClient122.request(op,project,fields),d->{if(!"OK".equals(d.get("result")))error(d.getOrDefault("result","INVALID"));done.accept(d);});else{error("DISCONNECTED");done.accept(Map.of("result","DISCONNECTED"));}}static void error(String code){toast=new String[]{"","","ERROR","",WorkspaceScreen122.tr("status."+code).getString(),"WorkSpace",""};toastUntil=System.nanoTime()+12_000_000_000L;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();Object current=mc.getConnection();WorkspaceClient122.detachDisconnected135(current);if(connection!=current){if(client!=null)client.detach();client=null;connection=current;inbox=List.of();seen.clear();toast=null;transferring=false;awaitingAccept=null;acceptPrevious=null;if(activeJoin!=null)activeJoin.fail("DISCONNECTED");activeJoin=null;nextPoll=0;if(current!=null){client=new WorkspaceClient122();client.attach();}}
        if(client==null)return;client.tick();if(!client.busy()&&Set.of("UNKNOWN","INVALID","DISCONNECTED","IDENTITY_CHANGED","STORE_CHANGED").contains(client.result)){if(transferring){transferring=false;if(activeJoin!=null)activeJoin.fail(client.result);}if(awaitingAccept!=null&&client.connected()){var recovery=awaitingAccept;Screen parent=acceptPrevious;awaitingAccept=null;call("VIEW",recovery.get("project"),Map.of(),d->{if("OK".equals(d.get("result")))download(d,parent);});return;}}long now=System.nanoTime();if(!client.connected()||client.busy()||transferring||nextPoll!=0&&now-nextPoll<0)return;nextPoll=now+2_000_000_000L;
        call("INBOX131","",Map.of(),d->{if(!"OK".equals(d.get("result")))return;inbox=d.getOrDefault("inbox131","").lines().map(row->row.split("\\|",7)).filter(p->p.length==7).toList();seen.retainAll(inbox.stream().map(row->row[0]).toList());if(toast!=null&&!toast[0].isEmpty()&&inbox.stream().noneMatch(row->row[0].equals(toast[0])))toast=null;for(var row:inbox)if(seen.add(row[0])){toast=row;toastUntil=System.nanoTime()+12_000_000_000L;}});
    }
    private static void preserve(Screen screen){if(screen instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell)shell.suspendForSettings();}
    static void openInbox(){Minecraft mc=Minecraft.getInstance();if(mc.screen instanceof WorkspaceBuild125 build&&build.running131()){error("BUILD_BUSY");return;}preserve(mc.screen);mc.setScreen(new WorkspaceInbox131(mc.screen));}
    static void openOffer(String id,Screen previous){if(previous instanceof WorkspaceBuild125 build&&build.running131()){error("BUILD_BUSY");return;}call("OFFER131","",Map.of("offer",id),d->{if("OK".equals(d.get("result"))){preserve(previous);Minecraft.getInstance().setScreen(new WorkspaceConsent131(previous,d));}});}
    static void dismiss(String id,Runnable done){call("DECLINE131","",Map.of("offer",id),d->{nextPoll=0;toast=null;done.run();});}
    static void accept(Map<String,String> offer,Screen previous){
        if("INVITE".equals(offer.get("kind131"))){awaitingAccept=offer;acceptPrevious=previous;}
        call("ACCEPT131","",Map.of("offer",offer.get("offer")),d->{if(Set.of("UNKNOWN","INVALID").contains(d.get("result"))&&awaitingAccept!=null){var recovery=awaitingAccept;awaitingAccept=null;acceptPrevious=null;call("VIEW",recovery.get("project"),Map.of(),fresh->{if("OK".equals(fresh.get("result")))download(fresh,previous);});return;}awaitingAccept=null;acceptPrevious=null;nextPoll=0;toast=null;if(!"OK".equals(d.get("result"))){error(d.getOrDefault("result","INVALID"));return;}if("true".equals(d.get("joined131")))download(d,previous);else Minecraft.getInstance().setScreen(previous);});
    }
    static void resume131(Map<String,String> joined,WorkspaceScreen122 editor){download(joined,editor,editor);}
    private static void download(Map<String,String> joined,Screen previous){download(joined,previous,null);}
    private static void download(Map<String,String> joined,Screen previous,WorkspaceScreen122 existing){
        transferring=true;var screen=new WorkspaceJoinScreen131(previous);screen.pin(connection,++transferGeneration);screen.existing(existing);activeJoin=screen;Minecraft.getInstance().setScreen(screen);String project=joined.get("project");
        call("JOIN_MANIFEST131",project,Map.of(),d->{if(!screen.matches(connection,transferGeneration))return;if(!"OK".equals(d.get("result"))){transferring=false;screen.fail(d.get("result"));return;}if(d.getOrDefault("sourceFiles","").isEmpty()){transferring=false;enter(project,screen);return;}
            try{screen.total((int)d.get("sourceFiles").lines().count());var download=new WorkspaceSources125.Download(d.get("sourceHash"),d.get("sourceFiles"));nextChunk(project,d.get("token131"),download,screen);}catch(Exception e){transferring=false;screen.fail(e.getMessage());}});
    }
    private static void nextChunk(String project,String token,WorkspaceSources125.Download download,WorkspaceJoinScreen131 screen){
        if(!screen.matches(connection,transferGeneration))return;if(!ready()){transferring=false;screen.fail("DISCONNECTED");return;}var entry=download.next();if(entry==null){
            try{download.verified();}catch(Exception e){transferring=false;screen.fail(e.getMessage());return;}
            // The verified bytes are already in memory; the native binding materializes its own working tree.
            // The old disposable copy had no consumer and grew on every join.
            transferring=false;enter(project,screen);return;
        }
        int offset=download.offset();screen.progress(entry.path(),offset,entry.size());call("JOIN_CHUNK131",project,Map.of("token",token,"path",entry.path(),"offset",Integer.toString(offset)),d->{if(!screen.matches(connection,transferGeneration))return;if(!"OK".equals(d.get("result"))){transferring=false;screen.fail(d.get("result"));return;}try{download.accept(entry.path(),offset,d.get("chunk"));nextChunk(project,token,download,screen);}catch(Exception e){transferring=false;screen.fail(e.getMessage());}});
    }
    static void cancelTransfer135(WorkspaceJoinScreen131 screen){if(activeJoin==screen){transferGeneration++;transferring=false;activeJoin=null;nextPoll=0;}}
    private static void enter(String project,WorkspaceJoinScreen131 screen){if(!screen.matches(connection,transferGeneration)){screen.fail("STORE_CHANGED");return;}call("VIEW",project,Map.of(),fresh->{if(!"OK".equals(fresh.get("result"))){screen.fail(fresh.get("result"));return;}if(!screen.cancelled()&&!WorkspaceIntegrated132.tryEnter135(fresh,screen.previous()))screen.fail("SYNCING130");});}
    private static void drawToast(GuiGraphics g){if(toast==null||System.nanoTime()-toastUntil>0)return;var mc=Minecraft.getInstance();int w=Math.min(310,mc.getWindow().getGuiScaledWidth()-20),x=mc.getWindow().getGuiScaledWidth()-w-10;g.pose().pushPose();g.pose().translate(0,0,800);g.fill(x,10,x+w,80,0xF0222832);g.renderOutline(x,10,w,70,0xFFDFE6EF);g.drawString(mc.font,WorkspaceScreen122.tr("notice."+toast[2]),x+10,19,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(),false);g.drawString(mc.font,mc.font.plainSubstrByWidth(toast[4]+" · "+toast[5],w-20),x+10,36,0xFFCBD4E1,false);g.drawString(mc.font,WorkspaceScreen122.tr("notice_open131"),x+10,57,0xFFAEBFD3,false);g.pose().popPose();}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){if(Minecraft.getInstance().screen==null)drawToast(event.getGuiGraphics());}
    @SubscribeEvent public static void screen(ScreenEvent.Render.Post event){drawToast(event.getGuiGraphics());}
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre event){if(event.getKeyCode()==298&&ready()){openInbox();event.setCanceled(true);}}
    @SubscribeEvent public static void gameKey(InputEvent.Key event){if(event.getKey()==298&&event.getAction()==1&&Minecraft.getInstance().screen==null&&ready())openInbox();}
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event){if(toast==null||System.nanoTime()-toastUntil>0||event.getButton()!=0)return;int w=Math.min(310,Minecraft.getInstance().getWindow().getGuiScaledWidth()-20),x=Minecraft.getInstance().getWindow().getGuiScaledWidth()-w-10;if(event.getMouseX()>=x&&event.getMouseX()<=x+w&&event.getMouseY()>=10&&event.getMouseY()<=80){String id=toast[0];toast=null;if(!id.isEmpty())openOffer(id,event.getScreen());event.setCanceled(true);}}
}
