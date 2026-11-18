package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.storage.DevResumeStore;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

/** Handoff: prepare -> bind -> verify identity -> normal game exit -> new game. */
public final class DevActivationService {
    private static final AtomicBoolean ACTIVATING = new AtomicBoolean();
    public static volatile String lastStatus = "Ready";
    private DevActivationService() { }
    public static boolean isActivating() { return ACTIVATING.get(); }

    public static java.util.concurrent.CompletableFuture<Boolean> activate() {
        synchronized(com.fixmer.mared.technology.server.setup.RemoteApplyService70.class) {
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        if(System.getenv("GENESIS_REMOTE70_ROOT")!=null||System.getenv("GENESIS_FIRST58_ROOT")!=null||com.fixmer.mared.technology.server.setup.RemoteApplyService70.busy()||com.fixmer.mared.technology.server.setup.ServerPanelService66.busy()){
            status(com.fixmer.mared.MaredLang.get("mared.remote70.owner_blocked"));
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (DevRestartService.isUnderSupervisor() || !ACTIVATING.compareAndSet(false,true)) return java.util.concurrent.CompletableFuture.completedFuture(false);
        final com.fixmer.mared.dev.storage.DevResumeStore.Target target;
        try { target = DevSessionResume.captureCurrent(); }
        catch (RuntimeException e) { status(e.getMessage()); ACTIVATING.set(false); return java.util.concurrent.CompletableFuture.completedFuture(false); }
        DevRestartOverlay.show("mared.dev.restart.animation.requesting");
        Thread task = new Thread(() -> perform(target, result), "mared-dev-activation");
        task.setDaemon(true); task.start();
        return result;
        }
    }
    private static void status(String value) { lastStatus = value; Mared.LOGGER.info("[Mared dev] {}",value); }
    private static void perform(com.fixmer.mared.dev.storage.DevResumeStore.Target target, java.util.concurrent.CompletableFuture<Boolean> result) {
        Process process = null;
        String ticket = null;
        try {
            if(target!=null&&target.localWorld())status(com.fixmer.mared.MaredLang.get("mared.integrated90.saving"));
            var local90=DevIntegratedRestart90.prepare(target);
            status("Preparing supervisor...");
            int port = DevClientConfig.controlPort();
            com.fixmer.mared.dev.DevPaths.migrateLegacy();
            Path dir = com.fixmer.mared.dev.DevPaths.root();
            Files.createDirectories(dir);
            Path propertiesFile = dir.resolve("supervisor.properties");
            try (var activationGuard=com.fixmer.genesis.supervisor.SupervisorRecovery.activationGuard(propertiesFile)) {
            Properties props = new Properties();
            if (Files.isRegularFile(propertiesFile)) try (Reader reader = Files.newBufferedReader(propertiesFile,StandardCharsets.UTF_8)) { props.load(reader); }
            com.fixmer.genesis.supervisor.SupervisorRecovery.awaitAvailable(port,propertiesFile,props,DevActivationService::status);
            // Preserve a manually configured launch command and its working directory.
            if (!props.containsKey("command") && props.stringPropertyNames().stream().noneMatch(k -> k.startsWith("command."))) {
                LaunchCommand launch = captureCommand();
                List<String> command = launch.command();
                for (int i=0;i<command.size();i++) props.setProperty("command."+i,command.get(i));
                props.setProperty("workDir",launch.cwd().toString());
            }
            props.setProperty("gameDir",FMLPaths.GAMEDIR.get().toAbsolutePath().normalize().toString());
            props.putIfAbsent("workDir",Path.of("").toAbsolutePath().normalize().toString());
            props.putIfAbsent("gracefulStopMs","2000");
            props.putIfAbsent("gracefulRestartWaitMs","2000");
            props.putIfAbsent("restartStopBudgetMs","2000");
            props.putIfAbsent("cds.mode","default");
            props.putIfAbsent("warmCache.enabled","false");
            props.putIfAbsent("restartDelayMs","0");
            props.putIfAbsent("logFile",com.fixmer.mared.dev.DevPaths.logs().resolve("supervisor.log").toString());
            String token = UUID.randomUUID().toString() + UUID.randomUUID();
            String instance = UUID.randomUUID().toString();
            props.setProperty("controlPort",Integer.toString(port));
            props.setProperty("controlToken",token); props.setProperty("instanceId",instance);
            Path temp = Files.createTempFile(dir,"supervisor-",".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temp,StandardCharsets.UTF_8)) { props.store(writer,"MaRed supervisor. command.N and workDir are preserved; remove command keys to recapture."); }
                replace(temp,propertiesFile);
            } finally { Files.deleteIfExists(temp); }
            // Export an embedded JDK-only capsule; FML's module classpath is not a java -cp classpath.
            // One capsule per activation: Windows may keep the previous JVM's JAR locked briefly.
            try(var old=Files.newDirectoryStream(dir,"supervisor-bootstrap-*.jar")){int n=0;for(Path capsule:old){if(++n>32)break;try{if(!Files.isSymbolicLink(capsule))Files.deleteIfExists(capsule);}catch(IOException ignored){}}}
            Path bootstrap = dir.resolve("supervisor-bootstrap-"+instance+".jar");
            temp = Files.createTempFile(dir,"bootstrap-",".tmp");
            try {
                try (InputStream in = DevActivationService.class.getResourceAsStream("/mared/dev/supervisor-bootstrap.jar")) {
                    if (in == null) throw new IOException("Missing supervisor resource. Rebuild the mod with the updated build.gradle.");
                    Files.copy(in,temp,StandardCopyOption.REPLACE_EXISTING);
                }
                replace(temp,bootstrap);
            } finally { Files.deleteIfExists(temp); }
            String javaExecutable = Path.of(System.getProperty("java.home"),"bin",isWindows()?"java.exe":"java").toString();
            ticket = DevSessionResume.prepare(target);
            ProcessBuilder builder = new ProcessBuilder(javaExecutable,"-Dgenesis.supervisor.quiet=true","--add-modules=jdk.httpserver","-cp",bootstrap.toString(),
                "com.fixmer.genesis.supervisor.Supervisor","--config="+propertiesFile,"--handoff-pid="+ProcessHandle.current().pid());
            builder.directory(Path.of("").toAbsolutePath().toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(com.fixmer.mared.dev.DevPaths.logs().resolve("bootstrap.log").toFile()));
            process = builder.start();
            process.getOutputStream().close(); // EOF must not terminate the controller.
            status("Waiting for supervisor confirmation...");
            long deadline = System.nanoTime()+Duration.ofSeconds(12).toNanos();
            while (System.nanoTime()<deadline) {
                if (!process.isAlive()) throw new IOException("Supervisor exited ("+process.exitValue()+"). See config/mared/dev/logs/bootstrap.log.");
                try {
                    Properties identity=com.fixmer.genesis.supervisor.SupervisorRecovery.identity(port,token);
                    if(!com.fixmer.genesis.supervisor.SupervisorRecovery.matches(identity,propertiesFile,props,port))throw new IllegalStateException("Unexpected supervisor owner");
                    if("ERROR".equals(identity.getProperty("state")))throw new IllegalStateException("Supervisor could not prepare handoff; see bootstrap.log");
                    if(Long.parseLong(identity.getProperty("supervisorPid"))==process.pid()
                        && Long.parseLong(identity.getProperty("handoffPid"))==ProcessHandle.current().pid()
                        && "WAITING_FOR_PARENT".equals(identity.getProperty("state"))) {
                        if(!com.fixmer.genesis.supervisor.SupervisorRecovery.post(port,token,"/handoff/commit",instance,ProcessHandle.current().pid()))throw new IllegalStateException("Supervisor rejected restart handoff");
                        status("Supervisor committed. Closing Minecraft normally...");
                        stopOnClient(local90);
                        result.complete(true);return;
                    }
                } catch(IOException e){ /* listener may still be starting */ }
                Thread.sleep(100);
            }
            throw new IOException("Supervisor confirmation timed out; this game remains open. See config/mared/dev/logs/bootstrap.log.");
            }
        } catch (Exception e) {
            DevSessionResume.cancel(ticket);
            if (process != null && process.isAlive()) {
                process.destroy();
                try {if(!process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(2,java.util.concurrent.TimeUnit.SECONDS);}}
                catch(InterruptedException interrupted){process.destroyForcibly();Thread.currentThread().interrupt();}
            }
            status("Activation failed: "+e.getMessage());
            Mared.LOGGER.error("[Mared dev] Activation failed",e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            DevRestartOverlay.clear();
            ACTIVATING.set(false); result.complete(false);
        }
    }
    /** A task still queued after timeout must not close the game later without a supervisor. */
    static void stopOnClient(DevIntegratedRestart90.Receipt local90)throws Exception{
        var permission=new AtomicBoolean(true);
        var stopped=new java.util.concurrent.CompletableFuture<Void>();
        Minecraft.getInstance().execute(()->{
            try{
                DevIntegratedRestart90.validate(local90);
                DevWorldProfile.restore();
                if(!permission.compareAndSet(true,false)){stopped.completeExceptionally(new java.util.concurrent.CancellationException("Handoff cancelled before client exit"));return;}
                Minecraft.getInstance().stop();stopped.complete(null);
            }catch(Throwable error){stopped.completeExceptionally(error);}
        });
        try{stopped.get(5,java.util.concurrent.TimeUnit.SECONDS);}
        catch(java.util.concurrent.TimeoutException timeout){
            if(permission.compareAndSet(true,false))throw timeout;
            // Minecraft.stop() has already begun; do not tear down its committed replacement.
            stopped.get();
        }
    }
    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source,target,StandardCopyOption.REPLACE_EXISTING); }
    }
    private record LaunchCommand(List<String> command, Path cwd) { }
    private static LaunchCommand captureCommand() throws IOException {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String executable = info.command().orElse(Path.of(System.getProperty("java.home"),"bin",isWindows()?"java.exe":"java").toString());
        String[] arguments = info.arguments().orElse(null);
        if (arguments == null || arguments.length == 0) {
            // Windows often hides JVM arguments. Recognize THIS source project's runClient,
            // rather than attempting to split sun.java.command and corrupt quoted arguments.
            for (Path cwd = Path.of("").toAbsolutePath().normalize(); cwd != null; cwd = cwd.getParent()) {
                Path build = cwd.resolve("build.gradle");
                if (Files.isRegularFile(build) && Files.isRegularFile(cwd.resolve("gradlew.bat"))
                    && Files.isRegularFile(cwd.resolve("src/main/java/com/fixmer/mared/Mared.java"))
                    && Files.readString(build).contains("net.neoforged.gradle.userdev")) {
                    status("Using project runClient (launch arguments unavailable). Gradle will compile changes.");
                    return new LaunchCommand(List.of(cwd.resolve(isWindows()?"gradlew.bat":"gradlew").toString(),
                        "--no-daemon","runClient"),cwd);
                }
            }
            throw new IOException("Launcher hides arguments. Configure command.N and workDir in config/mared/dev/supervisor.properties.");
        }
        if (arguments.length==0) throw new IOException("Empty launch arguments; configure command.N manually.");
        List<String> result = new ArrayList<>(); result.add(executable);
        // Override any inherited -Dmared.dev=false. Keep actual arguments intact, including @argfiles.
        result.add("-Dmared.dev=true");
        for (String arg : arguments) {
            if (arg.startsWith("-agentlib:jdwp=") || arg.startsWith("-Xrunjdwp:") || arg.startsWith("-Dmared.dev=")) continue;
            if (arg.startsWith("@") && !Files.isRegularFile(Path.of(arg.substring(1))))
                throw new IOException("Launch argument file no longer exists; configure command.N manually.");
            result.add(arg);
        }
        return new LaunchCommand(List.copyOf(result),Path.of("").toAbsolutePath().normalize());
    }
    private static boolean isWindows() { return System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"); }
}
