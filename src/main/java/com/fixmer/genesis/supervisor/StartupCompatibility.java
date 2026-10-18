package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** A conservative default, independent of mod names and load order. */
public final class StartupCompatibility {
    public static final String ENV="GENESIS_STARTUP_COMPATIBILITY";
    public record Policy(String mode) {
        public boolean experimental(){return "experimental".equals(mode);}
    }
    private StartupCompatibility() { }
    public static Policy read(Path game) {
        try {
            Path file=game.resolve("config/mared/dev/startup.properties");RuntimeFiles.safe(file);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>65536)return new Policy("compatible");
            Properties values=new Properties();try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){values.load(in);}
            return new Policy("experimental".equals(values.getProperty("startup.mode"))?"experimental":"compatible");
        } catch(IOException|RuntimeException failure){return new Policy("compatible");}
    }
    /** Supervised games use the policy fixed before their JVM was started. */
    public static boolean worldTuningAllowed(Path game) {
        String inherited=System.getenv(ENV);
        if("1".equals(System.getenv("GENESIS_SUPERVISOR"))&&inherited!=null)return "experimental".equals(inherited);
        return read(game).experimental();
    }
    static void clearAgentReport(Path game,SupervisorLog log) {
        try {Path file=game.resolve("config/mared/dev/path-cache-last.properties");RuntimeFiles.safe(file);Files.deleteIfExists(file);}
        catch(IOException|RuntimeException failure){log.warn("[startup-compat] Old agent report retained; use startup_compat.py for the current launch policy");}
    }
    static void report(Path game,String launch,String launchMode,Policy policy,boolean agent,boolean cds,SupervisorLog log) {
        Path temporary=null;
        try {
            if(!launch.matches("[0-9a-f-]{36}")||!java.util.Set.of("direct_cache","gradle","direct_config","configured").contains(launchMode))return;
            Path target=game.resolve("config/mared/dev/startup-compat-last.properties");RuntimeFiles.safe(target);Files.createDirectories(target.getParent());
            Properties values=new Properties();values.setProperty("schema","1");values.setProperty("launch",launch);
            values.setProperty("mode",policy.experimental()?"experimental":"compatible");values.setProperty("launch_mode",launchMode);
            values.setProperty("startup_agent_applied",Boolean.toString(agent));values.setProperty("dynamic_cds_applied",Boolean.toString(cds));
            values.setProperty("world_distance_tuning_allowed",Boolean.toString(policy.experimental()));
            temporary=Files.createTempFile(target.getParent(),".startup-compat-",".tmp");
            try(var out=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){values.store(out,"Genesis launch policy; not a certification of third-party mods");}
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException|RuntimeException failure){log.warn("[startup-compat] Launch policy report unavailable");}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
}
