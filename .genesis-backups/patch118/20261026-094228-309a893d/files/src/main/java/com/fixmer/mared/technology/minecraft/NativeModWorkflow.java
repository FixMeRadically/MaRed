package com.fixmer.mared.technology.minecraft;
import com.fixmer.genesis.supervisor.ModDeployment;
import com.fixmer.genesis.technology.scripts.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
/** Real NeoForge mods are installed on the next supervised JVM startup, not in a runtime classloader. */
public final class NativeModWorkflow {
    private NativeModWorkflow() {
    }
    public static Map<String,String> loadedVersions() {
        var map=new java.util.LinkedHashMap<String,String>();
        for(var mod:ModList.get().getMods())map.put(mod.getModId(),mod.getVersion().toString());
        return Map.copyOf(map);
    }
    private static ModDeployment deployment() {
        return new ModDeployment(FMLPaths.GAMEDIR.get());
    }
    public record Request99(Map<String,BuildArtifacts.Artifact> updates,java.util.Set<String> removals) {
        public Request99 { updates=Map.copyOf(updates);removals=java.util.Set.copyOf(removals); }
    }
    private static java.util.List<com.fixmer.genesis.supervisor.ModSet99.Input> deliverSet99(SourceProjects storage,Request99 request,BooleanSupplier cancelled,java.util.List<Path> files)throws IOException {
        var inputs=new java.util.ArrayList<com.fixmer.genesis.supervisor.ModSet99.Input>();
        for(var e:new java.util.TreeMap<>(request.updates()).entrySet()){
            validate98(e.getValue(),cancelled);Path delivered=storage.deliverArtifact(e.getKey(),e.getValue(),cancelled);files.add(delivered);
            inputs.add(new com.fixmer.genesis.supervisor.ModSet99.Input(e.getKey(),delivered,e.getValue().sha256()));
        }
        if(cancelled.getAsBoolean())throw new IOException("SET99_CANCELLED");return java.util.List.copyOf(inputs);
    }
    public static com.fixmer.genesis.supervisor.ModSet99.Review reviewSet99(SourceProjects storage,Request99 request,Map<String,String> versions,BooleanSupplier cancelled)throws IOException {
        var files=new java.util.ArrayList<Path>();try{return deployment().reviewSet99(deliverSet99(storage,request,cancelled,files),request.removals(),versions);}finally{files.forEach(NativeModWorkflow::cleanup98);}
    }
    public static String stageSet99(SourceProjects storage,Request99 request,Map<String,String> versions,BooleanSupplier cancelled,com.fixmer.genesis.supervisor.ModSet99.Review review)throws IOException {
        if("1".equals(System.getenv("GENESIS_SUPERVISOR"))&&!"4".equals(System.getenv("GENESIS_SUPERVISOR_PROTOCOL")))throw new IOException("SET99_OLD_SUPERVISOR");
        var files=new java.util.ArrayList<Path>();String token=null;
        try{
            var inputs=deliverSet99(storage,request,cancelled,files);token=deployment().stageSet99(inputs,request.removals(),versions,review);
            if(cancelled.getAsBoolean()){deployment().cancelPending(token);throw new IOException("SET99_CANCELLED");}return token;
        }catch(IOException|RuntimeException failed){if(token!=null)cancel(token);throw failed;}finally{files.forEach(NativeModWorkflow::cleanup98);}
    }
    public static ModDeployment.Review98 review98(SourceProjects storage,String project,BuildArtifacts.Artifact artifact,Map<String,String> versions,BooleanSupplier cancelled)throws IOException {
        validate98(artifact,cancelled);
        Path delivered=storage.deliverArtifact(project,artifact,cancelled);
        try {
            if(cancelled.getAsBoolean())throw new IOException("Mod review cancelled");
            return deployment().review98(delivered,artifact.sha256(),versions,project);
        } finally { cleanup98(delivered); }
    }
    private static void validate98(BuildArtifacts.Artifact artifact,BooleanSupplier cancelled)throws IOException {
        if(!artifact.kind().equals("mod")&&!artifact.kind().equals("mod_embedded"))throw new IOException("Select a packaged NeoForge mod JAR");
        if(artifact.inputs().isEmpty()||artifact.buildId().isEmpty())throw new IOException("Build provenance missing; build again");
        if(cancelled.getAsBoolean())throw new IOException("Mod operation cancelled");
    }
    private static void cleanup98(Path delivered) {
        try{Files.deleteIfExists(delivered);Files.deleteIfExists(delivered.getParent());}
        catch(IOException error){com.fixmer.mared.Mared.LOGGER.warn("Native mod delivery cleanup failed: {}",error.getMessage());}
    }
    public static String stageReviewed98(SourceProjects storage,String project,BuildArtifacts.Artifact artifact,Map<String,String> versions,BooleanSupplier cancelled,ModDeployment.Review98 review)throws IOException {
        if(!review.project().equals(project)||!review.sha256().equals(artifact.sha256()))throw new IOException("DEPLOYMENT98_REVIEW_CHANGED");
        return stage98(storage,project,artifact,versions,cancelled,review);
    }
    public static String stage(SourceProjects storage,String project,BuildArtifacts.Artifact artifact,Map<String,String> versions,BooleanSupplier cancelled)throws IOException {
        var request=new Request99(Map.of(project,artifact),java.util.Set.of());
        return stageSet99(storage,request,versions,cancelled,reviewSet99(storage,request,versions,cancelled));
    }
    private static String stage98(SourceProjects storage,String project,BuildArtifacts.Artifact artifact,Map<String,String> versions,BooleanSupplier cancelled,ModDeployment.Review98 review)throws IOException {
        if("1".equals(System.getenv("GENESIS_SUPERVISOR"))&&!"4".equals(System.getenv("GENESIS_SUPERVISOR_PROTOCOL")))throw new IOException("Stop the previous supervisor and start the updated MaRed before applying native mods");
        if(!artifact.kind().equals("mod")&&!artifact.kind().equals("mod_embedded"))throw new IOException("Select a packaged NeoForge mod JAR, not sources or a runtime ServerModule");
        if(artifact.inputs().isEmpty()||artifact.buildId().isEmpty())throw new IOException("Build provenance missing; run Build and Apply again");
        if(cancelled.getAsBoolean())throw new IOException("Mod apply cancelled");
        Path delivered=storage.deliverArtifact(project,artifact,cancelled);
        String token=null;
        try {
            if(cancelled.getAsBoolean())throw new IOException("Mod apply cancelled");
            token=review==null?deployment().stage(delivered,artifact.sha256(),versions,project):deployment().stageReviewed98(delivered,review,versions);
            if(cancelled.getAsBoolean()) {
                deployment().cancelPending(token);
                throw new IOException("Mod apply cancelled");
            }
            return token;
        }
        catch(IOException|RuntimeException error) {
            if(token!=null)cancel(token);
            throw error;
        }
        finally {
            try {
                Files.deleteIfExists(delivered);
                Files.deleteIfExists(delivered.getParent());
            }
            catch(IOException error) {
                com.fixmer.mared.Mared.LOGGER.warn("[Mared mod] Staging temporary file cleanup failed",error);
            }
        }
    }
    public static void cancel(String token) {
        if(token==null||token.isBlank())return;
        try {
            deployment().cancelPending(token);
        }
        catch(IOException e) {
            com.fixmer.mared.Mared.LOGGER.error("[Mared mod] Could not cancel staged mod",e);
        }
    }
    public static String status(String project)throws IOException {
        return deployment().status(project);
    }
    public static Path exportConfirmed(String project)throws IOException {
        return deployment().exportConfirmed(project,FMLPaths.GAMEDIR.get().resolve("mared/exports"));
    }
}
