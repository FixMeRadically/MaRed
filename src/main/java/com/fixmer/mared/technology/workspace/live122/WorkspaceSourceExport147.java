package com.fixmer.mared.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.live122.WorkspaceSources125;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Export a verified readable server snapshot, including models, without a Gradle/mod build. */
public final class WorkspaceSourceExport147 {
    private WorkspaceSourceExport147(){}
    public static void start(WorkspaceScreen122 workspace,Consumer<String> result){
        if(!workspace.settled132()){workspace.flush132();result.accept(com.fixmer.mared.MaredLang.get("mared.tools147.wait_sync"));return;}
        workspace.operation132("JOIN_MANIFEST131",Map.of(),data->{
            if(!"OK".equals(data.get("result"))){result.accept(data.getOrDefault("result","IO_ERROR"));return;}
            if(data.getOrDefault("sourceFiles","").isEmpty()){result.accept("EMPTY");return;}
            try{next(workspace,data.get("token131"),new WorkspaceSources125.Download(data.get("sourceHash"),data.get("sourceFiles")),result);}catch(Exception error){result.accept(error.toString());}
        });
    }
    private static void next(WorkspaceScreen122 workspace,String token,WorkspaceSources125.Download download,Consumer<String> result){
        var entry=download.next();if(entry==null){
            try{
                var files=download.verified();String project=UUID.fromString(workspace.context132().get("project")).toString();
                Thread io=new Thread(()->{Path archive=null;try{
                    Path folder=com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.safe(FMLPaths.CONFIGDIR.get().resolve("mared/workspace-exports147"));Files.createDirectories(folder);com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.privateMode(folder);
                    archive=com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.safe(folder.resolve(project+"-"+UUID.randomUUID()+".zip"));
                    try(var zip=new ZipOutputStream(Files.newOutputStream(archive,StandardOpenOption.CREATE_NEW))){for(var file:new TreeMap<>(files).values()){zip.putNextEntry(new ZipEntry(file.path()));zip.write(file.bytes());zip.closeEntry();}}
                    String path=archive.toString();Minecraft.getInstance().execute(()->{Minecraft.getInstance().keyboardHandler.setClipboard(path);result.accept(path);});
                }catch(Exception error){if(archive!=null)try{Files.deleteIfExists(archive);}catch(IOException ignored){}Minecraft.getInstance().execute(()->result.accept(error.toString()));}},"MaRed workspace export");io.setDaemon(true);io.start();
            }catch(Exception error){result.accept(error.toString());}return;
        }
        int offset=download.offset();workspace.operation132("JOIN_CHUNK131",Map.of("token",token,"path",entry.path(),"offset",Integer.toString(offset)),data->{
            if(!"OK".equals(data.get("result"))){result.accept(data.getOrDefault("result","IO_ERROR"));return;}
            try{download.accept(entry.path(),offset,data.get("chunk"));next(workspace,token,download,result);}catch(Exception error){result.accept(error.toString());}
        });
    }
}
