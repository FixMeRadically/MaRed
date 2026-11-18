package com.fixmer.mared.services.threading;

import com.fixmer.genesis.technology.concurrent.ManagedTaskQueue111;
import com.fixmer.mared.Mared;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Bounded work, true interruption and terminal futures; UI callbacks never run after shutdown/cancellation. */
public final class AsyncTaskScheduler implements TaskScheduler {
    private final ThreadPoolExecutor pool;
    private final ManagedTaskQueue111 tasks;
    private final MainThreadDispatcher mainThread;
    private volatile boolean shutdown;
    public AsyncTaskScheduler(int poolSize,MainThreadDispatcher mainThread){
        this.mainThread=Objects.requireNonNull(mainThread);
        pool=new ThreadPoolExecutor(Math.max(1,poolSize),Math.max(1,poolSize),30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),new WorkerFactory(),new ThreadPoolExecutor.AbortPolicy());
        tasks=new ManagedTaskQueue111(pool);
    }
    @Override public TaskHandle submit(Runnable task){return submit("task",task);}
    @Override public TaskHandle submit(String name,Runnable task){
        Objects.requireNonNull(task);return submit(name,()->{task.run();return null;},null,null,null);
    }
    @Override public <T> TaskHandle submit(Callable<T> task,Consumer<T> onSuccess,Consumer<Throwable> onError){return submit("task",task,onSuccess,onError,null);}
    @Override public <T> TaskHandle submit(String name,Callable<T> body,Consumer<T> onSuccess,Consumer<Throwable> onError,CancellationToken token){
        if(shutdown)throw new IllegalStateException("scheduler is shut down");Objects.requireNonNull(body);
        if(token!=null&&token.isCancelled()){var future=new CompletableFuture<T>();future.cancel(false);return new TaskHandle(future);}
        String label=name==null||name.isEmpty()?"task":name;
        CompletableFuture<T> future=tasks.submit(()->{
            Thread thread=Thread.currentThread();String before=thread.getName();thread.setName(before+" ["+label+"]");
            try{if(token!=null)token.throwIfCancelled();return body.call();}finally{thread.setName(before);}
        });
        future.whenComplete((value,error)->{
            Throwable cause=error;while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof CancellationException||shutdown)return;
            if(cause!=null&&onError==null){Mared.LOGGER.error("[threading] task '{}' failed",label,cause);return;}
            if(cause==null&&onSuccess==null)return;Throwable failure=cause;
            try{mainThread.execute(()->{
                if(shutdown||future.isCancelled()||token!=null&&token.isCancelled())return;
                try{if(failure==null)onSuccess.accept(value);else onError.accept(failure);}
                catch(RuntimeException callback){Mared.LOGGER.error("[threading] callback '{}' failed",label,callback);}
            });}catch(RuntimeException dispatch){Mared.LOGGER.error("[threading] cannot dispatch '{}'",label,dispatch);}
        });
        return new TaskHandle(future);
    }
    @Override public void shutdown(){
        if(shutdown)return;shutdown=true;pool.shutdown();
        try{if(!pool.awaitTermination(2,TimeUnit.SECONDS)){tasks.cancelAll();pool.shutdownNow();}}
        catch(InterruptedException interrupted){tasks.cancelAll();pool.shutdownNow();Thread.currentThread().interrupt();}
    }
    @Override public boolean isShutdown(){return shutdown;}
    @Override public int activeTaskCount(){return tasks.outstanding();}
    @Override public int queueSize(){return pool.getQueue().size();}
    private static final class WorkerFactory implements ThreadFactory {
        private final AtomicInteger number=new AtomicInteger();
        @Override public Thread newThread(Runnable work){Thread thread=new Thread(work,"mared-worker-"+number.incrementAndGet());thread.setDaemon(true);thread.setPriority(Thread.NORM_PRIORITY-1);return thread;}
    }
}
