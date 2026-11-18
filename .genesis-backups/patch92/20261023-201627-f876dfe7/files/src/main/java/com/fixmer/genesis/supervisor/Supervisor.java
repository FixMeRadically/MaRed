package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A JDK-only entry point. One lifecycle worker owns every launch and stop. */
public final class Supervisor implements AutoCloseable {
    public enum State { STOPPED, WAITING_FOR_PARENT, STARTING, RUNNING, STOPPING, ERROR }
    private final SupervisorConfig config;
    private final SupervisorLog log;
    private final ModDeployment deployment;
    private final RestartMetrics metrics;
    private final RestartPresentation presentation;
    private final ExecutorService preparation = Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"supervisor-preflight");t.setDaemon(true);return t;});
    private Future<LaunchPreparation.Plan> prepared;
    private boolean cdsDisabled;

    private final ScheduledExecutorService trialTimer = Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"supervisor-mod-trial");t.setDaemon(true);return t; });
    private volatile Thread lifecycleThread;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "supervisor-lifecycle");t.setDaemon(true); lifecycleThread = t; return t;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile State state = State.STOPPED;
    private volatile ManagedChildProcess child;
    private volatile String error = "";
    private volatile boolean closed;
    private volatile Runnable releaseControl=()->{};
    private volatile long handoffPid;
    private final CompletableFuture<Boolean> handoffDecision=new CompletableFuture<>();
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"supervisor-session-watch");t.setDaemon(true);return t;});
    private volatile int restarts;
    private final CountDownLatch done = new CountDownLatch(1);

    Supervisor(SupervisorConfig config, SupervisorLog log) { this.config = config; this.log = log; this.deployment=new ModDeployment(config.gameDir());this.metrics=new RestartMetrics(config.gameDir());this.presentation=new RestartPresentation(config,log); }

    public static void main(String[] args) throws Exception {
        SupervisorConfig config = SupervisorConfig.load(args);
        // The lock survives a closed stdin and prevents two controllers for one config.
        try (FileChannel channel = FileChannel.open(config.configPath().resolveSibling("supervisor.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Supervisor already owns this config");
            try (lock; SupervisorLog log = new SupervisorLog(config.logFile()); Supervisor s = new Supervisor(config, log);
                    SupervisorHttpServer control = new SupervisorHttpServer(config, s, log)) {
                Runtime.getRuntime().addShutdownHook(new Thread(s::close, "supervisor-shutdown"));
                // Binding MUST succeed before launching anything.
                s.releaseControl=control::close;
                control.start();
                long parent = 0;
                for (String arg : args) if (arg.startsWith("--handoff-pid=")) parent = Long.parseLong(arg.substring(14));
                s.initialStart(parent);
                Thread stdin = new Thread(() -> {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            switch (line.trim()) {
                                case "start", "stop", "restart" -> s.request(line.trim(), false);
                                case "status" -> System.out.println(s.statusJson());
                                case "quit", "exit" -> { s.close(); return; }
                                default -> log.warn("Commands: start | stop | restart | status | quit");
                            }
                        }
                    } catch (IOException e) { log.warn("stdin closed: " + e.getMessage()); }
                    // EOF is expected for a supervisor started by Minecraft.
                }, "supervisor-stdin");
                stdin.setDaemon(true); stdin.start();
                s.watchdog.scheduleWithFixedDelay(s::watchSession,100,250,TimeUnit.MILLISECONDS);
                s.done.await();
            }
        }
        System.exit(0); // Standalone bootstrap only; never invoked from the Minecraft process.
    }

    private void initialStart(long pid) {
        busy.set(true);
        state = State.STARTING;
        executeLifecycle(() -> {
            try {
                ManagedChildProcess.validateCommand(config);
                if (pid > 0) {
                    presentation.begin("Завершение текущей игры");
                    if (!ProcessHandle.current().isAlive()) throw new IOException("ProcessHandle unavailable; cannot verify original Minecraft exit");
                    ProcessHandle parent = ProcessHandle.of(pid).orElse(null);
                    handoffPid=pid;
                    state = State.WAITING_FOR_PARENT;
                    // Binding a port is not consent to restart. The original client commits explicitly.
                    if(!handoffDecision.get(15,TimeUnit.SECONDS))throw new IOException("Handoff cancelled");
                    prepared=preparation.submit(()->LaunchPreparation.prepare(config,log));
                    if (parent != null) {
                        log.info("Handoff: waiting for original Minecraft pid=" + pid);
                        try { parent.onExit().get(60, TimeUnit.SECONDS); }
                        catch (TimeoutException e) { throw new IOException("Original Minecraft is still running; no second client launched"); }
                    }
                }
                if (!closed) launch();
            } catch (Exception e) { failed(e); close(); }
            finally { busy.set(false); }
        });
    }

    synchronized boolean request(String action, boolean cooperative) {
        if(closed||!(action.equals("start")||action.equals("stop")||action.equals("restart")))return false;
        ManagedChildProcess current=child;
        if(action.equals("restart")&&(state!=State.RUNNING||current==null||!current.isAlive()||current.terminalQueued.get()||current.session().poll()!=GameSession.End.NONE||current.session().closing()))return false;
        if(!busy.compareAndSet(false,true))return false;
        if(current!=null&&(action.equals("restart")||action.equals("stop")))current.expectedExit=true;
        try {
            executeLifecycle(() -> {
                long begin = System.nanoTime();
                try {
                    if (closed) return;
                    switch (action) {
                        case "start" -> { if (child == null) launch(); }
                        case "stop" -> {
                            stopChild(false);
                            deployment.rollback("Session stopped before world-ready confirmation");
                            close();
                        }
                        case "restart" -> {
                            synchronized (this) { state = State.STOPPING; metrics.begin("restart"); }
                            presentation.begin("Завершение текущей игры");
                            prepared=preparation.submit(()->LaunchPreparation.prepare(config,log));
                            stopChild(cooperative, true);
                            if (!closed) {
                                if (config.restartDelayMs() > 0) Thread.sleep(config.restartDelayMs());
                                launch(); restarts++;
                            }
                        }
                        default -> throw new IllegalArgumentException("Unknown lifecycle action");
                    }
                } catch (IntegratedRestartHeld90 held) {
                    holdIntegrated90(held);
                } catch (Exception e) { failed(e); close(); }
                finally { log.info(action + " finished in " + (System.nanoTime()-begin)/1_000_000 + " ms, state=" + state); busy.set(false); }
            });
            return true;
        } catch (RejectedExecutionException e) { busy.set(false); return false; }
    }

    synchronized boolean requestHttp(String action,boolean cooperative,String launch){
        if(action.equals("restart")&&(child==null||!child.launchId().equals(launch)))return false;
        return request(action,cooperative);
    }
    synchronized boolean commitHandoff(String instance,long parent){
        if(closed||state!=State.WAITING_FOR_PARENT||handoffPid!=parent||!config.instanceId().equals(instance)||handoffDecision.isDone())return false;
        return handoffDecision.complete(true);
    }
    boolean cancelHandoffAsync(String instance){
        synchronized(this){if(closed||state!=State.WAITING_FOR_PARENT||!config.instanceId().equals(instance))return false;handoffDecision.complete(false);}
        Thread cancel=new Thread(this::close,"supervisor-handoff-cancel");cancel.setDaemon(true);cancel.start();return true;
    }
    private void executeLifecycle(Runnable task){
        worker.execute(()->{try{task.run();}catch(Throwable fatal){
            try{failed(new IOException("Lifecycle failure: "+fatal.getClass().getSimpleName(),fatal));}finally{close();}
        }});
    }

    private void launch() throws IOException {
        if (closed) return;
        if (child != null) throw new IOException("Previous process tree has not been reaped");
        state = State.STARTING; error = "";
        long launchBegin=System.nanoTime();
        LaunchPreparation.Plan plan;
        try {plan=prepared==null?LaunchPreparation.prepare(config,log):prepared.get(10,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Launch preparation interrupted",e);}
        catch(ExecutionException|TimeoutException e){throw new IOException("Launch preparation failed",e);}
        finally{if(prepared!=null)prepared.cancel(true);prepared=null;}
        presentation.phase("Подготовка файлов и установка изменений");
        String trial = deployment.beforeLaunch();
        LaunchPreparation.warm(config,log);
        presentation.phase("Запуск Minecraft");
        ManagedChildProcess launched;
        try { launched = ManagedChildProcess.launch(config, log, trial,plan,cdsDisabled); }
        catch (IOException error) {
            if (deployment.rollback("Candidate process could not start: "+error.getMessage())) {
                log.warn("Candidate launch failed; restored previous JAR and retrying once");
                metrics.begin("rollback"); presentation.begin("Возврат предыдущей версии мода"); launch(); return;
            }
            throw error;
        }
        if (!trial.isEmpty()) trialTimer.schedule(() -> expireTrial(trial),5,TimeUnit.MINUTES);
        child = launched;
        if (closed) {
            try { launched.stopTree(config.gracefulStopMs(), log); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            child = null; return;
        }
        state = State.RUNNING;
        log.info("Child started, pid=" + launched.pid());
        log.info("[launch-mode] "+plan.mode());
        metrics.event(launched.launchId(),"launch_"+plan.mode(),0,log);
        if(launched.startupProfileRequested)metrics.event(launched.launchId(),"startup_profile_requested",0,log);
        metrics.event(launched.launchId(),"fast_"+plan.reason().toLowerCase(java.util.Locale.ROOT),0,plan.changes(),log);
        metrics.event(launched.launchId(),"prepare",plan.elapsedMs(),log);
        metrics.event(launched.launchId(),"spawn",(System.nanoTime()-launchBegin)/1_000_000,log);
        Thread watcher = new Thread(() -> {
            try {sessionEnded(launched,launched.waitFor(),"launcher exited",true);}
            catch(InterruptedException e){Thread.currentThread().interrupt();}
        },"supervisor-exit");
        watcher.setDaemon(true);watcher.start();
    }
    private final java.util.Map<ManagedChildProcess,Long> endCandidates=new java.util.concurrent.ConcurrentHashMap<>();
    private void watchSession(){
        if(closed)return;
        try{
            ManagedChildProcess current=child;if(current==null||current.expectedExit||state!=State.RUNNING)return;
            if(!current.isAlive()){sessionEnded(current,current.waitFor(),"launcher exited",true);return;}
            GameSession.End end=current.session().poll();
            if(current.session().presentationReady())finishPresentation(current);
            long gameStarted=current.session().jvmStartMs();
            if(gameStarted>0&&gameStarted-current.spawnWall>=-2000&&gameStarted-current.spawnWall<900_000&&current.bootGapWritten.compareAndSet(false,true))metrics.event(current.launchId(),"launcher_gap",Math.max(0,gameStarted-current.spawnWall),log);
            if(end==GameSession.End.NONE){endCandidates.remove(current);return;}
            // Let Gradle propagate a real non-zero game exit before classifying a stuck wrapper.
            long now=System.nanoTime(),first=endCandidates.computeIfAbsent(current,c->now);
            if((end==GameSession.End.NORMAL&&current.session().confirmedExit())||now-first>=2_000_000_000L)sessionEnded(current,end==GameSession.End.NORMAL?0:1,"game session ended; launcher still running",current.session().confirmedExit());
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Throwable e){try{failed(new IOException("Session watchdog failed",e));}finally{close();}}
    }
    private synchronized void sessionEnded(ManagedChildProcess launched,int code,String reason,boolean recover){
        if(closed||child!=launched||launched.expectedExit||!launched.terminalQueued.compareAndSet(false,true))return;
        busy.set(true);state=State.STOPPING;
        try{executeLifecycle(()->{
            if(closed||child!=launched||launched.expectedExit)return;
            try{
                log.info("Session ended: "+reason+"; exit="+code+"; gamePid="+launched.session().pid());
                stopChild(false);
                if(code!=0){
                    if(launched.customCds())cdsDisabled=true;
                    if(deployment.rollback("Game ended before world-ready confirmation; exit="+code)){
                        if(recover&&!launched.session().integrated90()){
                            log.warn("Candidate mod rolled back; starting the previous installation once");
                            metrics.begin("rollback");presentation.begin("Возврат предыдущей версии мода");launch();return;
                        }
                        log.warn("Candidate rolled back; no automatic relaunch because game exit could not be verified");
                    }
                    failed(new IOException("Game session failed; see child log"));
                }else deployment.rollback("Game closed normally before world-ready confirmation; not relaunching");
            }catch(Exception e){failed(e);}
            finally{endCandidates.remove(launched);if(child==null||child==launched)close();busy.set(false);}
        });}catch(RejectedExecutionException ignored){close();}
    }

    boolean confirmDeployment(String token) {
        if (closed || state!=State.RUNNING || child==null || child.terminalQueued.get() || token == null || token.isBlank() || !busy.compareAndSet(false,true)) return false;
        try { executeLifecycle(() -> {
            try { deployment.confirm(token); log.info("Mod trial confirmed after world entry"); }
            catch (IOException e) { log.error("Mod confirmation failed: "+e.getMessage()); }
            finally { busy.set(false); }
        }); return true; }
        catch (RejectedExecutionException e) { busy.set(false); return false; }
    }
    private void expireTrial(String token) {
        if (closed) return;
        if (!busy.compareAndSet(false,true)) { trialTimer.schedule(() -> expireTrial(token),1,TimeUnit.SECONDS); return; }
        try { executeLifecycle(() -> {
            try {
                if (!token.equals(deployment.activeTrial())) return;
                if (child != null && (!child.isAlive()||child.terminalQueued.get()||child.session().closing())) return; // Exit watcher owns a game already closed by the user.
                if(child!=null&&child.session().integrated90()){log.warn("INTEGRATED90_TRIAL: automatic timed rollback deferred for an integrated world; waiting for normal completion or explicit stop");return;}
                metrics.begin("rollback");
                presentation.begin("Возврат предыдущей версии мода");
                log.warn("Mod trial did not open a world in 5 minutes; restoring previous JAR");
                boolean wasRunning=child!=null;
                if(child!=null)child.expectedExit=true;
                stopChild(false, true);
                deployment.rollback("World-ready confirmation timeout");
                if (wasRunning&&!closed) launch();
            } catch (IntegratedRestartHeld90 held) {holdIntegrated90(held);}
            catch (Exception e) { failed(e); close(); }
            finally { busy.set(false); }
        }); } catch (RejectedExecutionException e) { busy.set(false); }
    }

    private void holdIntegrated90(IntegratedRestartHeld90 held){
        error=held.getMessage();log.warn(error);ManagedChildProcess retained=child;
        if(retained!=null){retained.expectedExit=false;state=State.RUNNING;}
        if(prepared!=null){prepared.cancel(true);prepared=null;}presentation.ready();
    }
    private static final class IntegratedRestartHeld90 extends IOException {IntegratedRestartHeld90(){super("INTEGRATED90_HOLD: world save/stop/exit not confirmed; retained owned client, no force and no second JVM. Close the game normally and inspect logs.");}}
    private void stopChild(boolean cooperative) throws Exception { stopChild(cooperative, false); }

    private void stopChild(boolean cooperative, boolean recordShutdown) throws Exception {
        ManagedChildProcess current = child;
        if (current == null) { state = State.STOPPED; return; }
        state = State.STOPPING;
        long begin=System.nanoTime();
        current.session().poll();
        if(current.isAlive())current.requestShutdown();
        int budget=cooperative?config.gracefulRestartWaitMs():config.gracefulStopMs();
        if(recordShutdown&&current.session().integrated90())budget=Math.max(budget,120_000);
        log.info("Requesting normal Minecraft shutdown; grace budget="+budget+" ms");
        if(!current.terminalQueued.get())current.waitForShutdown(budget);
        current.session().poll();
        if(recordShutdown&&current.session().integrated90())try{if(!(current.session().normalExit91()&&deployment.recovery91().phase().equals("RESTORE_REQUESTED")))current.session().requireIntegratedRestart90();}catch(IllegalStateException unconfirmed){throw new IntegratedRestartHeld90();}
        String exitProof=current.session().exitSource();
        if(!current.isAlive()&&current.session().pid()==current.pid())exitProof="launcher";
        if (!current.stopTree(0, log))
            throw new IOException("Process tree still alive after forced stop; refusing a second client");
        if (recordShutdown){
            metrics.event(current.launchId(),"shutdown",(System.nanoTime()-begin)/1_000_000,log);
            metrics.event(current.launchId(),"shutdown_via_"+exitProof,0,log);
        }
        endCandidates.remove(current);
        child = null; state = State.STOPPED;
    }

    synchronized boolean recordMetric(String launch,String phase,long duration) {
        ManagedChildProcess c=child;
        if(state!=State.RUNNING||c==null||!c.launchId().equals(launch)||phase==null||!(java.util.Set.of("mod_loaded","world_loading","world_ready","frame_ready","player_restored","player_restore","title_ready","return_failed","resources_ready","mared_threading","mared_persistent","mared_plugins","mared_setup","startup_launch_cache","startup_font").contains(phase)||(phase.startsWith("cache_capture_")&&LaunchCacheClient.confirmationReason(phase.substring("cache_capture_".length()).toUpperCase(java.util.Locale.ROOT)))||java.util.Set.of("cache_capture_cached","cache_capture_no_seed","cache_capture_consumed","cache_capture_input_changed_during_capture","cache_capture_invalid_or_unsupported","cache_capture_unconfirmed","cache_capture_fingerprint_time_limit","cache_capture_fingerprint_file_limit","cache_capture_fingerprint_byte_limit","cache_capture_linked_input","cache_capture_debug_launch","cache_capture_long_opaque_command").contains(phase))||duration<0||duration>86_400_000)return false;
        metrics.event(launch,phase,duration,log);
        switch(phase){
            case "mod_loaded" -> presentation.phase("Загрузка ресурсов Minecraft");
            case "world_loading" -> presentation.phase("Открытие мира");
            case "player_restored" -> presentation.phase("Восстановление игрока");
            case "world_ready","title_ready","return_failed" -> presentation.phase("Подготовка первого кадра");
            case "frame_ready" -> finishPresentation(c);
            default -> { }
        }
        return true;
    }
    private void finishPresentation(ManagedChildProcess current){
        if(!current.presentationReleased.compareAndSet(false,true))return;
        long epoch=presentation.epochMs(),elapsed=System.currentTimeMillis()-epoch;
        if(epoch>0&&elapsed>=0&&elapsed<=900_000)metrics.event(current.launchId(),"scene_total",elapsed,log);
        presentation.ready();
    }
    private void failed(Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        error = e.toString(); state = State.ERROR; log.error(error);
    }

    synchronized boolean canRetire(){
        if(closed)return false;
        if(child==null)return !busy.get()&&(state==State.STOPPED||state==State.ERROR);
        return !child.expectedExit&&(child.terminalQueued.get()||!child.isAlive()||child.session().poll()!=GameSession.End.NONE);
    }
    synchronized boolean retire(String instance){
        if(!config.instanceId().equals(instance)||!canRetire()||!busy.compareAndSet(false,true))return false;
        if(child!=null)child.expectedExit=true;
        try{executeLifecycle(()->{try{stopChild(false);deployment.rollback("Stale supervisor retired without relaunch");}catch(Exception e){failed(e);}finally{close();}});return true;}
        catch(RejectedExecutionException e){busy.set(false);return false;}
    }
    synchronized String identityProperties()throws IOException{
        var p=new java.util.Properties();ManagedChildProcess c=child;
        p.setProperty("service","genesis-supervisor");p.setProperty("protocol","4");p.setProperty("lifecycleVersion","10");p.setProperty("presentationVersion","11");
        p.setProperty("instanceId",config.instanceId());p.setProperty("configPath",config.configPath().toAbsolutePath().normalize().toString());
        p.setProperty("gameDir",config.gameDir().toAbsolutePath().normalize().toString());p.setProperty("controlPort",Integer.toString(config.controlPort()));
        p.setProperty("supervisorPid",Long.toString(ProcessHandle.current().pid()));p.setProperty("state",state.name());
        p.setProperty("busy",Boolean.toString(busy.get()));p.setProperty("handoffPid",Long.toString(handoffPid));
        p.setProperty("canRetire",Boolean.toString(canRetire()));p.setProperty("gameClosing",Boolean.toString(c!=null&&c.session().closing()));
        p.setProperty("processLeaseVersion","1");p.setProperty("gameLease",c==null?"NONE":c.session().leaseState());
        p.setProperty("gamePid",Long.toString(c==null?0:c.session().pid()));p.setProperty("gameState",c==null?"NONE":c.session().state());
        var out=new StringWriter();p.store(out,"Genesis controller identity");return out.toString();
    }
    String statusJson() {
        ManagedChildProcess c = child;
        return "{\"service\":\"genesis-supervisor\",\"protocol\":4,\"instanceId\":" + SupervisorHttpServer.quote(config.instanceId())
            + ",\"configPath\":" + SupervisorHttpServer.quote(config.configPath().toString())
            + ",\"lifecycleVersion\":10,\"presentationVersion\":11,\"processLeaseVersion\":1,\"gameLease\":"+SupervisorHttpServer.quote(c==null?"NONE":c.session().leaseState())+",\"gamePid\":"+(c==null?0:c.session().pid())+",\"gameState\":"+SupervisorHttpServer.quote(c==null?"NONE":c.session().state())+",\"state\":" + SupervisorHttpServer.quote(state.name()) + ",\"busy\":" + busy.get()
            + ",\"launchId\":" + SupervisorHttpServer.quote(c==null?"":c.launchId())
            + ",\"pid\":" + (c == null ? -1 : c.pid()) + ",\"supervisorPid\":" + ProcessHandle.current().pid()
            + ",\"uptimeMs\":" + (c == null ? 0 : c.uptimeMs()) + ",\"restarts\":" + restarts
            + ",\"controlPort\":" + config.controlPort() + ",\"error\":" + SupervisorHttpServer.quote(error) + "}";
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        boolean interrupted=Thread.interrupted();
        handoffDecision.complete(false);
        // Close control independently of the process-tree reaper or lifecycle worker.
        try{releaseControl.run();}catch(RuntimeException ignored) { }
        presentation.close();
        watchdog.shutdown();
        preparation.shutdownNow();
        trialTimer.shutdownNow();
        boolean onWorker = Thread.currentThread() == lifecycleThread;
        // A terminal child exit is handled on this worker. Never interrupt or join ourselves.
        if (onWorker) worker.shutdown(); else worker.shutdownNow();
        try {
            if(!onWorker)try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))log.warn("Lifecycle worker did not stop in 5 seconds");}
                catch(InterruptedException e){interrupted=true;Thread.interrupted();}
            ManagedChildProcess c=child;
            if(c!=null)try{if(!c.stopTree(config.gracefulStopMs(),log))log.warn("Owned process tree could not be fully reaped during shutdown");}
                catch(InterruptedException e){interrupted=true;Thread.interrupted();try{c.stopTree(0,log);}catch(InterruptedException again){interrupted=true;}}
        }finally{done.countDown();if(interrupted)Thread.currentThread().interrupt();}
    }
}
