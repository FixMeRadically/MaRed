package com.fixmer.genesis.supervisor;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Pure command preparation can overlap old-world shutdown; it never mutates mods or launches a JVM. */
final class LaunchPreparation {
    record Plan(List<String> command,Map<String,String> environment,long elapsedMs,Path workDir,String mode,String reason,List<String> changes) {
        Plan(List<String> command,Map<String,String> environment,long elapsedMs,Path workDir,String mode,String reason){this(command,environment,elapsedMs,workDir,mode,reason,List.of());}
        Plan(List<String> command,Map<String,String> environment,long elapsedMs,Path workDir){this(command,environment,elapsedMs,workDir,"configured","CUSTOM_COMMAND");}
        Plan(List<String> command,Map<String,String> environment,long elapsedMs,Path workDir,String mode){this(command,environment,elapsedMs,workDir,mode,"UNRECORDED");}
        Plan {
            command=List.copyOf(command);
            environment=Map.copyOf(environment);
            changes=List.copyOf(changes);
        }
    }
    static Plan prepare(SupervisorConfig config,SupervisorLog log)throws IOException {
        long begin=System.nanoTime();
        var decision=DirectLaunchPlan.evaluate(config,log);
        var fast=decision.snapshot();
        if(fast!=null)return new Plan(fast.command(),fast.environment(),TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin),fast.workDir(),"direct_cache",decision.reason(),decision.changes());
        ManagedChildProcess.validateCommand(config);
        var command=new ArrayList<>(ManagedChildProcess.normalize(config.command(),config.workDir()));
        var env=new LinkedHashMap<String,String>();
        return new Plan(command,env,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin),config.workDir(),DirectLaunchPlan.defaultWrapper(config.command())?"gradle":"direct_config",decision.reason(),decision.changes());
    }
    /** Warm the candidate files after installation, within byte/time budgets. Never scans saves or writes them. */
    static void warm(SupervisorConfig config,SupervisorLog log) {
        if(!config.warmCache())return;
        long begin=System.nanoTime(),deadline=begin+TimeUnit.MILLISECONDS.toNanos(config.warmCacheMs()),bytes=0;
        int entries=0,files=0;
        try {
            var pending=new ArrayDeque<Path>();
            pending.add(config.gameDir().resolve("mods"));
            pending.add(config.gameDir().resolve("libraries"));
            // Explicit direct-JVM class/module paths only. Opaque @argfiles are left to the launcher.
            var args=config.command();
            for(int i=1;i<args.size();i++)if(Set.of("-cp","-classpath","--class-path","-p","--module-path").contains(args.get(i))&&i+1<args.size()) {
                for(String part:args.get(++i).split(java.util.regex.Pattern.quote(java.io.File.pathSeparator),-1))if(!part.isBlank())pending.add(config.workDir().resolve(part).normalize());
            }
            else if(args.get(i).equals("-jar")&&i+1<args.size())pending.add(config.workDir().resolve(args.get(++i)).normalize());
            var visited=new HashSet<Path>();
            byte[] buffer=new byte[65536];
            while(!pending.isEmpty()&&entries<4096&&bytes<config.warmCacheBytes()&&System.nanoTime()<deadline&&!Thread.currentThread().isInterrupted()) {
                Path file=pending.removeFirst().toAbsolutePath().normalize();
                if(!visited.add(file))continue;
                entries++;
                if(Files.isSymbolicLink(file)||!Files.exists(file,LinkOption.NOFOLLOW_LINKS))continue;
                RuntimeFiles.safe(file);
                if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS)) {
                    try(var children=Files.newDirectoryStream(file)) {
                        for(Path child:children) {
                            if(pending.size()+entries>=4096||System.nanoTime()>=deadline)break;
                            String name=child.getFileName().toString();
                            if(!name.startsWith(".")&&!Set.of("saves","logs","config","screenshots").contains(name))pending.addLast(child);
                        }
                    }
                    continue;
                }
                if(!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                files++;
                try(var in=Files.newInputStream(file)) {
                    int n;
                    while(bytes<config.warmCacheBytes()&&System.nanoTime()<deadline&&!Thread.currentThread().isInterrupted() &&(n=in.read(buffer,0,(int)Math.min(buffer.length,config.warmCacheBytes()-bytes)))!=-1)bytes+=n;
                }
            }
            log.info("[warm-cache] files="+files+" read="+bytes+" bytes in "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)+" ms (bounded; OS retention is not guaranteed)");
        }
        catch(IOException e) {
            log.warn("Cache warm skipped: "+e.getMessage());
        }
    }
}
