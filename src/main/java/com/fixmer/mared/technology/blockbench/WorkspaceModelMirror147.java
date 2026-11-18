package com.fixmer.mared.technology.blockbench;

import com.fixmer.genesis.technology.workspace.live122.WorkspaceScripts129;
import com.fixmer.genesis.technology.creation.BlockbenchDocument146;
import com.fixmer.mared.technology.workspace.live122.WorkspaceScreen122;
import com.fixmer.mared.gui2.runtime.RuntimeProvider;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import java.util.*;
import java.util.concurrent.*;

/** Mirrors acknowledged server models to the owner's existing project without overwriting local drafts. */
public final class WorkspaceModelMirror147 {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MaRed model mirror");t.setDaemon(true);return t;});
    private static final Map<String,String> applied=new HashMap<>();
    private static boolean busy;
    private static long next=System.nanoTime();
    private WorkspaceModelMirror147(){}
    public static void tick(WorkspaceScreen122 workspace){
        if(Minecraft.getInstance().screen instanceof DesktopBlockbenchScreen148)return;
        if(busy||System.nanoTime()-next<0)return;next=System.nanoTime()+3_000_000_000L;
        var context=workspace.context132();String source=context.getOrDefault("source132","");
        if(!"true".equals(context.get("models147"))||source.isEmpty()||!context.getOrDefault("actor","").equals(context.get("owner")))return;
        for(String row:context.getOrDefault("files","").lines().toList()){
            String[] fields=row.split("\\|",-1);if(fields.length!=3||!BlockbenchDocument146.validPath(fields[0]))continue;
            String path=fields[0],key=context.get("project")+"/"+path;
            if(fields[2].equals(applied.get(key)))continue;
            if(RuntimeProvider.isInstalled()&&!RuntimeProvider.get().session().workbench().canMirrorModel147(source,path))continue;
            busy=true;var channel=new WorkspaceModelClient147(context,workspace::modelMetadata147);
            channel.open(path).thenAccept(model->{
                IO.execute(()->{
                    try{
                        var config=FMLPaths.CONFIGDIR.get();var storage=new WorkspaceScripts129(com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.workspace(config.resolve("COMMANDS".equals(context.get("mode133"))?"mared/command_projects":"mared/script_projects")),config.resolve("mared/workspace-script-bindings129"));
                        storage.mirrorModel147(UUID.fromString(context.get("actor")),context.get("store"),UUID.fromString(context.get("project")),path,model.text());
                        Minecraft.getInstance().execute(()->{if(applied.size()>2048)applied.clear();applied.put(key,model.hash());});
                    }catch(Exception failure){com.fixmer.mared.Mared.LOGGER.warn("Model mirror kept the local copy of {}: {}",path,failure.toString());}
                    finally{Minecraft.getInstance().execute(()->{channel.close();busy=false;});}
                });
            }).exceptionally(failure->{channel.close();busy=false;return null;});
            return;
        }
    }
}
