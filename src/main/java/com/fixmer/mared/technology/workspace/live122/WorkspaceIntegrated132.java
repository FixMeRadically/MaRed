package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.shell.MaredShellScreen;
import com.fixmer.mared.gui2.runtime.RuntimeProvider;
import com.fixmer.mared.gui2.navigation.SpaceId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Map;

/** Collaboration belongs to the normal MaRed workbench, and survives opening its management dialogs. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class WorkspaceIntegrated132 {
    private WorkspaceIntegrated132(){}
    private static WorkspaceScreen122 current;
    private static Object connection;
    public static WorkspaceScreen122 current132(){return current;}
    public static boolean matches132(String project){return current!=null&&!project.isEmpty()&&project.equals(current.context132().get("project"));}
    public static Screen shell132(Screen fallback){
        if(RuntimeProvider.isInstalled()&&RuntimeProvider.get().host() instanceof MaredShellScreen shell)return shell;
        if(fallback instanceof MaredShellScreen)return fallback;
        return new MaredShellScreen().setInitialSpace(SpaceId.STUDIO);
    }
    public static void enter(Map<String,String> context,Screen previous){tryEnter135(context,previous);}
    public static boolean tryEnter135(Map<String,String> context,Screen previous){
        if(current!=null&&!matches132(context.getOrDefault("project",""))&&!current.settled132()){
            Mared.LOGGER.warn("[WorkSpace] Cannot switch project while local operations await acknowledgment");WorkspaceSocial131.error("SYNCING130");
            return false;
        }
        if(!matches132(context.getOrDefault("project",""))){
            if(current!=null)current.detach132();
            current=new WorkspaceScreen122(previous);current.integrate132(context);
            connection=Minecraft.getInstance().getConnection();
        }
        if(previous instanceof WorkspaceScreen122 manager)manager.releaseTransport132();Screen shell=shell132(previous);var mc=Minecraft.getInstance();
        if(mc.screen instanceof MaredShellScreen active)active.suspendForSettings();
        mc.setScreen(shell);
        if(RuntimeProvider.isInstalled()){
            var runtime=RuntimeProvider.get();
            if(!(runtime.navigation().current() instanceof com.fixmer.mared.gui2.spaces.StudioSpace))runtime.navigation().push(SpaceId.STUDIO);
            runtime.session().workbench().openShared132(current);
        }
        return true;
    }
    public static void openAt132(String path,int line){
        if(current==null)return;var mc=Minecraft.getInstance();mc.setScreen(shell132(mc.screen));
        if(RuntimeProvider.isInstalled())RuntimeProvider.get().session().workbench().openSharedFile132(current,path,line);
    }
    public static void leave132(){if(current!=null){current.operation132("LEAVE",Map.of(),d->{});current.detach132();current=null;}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        com.fixmer.mared.technology.blockbench.WorkspaceModelClient147.tickAll147();
        if(current==null)return;
        if(Minecraft.getInstance().getConnection()!=connection){current.detach132();current=null;connection=null;return;}
        current.tickClient();
        com.fixmer.mared.technology.blockbench.WorkspaceModelMirror147.tick(current);
    }
}
