package com.fixmer.mared.technology.scripts;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.server.*;
import com.fixmer.mared.technology.server.LocalScriptServerBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Client preparation and local transport. Closing an editor cancels preparation, not active world modules. */
public final class ServerProjectApplies implements AutoCloseable {
    public record Result(boolean success,String project,String detail) {}
    private record Target(IntegratedServer server,UUID owner) {
        boolean current(){var mc=Minecraft.getInstance();return mc.getSingleplayerServer()==server&&mc.player!=null&&owner.equals(mc.player.getUUID())&&!server.isPublished();}
    }
    private final SourceProjects storage;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"Genesis server module compiler");thread.setDaemon(true);return thread;});
    private Future<RuntimeModuleCompiler.Prepared> preparation;
    private CompletableFuture<ServerModuleEngine.Result> deployment;
    private JavaSourceRunner.Cancellation token;
    private Target target;
    private String project="",diagnostics="";
    private boolean closed;
    public ServerProjectApplies(SourceProjects storage){this.storage=storage;}
    private static Target capture(){var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();return server==null||mc.player==null||server.isPublished()?null:new Target(server,mc.player.getUUID());}
    public static boolean available(){return capture()!=null;}
    public boolean busy(){return preparation!=null||deployment!=null;}
    public String phase(){return preparation!=null?"compile":deployment!=null?"apply":"";}
    public String project(){return project;}
    public static ServerModuleEngine.Status status(String id){Target target=capture();return target==null?null:LocalScriptServerBridge.statuses(target.server).get(id);}
    public static String drain(){Target target=capture();return target==null?"":LocalScriptServerBridge.drain(target.server);}
    public boolean start(String id,String entry,Map<String,SourceProjects.Recovery> drafts,BuildArtifacts.Artifact artifact){
        if(closed||busy())return false;Target captured=capture();if(captured==null)return false;
        this.target=captured;this.project=id;this.diagnostics="";token=new JavaSourceRunner.Cancellation();var cancellation=token;var snapshot=Map.copyOf(drafts);
        preparation=worker.submit(()->{
            if(artifact==null){Map<String,String> sources;Map<String,byte[]> jars,resources;synchronized(storage){sources=storage.javaSources(id,snapshot);jars=storage.javaLibraries(id);resources=storage.javaResources(id);}return RuntimeModuleCompiler.compile(sources,jars,resources,entry,cancellation,storage.javaHome());}
            if(cancellation.cancelled())throw new java.io.IOException("Deployment cancelled");
            if(artifact.size()>64L*1024*1024)throw new java.io.IOException("Runtime JAR exceeds 64 MiB");
            var delivered=storage.deliverArtifact(id,artifact,cancellation::cancelled);
            byte[] bytes;try(var input=java.nio.file.Files.newInputStream(delivered)){bytes=input.readNBytes(64*1024*1024+1);}finally{try{java.nio.file.Files.deleteIfExists(delivered);}finally{java.nio.file.Files.deleteIfExists(delivered.getParent());}}
            return RuntimeModuleCompiler.fromJar(bytes,storage.buildRuntimeLibraries(id,artifact,cancellation::cancelled),entry,cancellation);
        });return true;
    }
    public boolean manage(String id,boolean rollback){if(closed||busy())return false;var captured=capture();if(captured==null)return false;target=captured;project=id;token=new JavaSourceRunner.Cancellation();diagnostics="";deployment=LocalScriptServerBridge.manage(captured.server,captured.owner,id,rollback);return true;}
    public void cancel(){if(token!=null)token.cancel();if(deployment!=null)deployment.cancel(false);}
    public void poll(Consumer<Result> callback){
        if(target!=null&&!target.current())cancel();
        if(preparation!=null&&preparation.isDone()){
            Future<RuntimeModuleCompiler.Prepared> task=preparation;preparation=null;
            try {
                var prepared=task.get();diagnostics=prepared.diagnostics();
                if(closed||token.cancelled()||!target.current()){prepared.bundle().close();callback.accept(new Result(false,project,"CANCELLED / world changed"));return;}
                var captured=target;deployment=LocalScriptServerBridge.apply(captured.server,captured.owner,()->!captured.server.isPublished(),project,prepared.bundle());
            }catch(InterruptedException bad){Thread.currentThread().interrupt();callback.accept(new Result(false,project,"CANCELLED"));}
            catch(ExecutionException bad){callback.accept(new Result(false,project,Objects.toString(bad.getCause().getMessage(),bad.getCause().toString())));}
        }
        if(deployment!=null&&deployment.isDone()){
            var task=deployment;deployment=null;
            try{var result=task.join();callback.accept(new Result(result.success(),project,result.message()+" #"+result.generation()+"\n"+diagnostics));}
            catch(CompletionException|CancellationException bad){callback.accept(new Result(false,project,bad.toString()));}
        }
    }
    @Override public void close(){if(closed)return;closed=true;cancel();
        // Consume abandoned successful compilations after worker completion so temporary bundles cannot leak.
        if(preparation!=null){var task=preparation;worker.submit(()->{try{task.get().bundle().close();}catch(Exception ignored){}});preparation=null;}
        worker.shutdown();
    }
}
