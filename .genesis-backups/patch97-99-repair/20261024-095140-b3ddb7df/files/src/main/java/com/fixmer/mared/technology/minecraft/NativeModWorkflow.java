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
    public static String stage(SourceProjects storage,String project,BuildArtifacts.Artifact artifact,Map<String,String> versions,BooleanSupplier cancelled)throws IOException {
        if("1".equals(System.getenv("GENESIS_SUPERVISOR"))&&!"4".equals(System.getenv("GENESIS_SUPERVISOR_PROTOCOL")))throw new IOException("Stop the previous supervisor and start the updated MaRed before applying native mods");
        if(!artifact.kind().equals("mod")&&!artifact.kind().equals("mod_embedded"))throw new IOException("Select a packaged NeoForge mod JAR, not sources or a runtime ServerModule");
        if(artifact.inputs().isEmpty()||artifact.buildId().isEmpty())throw new IOException("Build provenance missing; run Build and Apply again");
        if(cancelled.getAsBoolean())throw new IOException("Mod apply cancelled");
        Path delivered=storage.deliverArtifact(project,artifact,cancelled);
        String token=null;
        try {
            if(cancelled.getAsBoolean())throw new IOException("Mod apply cancelled");
            token=deployment().stage(delivered,artifact.sha256(),versions,project);
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
