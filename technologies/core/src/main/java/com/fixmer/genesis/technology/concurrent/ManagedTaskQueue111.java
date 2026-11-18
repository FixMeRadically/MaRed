package com.fixmer.genesis.technology.concurrent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Futures, real interrupts and accounting share one lifecycle, including queued cancellation/rejection. */
public final class ManagedTaskQueue111 {
    private final ThreadPoolExecutor executor;
    private final AtomicInteger outstanding=new AtomicInteger();
    private final Set<FutureTask<?>> live=ConcurrentHashMap.newKeySet();
    public ManagedTaskQueue111(ThreadPoolExecutor executor){this.executor=Objects.requireNonNull(executor);}
    public int outstanding(){return outstanding.get();}
    public <T> CompletableFuture<T> submit(Callable<T> body){
        Objects.requireNonNull(body);var result=new CompletableFuture<T>();var stage=new AtomicInteger();
        final class Work extends FutureTask<T> {
            Work(){super(body);}
            void finish(){live.remove(this);outstanding.decrementAndGet();}
            @Override public void run(){
                if(!stage.compareAndSet(0,1))return;
                try{super.run();}finally{stage.set(2);finish();}
            }
            @Override protected void done(){
                if(stage.compareAndSet(0,2))finish();
                if(isCancelled()){result.cancel(false);return;}
                try{result.complete(get());}
                catch(ExecutionException error){result.completeExceptionally(error.getCause());}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();result.completeExceptionally(interrupted);}
                catch(CancellationException cancelled){result.cancel(false);}
            }
        }
        Work work=new Work();outstanding.incrementAndGet();live.add(work);
        result.whenComplete((value,error)->{if(result.isCancelled()){work.cancel(true);executor.remove(work);}});
        try{executor.execute(work);}catch(RuntimeException rejected){work.cancel(false);throw rejected;}
        return result;
    }
    public void cancelAll(){for(FutureTask<?> task:live)task.cancel(true);executor.purge();}
}
