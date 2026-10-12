package com.fixmer.genesis.technology.scripts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Explicit local Gradle Wrapper execution. Build scripts have the user's OS permissions. */
public final class GradleBuildRunner implements AutoCloseable {
    public enum Status { SUCCESS, FAILED, CANCELLED, TIMEOUT, INPUT_ERROR, IO_ERROR }
    public record Options(long timeoutMillis,int maxWorkers){
        public Options{if(timeoutMillis<1||timeoutMillis>86_400_000L||maxWorkers<0||maxWorkers>64)throw new IllegalArgumentException("Invalid Gradle build limits");}
    }
    public record Settings(Path javaHome,Path wrapperJar){public Settings{javaHome=Objects.requireNonNull(javaHome).toAbsolutePath().normalize();wrapperJar=Objects.requireNonNull(wrapperJar).toAbsolutePath().normalize();}}
    public record Result(Status status,int exitCode,String output,boolean truncated,List<Path> artifacts,BuildArtifacts.Inventory inventory){
        public Result{artifacts=List.copyOf(artifacts);inventory=Objects.requireNonNull(inventory);}
        public Result(Status status,int exitCode,String output,boolean truncated,List<Path> artifacts){this(status,exitCode,output,truncated,artifacts,BuildArtifacts.empty());}
    }
    public static final int MAX_OUTPUT_CHARS=256*1024;
    private final long timeoutMillis;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Genesis Gradle build");t.setDaemon(true);return t;});
    private final Deque<String> capture=new ArrayDeque<>(),live=new ArrayDeque<>();
    private int capturedChars;
    private boolean truncated,closed;
    private Future<Result> pending;
    private volatile AtomicBoolean cancellation;
    private volatile String phase="";
    public GradleBuildRunner(){this(600_000);}
    public GradleBuildRunner(long timeoutMillis){if(timeoutMillis<1||timeoutMillis>86_400_000)throw new IllegalArgumentException("Invalid Gradle timeout");this.timeoutMillis=timeoutMillis;}
    public static boolean validTask(String task){return task!=null&&task.length()<=192&&task.matches(":?[\\p{L}\\p{N}_$][\\p{L}\\p{N}_$.-]*(?::[\\p{L}\\p{N}_$][\\p{L}\\p{N}_$.-]*)*");}
    public static boolean validAbsolutePath(String path){try{return path!=null&&path.length()<=2048&&Path.of(path).isAbsolute();}catch(RuntimeException bad){return false;}}
    public boolean busy(){return pending!=null;}
    public String phase(){return phase;}
    public boolean start(Path project,Settings settings,String task){
        return start(project,settings,task,false,new Options(timeoutMillis,2));
    }
    /** Explicitly configures/evaluates the project to enumerate tasks, without invoking a selected build task. */
    public boolean startTaskListing(Path project,Settings settings){return start(project,settings,"tasks",true,new Options(timeoutMillis,2));}
    public boolean start(Path project,Settings settings,String task,Options options){return start(project,settings,task,false,options);}
    public boolean startTaskListing(Path project,Settings settings,Options options){return start(project,settings,"tasks",true,options);}
    private boolean start(Path project,Settings settings,String task,boolean listing,Options options){
        if(closed||pending!=null)return false;Objects.requireNonNull(project);Objects.requireNonNull(settings);Objects.requireNonNull(options);
        var token=new AtomicBoolean();
        synchronized(this){capture.clear();live.clear();capturedChars=0;truncated=false;cancellation=token;}
        phase="prepare";
        pending=worker.submit(()->execute(project.toAbsolutePath().normalize(),settings,task,token,listing,options));return true;
    }
    public void stop(){if(cancellation!=null)cancellation.set(true);}
    public void poll(java.util.function.Consumer<Result> completed){
        if(pending==null||!pending.isDone())return;var task=pending;pending=null;
        try{completed.accept(task.get());}catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(ExecutionException e){completed.accept(new Result(Status.IO_ERROR,-1,e.getCause().toString(),false,List.of()));}
    }
    public synchronized String drainOutput(){var text=new StringBuilder();for(int i=0;i<4&&!live.isEmpty();i++)text.append(live.removeFirst());return text.toString();}
    // A delayed reader from a completed job cannot contaminate the next build's logs.
    private synchronized void append(String text,AtomicBoolean token){if(cancellation==token)append(text);}
    private synchronized void append(String text){
        capture.addLast(text);capturedChars+=text.length();
        while(capturedChars>MAX_OUTPUT_CHARS){String first=capture.removeFirst();int drop=Math.min(first.length(),capturedChars-MAX_OUTPUT_CHARS);capturedChars-=drop;if(drop<first.length())capture.addFirst(first.substring(drop));truncated=true;}
        if(live.size()==32)live.removeFirst();live.addLast(text);
    }
    private synchronized Result result(Status status,int exit,List<Path> artifacts){return result(status,exit,artifacts,BuildArtifacts.empty());}
    private synchronized Result result(Status status,int exit,List<Path> artifacts,BuildArtifacts.Inventory inventory){var text=new StringBuilder();if(truncated)text.append("[Earlier output truncated]\n");for(String line:capture)text.append(line);return new Result(status,exit,text.toString(),truncated,artifacts,inventory);}
    private Result execute(Path project,Settings settings,String task,AtomicBoolean token,boolean listing,Options options){
        java.nio.channels.FileChannel lockChannel=null;java.nio.channels.FileLock projectLock=null;String buildId=UUID.randomUUID().toString(),inputs="";BuildInputs.Snapshot sourceSnapshot=null;BuildArtifacts.Inventory before=BuildArtifacts.empty();final long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(options.timeoutMillis());java.util.function.BooleanSupplier budget=()->token.get()||System.nanoTime()>=deadline;Process child=null;Set<ProcessHandle> descendants=ConcurrentHashMap.newKeySet();Thread reader=null,shutdown=null;boolean hook=false;Status status=Status.SUCCESS;
        try{
            if(token.get())return result(Status.CANCELLED,-1,List.of());
            if(!validTask(task))return invalid("Use one Gradle task, e.g. build, test, clean or :module:build");
            if(!Files.isDirectory(project)||!BuildTools.gradleRoot(project))return invalid("Project has no Gradle build or settings file");
            Path javaExecutable=settings.javaHome.resolve("bin").resolve(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java");
            if(!Files.isExecutable(javaExecutable))return invalid("Java executable not found in configured JDK home");
            try{BuildTools.validateWrapper(settings.wrapperJar);}catch(IOException invalid){return invalid(invalid.getMessage());}
            for(Path p=project;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))return invalid("Linked Gradle project is unsupported");
            Path state=project.resolve(".genesis-build");if(Files.isSymbolicLink(state))return invalid("Linked Gradle state is unsupported");Files.createDirectories(state);if(!state.toRealPath().startsWith(project.toRealPath())||Files.isSymbolicLink(state.resolve("build.lock")))return invalid("Gradle state escapes project");
            lockChannel=java.nio.channels.FileChannel.open(state.resolve("build.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            try{projectLock=lockChannel.tryLock();}catch(java.nio.channels.OverlappingFileLockException busy){return invalid("This project is already building in another editor");}if(projectLock==null)return invalid("This project is already building in another process");
            sourceSnapshot=BuildInputs.snapshot(project,budget);inputs=sourceSnapshot.fingerprint(List.of());before=listing?BuildArtifacts.empty():BuildArtifacts.scan(project,budget);
            for(String issue:GradleOutputs.prune(project))append("[Build] "+issue+"\n");Path init=GradleOutputs.script(state.resolve(buildId),buildId);
            var command=new ArrayList<>(List.of(javaExecutable.toString(),"-Xmx256m","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",settings.wrapperJar.toString(),"org.gradle.wrapper.GradleWrapperMain","--project-dir",project.toString(),"--no-daemon","--console=plain","--stacktrace","-Dorg.gradle.java.home="+settings.javaHome,task));
            if(!listing){command.add(command.size()-1,"--init-script");command.add(command.size()-1,init.toString());command.add(command.size()-1,"--no-configuration-cache");command.add(command.size()-1,"-Dgenesis.build.id="+buildId);command.add(command.size()-1,"-Dgenesis.build.root="+project);}
            if(options.maxWorkers()>0)command.add(command.size()-1,"--max-workers="+options.maxWorkers());
            if(listing)command.add("--all");
            var builder=new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true);
            for(String key:List.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS","JDK_JAVAC_OPTIONS","CLASSPATH","GRADLE_OPTS","JAVA_OPTS"))builder.environment().remove(key);
            builder.environment().put("JAVA_HOME",settings.javaHome.toString());builder.environment().put("GRADLE_USER_HOME",(project.getParent()==null?project:project.getParent()).resolve(".genesis-gradle-cache").toString());
            if(token.get())return result(Status.CANCELLED,-1,List.of());
            append("[Build] Project: "+project+"\n[Build] Launcher JDK: "+settings.javaHome+"\n[Build] Wrapper: "+settings.wrapperJar+"\n[Build] Task: "+task+"; timeout: "+options.timeoutMillis()+" ms; workers: "+(options.maxWorkers()==0?"project default":options.maxWorkers())+"\n");
            if(Files.isRegularFile(project.resolve("gradle/gradle-daemon-jvm.properties")))append("[Build] Gradle daemon JVM criteria are configured by the project; they take precedence over launcher JDK selection\n");
            phase=listing?"tasks":"build";child=builder.start();child.getOutputStream().close();final Process running=child;var error=new AtomicReference<IOException>();
            reader=new Thread(()->{try(var input=new InputStreamReader(running.getInputStream(),StandardCharsets.UTF_8)){char[] buffer=new char[4096];int n;while((n=input.read(buffer))!=-1){append(new String(buffer,0,n),token);}}catch(IOException bad){error.set(bad);}},"Genesis Gradle output");reader.setDaemon(true);
            shutdown=new Thread(()->terminate(running,descendants),"Genesis Gradle shutdown");Runtime.getRuntime().addShutdownHook(shutdown);hook=true;reader.start();
            boolean enumerate=true;
            while(child.isAlive()){
                if(enumerate)try(var stream=child.descendants()){stream.limit(1024).forEach(p->{if(descendants.size()<1024)descendants.add(p);});}catch(RuntimeException denied){enumerate=false;}
                if(token.get()||Thread.currentThread().isInterrupted()){status=Status.CANCELLED;break;}
                if(error.get()!=null){status=Status.IO_ERROR;break;}
                if(System.nanoTime()>=deadline){status=Status.TIMEOUT;break;}child.waitFor(25,TimeUnit.MILLISECONDS);
            }
            terminate(child,descendants);child.waitFor(2,TimeUnit.SECONDS);reader.join(1000);
            if(reader.isAlive()){child.getInputStream().close();reader.join(1000);}
            int exit=child.isAlive()?-1:child.exitValue();
            if(status==Status.SUCCESS){if(token.get())status=Status.CANCELLED;else if(error.get()!=null)status=Status.IO_ERROR;else if(exit!=0)status=Status.FAILED;}
            if(status==Status.SUCCESS&&!listing){
                BuildArtifacts.Inventory inventory;
                try{phase="artifacts";var excludes=GradleOutputs.reported(project,buildId)?GradleOutputs.inputExclusions(project,buildId):List.<String>of();inputs=sourceSnapshot.fingerprint(excludes);if(!BuildInputs.capture(project,budget,excludes).equals(inputs))throw new IOException("Project inputs changed during build; artifacts withheld");
                    final long artifactsDeadline=deadline;java.util.function.BooleanSupplier inspectionCancelled=()->token.get()||System.nanoTime()>=artifactsDeadline;
                    if(GradleOutputs.reported(project,buildId))inventory=BuildArtifacts.capture(project,GradleOutputs.outputs(project,buildId),inspectionCancelled);
                    else{var discovered=BuildArtifacts.scan(project,inspectionCancelled);var old=new HashSet<>(before.artifacts());inventory=new BuildArtifacts.Inventory(discovered.artifacts().stream().filter(a->!old.contains(a)).toList(),discovered.limited(),discovered.issues());append("[Artifacts] No Gradle output report: only newly created or changed archives are offered\n");}
                    Path dependencyConfig=project.resolve(ProjectDependencies.CONFIG);if(Files.isRegularFile(dependencyConfig)&&GradleOutputs.reported(project,buildId)){
                        var config=ProjectDependencies.parse(Files.readString(dependencyConfig));var runtimeJars=new ArrayList<Path>();Path runtimeFolder=project.resolve(".genesis-build").resolve(buildId).resolve("runtime");if(Files.isDirectory(runtimeFolder))try(var files=Files.newDirectoryStream(runtimeFolder,"*.jar")){for(Path file:files)runtimeJars.add(file);}if(!inventory.artifacts().isEmpty())ProjectDependencies.verifyResolved(config,runtimeJars);
                    }
                    final String provenance=inputs,invocation=GradleOutputs.reported(project,buildId)?buildId:"";inventory=new BuildArtifacts.Inventory(inventory.artifacts().stream().map(a->a.withBuild(provenance,invocation)).toList(),inventory.limited(),inventory.issues());
                    if(System.nanoTime()>=deadline)return result(Status.TIMEOUT,exit,List.of());
                }catch(IOException scanError){if(token.get())return result(Status.CANCELLED,exit,List.of());append("[Artifacts] "+scanError.getMessage()+"\n");return result(System.nanoTime()>=deadline?Status.TIMEOUT:Status.IO_ERROR,exit,List.of(),new BuildArtifacts.Inventory(List.of(),true,List.of(scanError.getMessage())));}
                if(token.get())return result(Status.CANCELLED,exit,List.of());
                if(inventory.limited())append("[Artifacts] Discovery limited; some outputs may be missing\n");for(String issue:inventory.issues())append("[Artifacts] "+issue+"\n");
                return result(status,exit,inventory.artifacts().stream().map(a->project.resolve(a.path())).toList(),inventory);
            }
            if(status==Status.TIMEOUT)append("[Build] Timeout after "+options.timeoutMillis()+" ms; increase this project's build timeout if required\n");
            if(status==Status.CANCELLED)append("[Build] Cancelled; child processes terminated\n");
            return result(status,exit,List.of());
        }catch(InterruptedException cancelled){Thread.currentThread().interrupt();token.set(true);return result(Status.CANCELLED,-1,List.of());}
        catch(IOException|RuntimeException bad){append(bad.toString()+"\n");return result(token.get()?Status.CANCELLED:System.nanoTime()>=deadline?Status.TIMEOUT:Status.IO_ERROR,-1,List.of());}
        finally{
            phase="cleanup";if(projectLock!=null)try{projectLock.close();}catch(IOException ignored){}if(lockChannel!=null)try{lockChannel.close();}catch(IOException ignored){}if(child!=null)terminate(child,descendants);
            if(hook)try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException ignored){}
            if(reader!=null&&reader.isAlive()&&child!=null)try{child.getInputStream().close();}catch(IOException ignored){}
            phase="finished";
        }
    }
    private Result invalid(String message){append(message+"\n");return result(Status.INPUT_ERROR,-1,List.of());}
    private static void terminate(Process child,Set<ProcessHandle> known){
        try(var stream=child.descendants()){stream.limit(1024).forEach(p->{if(known.size()<1024)known.add(p);if(p.isAlive())p.destroyForcibly();});}catch(RuntimeException ignored){}
        for(ProcessHandle p:known)try{if(p.isAlive())p.destroyForcibly();}catch(RuntimeException ignored){}
        if(child.isAlive())child.destroyForcibly();
    }
    @Override public void close(){if(closed)return;closed=true;stop();worker.shutdown();}
}
