package com.fixmer.mared.technology.scripts;

import com.fixmer.genesis.technology.scripts.*;
import com.fixmer.genesis.technology.scripts.SourceProjects.Recovery;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One explicit local run per editor session. Never uses MR, Minecraft commands or network packets. */
public final class JavaProjectRuns implements AutoCloseable {
    private final SourceProjects projects;
    private final JavaSourceRunner runner;
    private final JavaSourceRunner.Limits limits;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Genesis Java projects");t.setDaemon(true);return t;});
    private Future<JavaSourceRunner.Result> pending;
    private JavaSourceRunner.Cancellation cancellation;
    private boolean closed;
    private String project="";
    public JavaProjectRuns(SourceProjects projects){this(projects,null,JavaSourceRunner.Limits.defaults());}
    public JavaProjectRuns(SourceProjects projects,JavaSourceRunner runner,JavaSourceRunner.Limits limits){this.projects=projects;this.runner=runner;this.limits=limits;}
    public boolean busy(){return pending!=null;}
    public String phase(){return cancellation==null?"":cancellation.phase();}
    public String project(){return project;}
    public boolean start(String id,String mainClass,Map<String,Recovery> edits){
        if(closed||pending!=null)return false;var snapshot=Map.copyOf(edits);var token=new JavaSourceRunner.Cancellation();cancellation=token;project=id;
        pending=worker.submit(()->{
            if(token.cancelled())return new JavaSourceRunner.Result(JavaSourceRunner.Status.CANCELLED,"prepare",-1,"");
            try {Map<String,String> sources;Map<String,byte[]> libraries,resources;
                synchronized(projects){sources=projects.javaSources(id,snapshot);libraries=projects.javaLibraries(id);resources=projects.javaResources(id);}
                return (runner==null?new JavaSourceRunner(projects.javaHome()):runner).run(sources,libraries,resources,mainClass,limits,token);}
            catch(Exception e){return new JavaSourceRunner.Result(JavaSourceRunner.Status.INPUT_ERROR,"prepare",-1,e.getMessage()==null?e.toString():e.getMessage());}
        });return true;
    }
    public void stop(){if(cancellation!=null)cancellation.cancel();}
    public void poll(Consumer<JavaSourceRunner.Result> completed){
        if(pending==null||!pending.isDone())return;var task=pending;pending=null;
        try{completed.accept(task.get());}catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(ExecutionException e){completed.accept(new JavaSourceRunner.Result(JavaSourceRunner.Status.IO_ERROR,"prepare",-1,e.getCause().toString()));}
    }
    @Override public void close(){if(closed)return;closed=true;stop();worker.shutdown();}
}
