package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns the direct process and retains discovered descendants after parent exit. */
final class ManagedChildProcess {
    private final Process process;
    private final RuntimeFiles runtime;
    private final CdsSupport.Session cds;
    private final GameSession session;
    final java.util.concurrent.atomic.AtomicBoolean terminalQueued=new java.util.concurrent.atomic.AtomicBoolean();
    volatile boolean expectedExit;
    final java.util.concurrent.atomic.AtomicBoolean presentationReleased=new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicBoolean bootGapWritten=new java.util.concurrent.atomic.AtomicBoolean();
    final long spawnWall=System.currentTimeMillis();
    GameSession session(){return session;}
    private volatile boolean untrackedDescendant;
    private final Map<Long,java.time.Instant> identities = new ConcurrentHashMap<>();
    String launchId() { return runtime.id; }
    boolean startupProfileRequested;
    boolean customCds() { return cds.custom(); }
    void requestShutdown() throws IOException { runtime.shutdown(); }

    private final long started = System.nanoTime();
    private final Map<Long, ProcessHandle> tree = new ConcurrentHashMap<>();
    private final ScheduledExecutorService tracker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "supervisor-tree"); t.setDaemon(true); return t;
    });
    private ManagedChildProcess(Process process, SupervisorLog log,RuntimeFiles runtime,CdsSupport.Session cds,SupervisorConfig config) throws IOException {
        this.runtime=runtime;this.cds=cds;this.session=new GameSession(runtime,config);
        this.process = process; tree.put(process.pid(), process.toHandle());
        process.getOutputStream().close(); // Never depend on an interactive child stdin.
        tracker.scheduleWithFixedDelay(this::rememberTree, 0, 50, TimeUnit.MILLISECONDS);
        Thread reader = new Thread(() -> {
            try (Reader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                ChildOutput.copy(in,log::child);
            } catch (IOException e) { log.warn("Child output closed: " + e.getMessage()); }
        }, "supervisor-output");
        reader.setDaemon(true); reader.start();
    }
    static ManagedChildProcess launch(SupervisorConfig config, SupervisorLog log) throws IOException { return launch(config,log,""); }
    static ManagedChildProcess launch(SupervisorConfig config, SupervisorLog log, String trial) throws IOException {
        return launch(config,log,trial,LaunchPreparation.prepare(config,log),false);
    }
    static ManagedChildProcess launch(SupervisorConfig config,SupervisorLog log,String trial,LaunchPreparation.Plan plan,boolean bypassCds)throws IOException {
        List<String> command = new ArrayList<>(plan.command());
        Map<String,String> extra=new HashMap<>(plan.environment());
        var policy=StartupCompatibility.read(config.gameDir());
        CdsSupport.Session cds=CdsSupport.apply(config,command,extra,bypassCds,log,plan.workDir(),policy.experimental());
        RuntimeFiles runtime=new RuntimeFiles(config.gameDir());
        if(!policy.experimental())StartupCompatibility.clearAgentReport(config.gameDir(),log);
        boolean startupAgent=StartupPathCache.apply(config.gameDir(),plan.mode(),command,extra,runtime.id,cds.custom(),log,policy.experimental());
        var startupRecording=StartupRecording.prepare(config.gameDir(),plan.mode(),command,runtime.id,log);
        if(System.getProperty("os.name","").startsWith("Windows") && command.stream().mapToInt(a->a.length()+3).sum()>24000
                && Set.of("java","java.exe","javaw.exe").contains(Path.of(command.get(0)).getFileName().toString().toLowerCase(Locale.ROOT))) {
            try {command=new ArrayList<>(JavaArgumentFile.wrap(command,runtime));}
            catch(IOException e){startupRecording.failed(log);runtime.cleanup(log);throw e;}
        }
        ProcessBuilder builder = new ProcessBuilder(command).directory(plan.workDir().toFile()).redirectErrorStream(true);
        builder.environment().putAll(extra);
        builder.environment().remove("GENESIS_TRANSITION_FILE");
        Path surface=RestartSurface.path(config.gameDir());
        if(config.restartUi()&&RestartSurface.load(surface)!=null)builder.environment().put("GENESIS_TRANSITION_FILE",surface.toString());
        builder.environment().put(StartupCompatibility.ENV,policy.mode());
        builder.environment().put("GENESIS_LAUNCH_ID",runtime.id);
        builder.environment().put("GENESIS_SESSION_FILE",runtime.directory.resolve("session.properties").toString());
        builder.environment().put("GENESIS_SHUTDOWN_FILE",runtime.shutdownFile().toString());
        builder.environment().put("GENESIS_SUPERVISOR", "1");
        builder.environment().put("GENESIS_SUPERVISOR_PROTOCOL", "4");
        builder.environment().put("GENESIS_FRAME_HANDOFF", "32");
        builder.environment().put("GENESIS_DEV_MODE", "true");
        builder.environment().put("GENESIS_SUPERVISOR_PORT", Integer.toString(config.controlPort()));
        builder.environment().put("GENESIS_SUPERVISOR_TOKEN", config.token());
        builder.environment().remove("GENESIS_MOD_TRIAL");
        if (!trial.isEmpty()) builder.environment().put("GENESIS_MOD_TRIAL",trial);
        // Do not print launch arguments: launcher commands can contain account tokens.
        log.info("Launching child in " + plan.workDir());
        Process process;
        try { process=builder.start(); }
        catch(IOException e) { startupRecording.failed(log);runtime.cleanup(log);throw e; }
        StartupCompatibility.report(config.gameDir(),runtime.id,plan.mode(),policy,startupAgent,cds.custom(),log);
        startupRecording.spawned(log);
        try { var managed=new ManagedChildProcess(process, log,runtime,cds,config);managed.startupProfileRequested=startupRecording.claim()!=null;return managed; }
        catch (IOException | RuntimeException e) { process.destroyForcibly();runtime.cleanup(log);throw e; }
    }
    static List<String> normalize(List<String> original, Path cwd) throws IOException {
        List<String> args = new ArrayList<>(original);
        String head = args.get(0).replace('\\','/');
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String name = head.substring(head.lastIndexOf('/')+1);
        if (name.equalsIgnoreCase("gradlew") || name.equalsIgnoreCase("gradlew.bat")) {
            Path originalPath = Path.of(head);
            Path parent = originalPath.getParent();
            Path wrapper = cwd.resolve(parent == null ? Path.of("") : parent).resolve(windows ? "gradlew.bat" : "gradlew").normalize();
            if (!Files.isRegularFile(wrapper)) throw new IOException("Missing Gradle wrapper: " + wrapper);
            if (args.contains("--daemon")) throw new IOException("Shared Gradle daemon is not supported; use --no-daemon");
            if (!args.contains("--no-daemon")) args.add(1, "--no-daemon");
            args.set(0, wrapper.toString());
            if (!windows) { args.add(0, "sh"); return args; }
        }
        if (windows && (args.get(0).toLowerCase(Locale.ROOT).endsWith(".bat") || args.get(0).toLowerCase(Locale.ROOT).endsWith(".cmd"))) {
            // cmd.exe has shell expansion even with ProcessBuilder. Refuse ambiguous metacharacters.
            List<String> quoted = new ArrayList<>();
            for (String arg : args) {
                if (arg.matches(".*[\"%!?&|<>^\\r\\n].*")) throw new IOException("Unsafe batch argument; use a direct java command instead");
                quoted.add("\"" + arg + "\"");
            }
            return List.of("cmd.exe", "/d", "/s", "/c", "\"" + String.join(" ", quoted) + "\"");
        }
        return args;
    }
    static void validateCommand(SupervisorConfig config) throws IOException {
        List<String> cmd = normalize(config.command(), config.workDir());
        Path executable = Path.of(cmd.get(0));
        if (executable.isAbsolute() || executable.getParent() != null) {
            if (!Files.isRegularFile(config.workDir().resolve(executable)))
                throw new IOException("Executable not found: " + executable);
            return;
        }
        String path = System.getenv("PATH");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (path != null) for (String entry : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path base = Path.of(entry.isEmpty() ? "." : entry);
            if (!base.isAbsolute()) base = config.workDir().resolve(base);
            if (Files.isRegularFile(base.resolve(executable)) || (windows && Files.isRegularFile(base.resolve(executable + ".exe")))) return;
        }
        throw new IOException("Executable is not on PATH: " + executable);
    }
    private void rememberTree() {
        for (ProcessHandle h : List.copyOf(tree.values())) {
            try { h.descendants().limit(513).forEach(p -> {
                if(tree.size()>=512&&!tree.containsKey(p.pid())){untrackedDescendant=true;return;}
                tree.computeIfAbsent(p.pid(),pid->{p.info().startInstant().ifPresent(start->identities.put(pid,start));return p;});
            }); }
            catch (RuntimeException ignored) { }
        }
    }
    private boolean sameIdentity(ProcessHandle h) {
        if(h.pid()==process.pid())return true;
        try{var start=identities.get(h.pid());return start!=null&&h.info().startInstant().filter(start::equals).isPresent();}
        catch(RuntimeException e){return false;}
    }
    boolean isAlive() { return process.isAlive(); }
    long pid() { return process.pid(); }
    long uptimeMs() { return (System.nanoTime()-started)/1_000_000; }
    int waitFor() throws InterruptedException { return process.waitFor(); }
    boolean waitFor(long ms) throws InterruptedException { return process.waitFor(ms,TimeUnit.MILLISECONDS); }
    void waitForShutdown(long budgetMs)throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budgetMs);
        while(process.isAlive()){
            GameSession.End end=session.poll();
            // The world JVM is gone; do not spend the remaining grace budget waiting for Gradle cleanup.
            if(end!=GameSession.End.NONE&&session.confirmedExit())return;
            long left=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());if(left<=0)return;
            process.waitFor(Math.min(50,left),TimeUnit.MILLISECONDS);
        }
    }
    synchronized boolean stopTree(int gracefulMs, SupervisorLog log) throws InterruptedException {
        rememberTree();
        List<ProcessHandle> handles = new ArrayList<>(tree.values());
        // Descendants before ancestors; PID order is not a tree order on Windows.
        handles.sort(Comparator.comparingInt(this::depth).reversed());
        for (ProcessHandle h : handles) if (h.pid() != process.pid() && h.isAlive() && sameIdentity(h)) h.destroy();
        process.destroy();
        if (!awaitTree(gracefulMs)) {
            log.warn("Graceful stop timed out; forcing owned process tree");
            rememberTree();
            for (ProcessHandle h : tree.values()) if (h.pid() != process.pid() && h.isAlive() && sameIdentity(h)) h.destroyForcibly();
            process.destroyForcibly();
            if (!awaitTree(5000)) {tracker.shutdownNow();return false;}
        }
        cds.finished(process.exitValue(),log);
        runtime.cleanup(log);
        tracker.shutdownNow();
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        return true;
    }
    private int depth(ProcessHandle handle) {
        int depth = 0;
        Set<Long> seen = new HashSet<>();
        while (depth < 128 && seen.add(handle.pid())) {
            java.util.Optional<ProcessHandle> parent;try{parent=handle.parent();}catch(RuntimeException e){break;}
            if (parent.isEmpty() || !tree.containsKey(parent.get().pid())) break;
            depth++; handle = parent.get();
        }
        return depth;
    }
    private boolean blocksReap(ProcessHandle handle){
        try{
            if(!handle.isAlive())return false;
            var expected=identities.get(handle.pid());var actual=handle.info().startInstant();
            // Only a proven PID reuse may be ignored; an unverified live descendant blocks a second JVM.
            return expected==null||actual.isEmpty()||actual.get().equals(expected);
        }catch(RuntimeException e){return true;}
    }
    private boolean gameBlocksReap(){
        session.poll();
        if(!session.registered())return false;
        if(session.pid()==process.pid()&&!process.isAlive())return false;
        return !session.confirmedExit();
    }
    private boolean awaitTree(long ms) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
        while (process.isAlive() || gameBlocksReap() || tree.values().stream().anyMatch(h -> h.pid() != process.pid() && blocksReap(h))) {
            if (System.nanoTime() >= deadline) return false;
            rememberTree(); Thread.sleep(20);
        }
        return !untrackedDescendant;
    }
}
