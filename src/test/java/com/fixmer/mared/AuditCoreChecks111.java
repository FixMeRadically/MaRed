package com.fixmer.mared;
import com.fixmer.genesis.technology.concurrent.ManagedTaskQueue111;
import com.fixmer.genesis.technology.storage.AtomicSettings111;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real executor/file tests. No Minecraft, renderer or Windows focus simulation. */
public final class AuditCoreChecks111 {
    private static int checks;
    static void ok(boolean value){checks++;if(!value)throw new AssertionError("audit111 check "+checks);}
    static void await(Callable<Boolean> condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.call()){if(System.nanoTime()>end)throw new AssertionError("audit111 timeout");Thread.sleep(2);}
    }
    public static void main(String[] args)throws Exception{
        ThreadPoolExecutor pool=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1));
        ManagedTaskQueue111 tasks=new ManagedTaskQueue111(pool);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicBoolean interrupted=new AtomicBoolean();
        try{
            var first=tasks.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){interrupted.set(true);throw e;}return 1;});
            ok(entered.await(5,TimeUnit.SECONDS));
            AtomicBoolean ran=new AtomicBoolean();var queued=tasks.submit(()->{ran.set(true);return 2;});ok(tasks.outstanding()==2);
            try{tasks.submit(()->3);throw new AssertionError("queue accepted overflow");}catch(RejectedExecutionException expected){checks++;}
            ok(tasks.outstanding()==2);ok(queued.cancel(true));await(()->tasks.outstanding()==1);ok(pool.getQueue().isEmpty());ok(!ran.get());
            ok(first.cancel(true));await(interrupted::get);await(()->tasks.outstanding()==0);ok(first.isCancelled());
            var failure=tasks.submit(()->{throw new IllegalArgumentException("expected");});
            try{failure.get(5,TimeUnit.SECONDS);throw new AssertionError("exception swallowed");}catch(ExecutionException expected){ok(expected.getCause() instanceof IllegalArgumentException);}
            await(()->tasks.outstanding()==0);
            for(int i=0;i<500;i++){
                var f=tasks.submit(()->7);if((i&1)==0)f.cancel(true);else ok(f.get(5,TimeUnit.SECONDS)==7);
                await(()->tasks.outstanding()==0);
            }
            CountDownLatch started=new CountDownLatch(1);var active=tasks.submit(()->{started.countDown();new CountDownLatch(1).await();return 0;});ok(started.await(5,TimeUnit.SECONDS));
            var waiting=tasks.submit(()->0);tasks.cancelAll();await(()->tasks.outstanding()==0);ok(active.isCancelled()&&waiting.isCancelled());
        }finally{release.countDown();tasks.cancelAll();pool.shutdownNow();ok(pool.awaitTermination(5,TimeUnit.SECONDS));}
        try{tasks.submit(()->1);throw new AssertionError("shutdown accepted");}catch(RejectedExecutionException expected){checks++;}ok(tasks.outstanding()==0);
        Path root=Files.createTempDirectory("Genesis audit111 ");ExecutorService writers=Executors.newFixedThreadPool(4);
        try{
            Path target=root.resolve("settings.json"),stale=root.resolve("settings.json.tmp");Files.writeString(stale,"leave alone");Files.writeString(target,"existing before concurrent replacement");
            List<String> payloads=new ArrayList<>();List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<32;i++){String text="{\"index\":"+i+",\"text\":\""+"Русский 😀 ".repeat(1000)+"\"}";payloads.add(text);futures.add(writers.submit(()->{AtomicSettings111.write(target,text);return null;}));}
            for(var f:futures)f.get(15,TimeUnit.SECONDS);ok(payloads.contains(Files.readString(target)));ok(Files.readString(stale).equals("leave alone"));
            try(var list=Files.list(root)){ok(list.noneMatch(p->p.getFileName().toString().startsWith(".mared-settings-")));}
            Path directory=root.resolve("directory.json");Files.createDirectories(directory);Files.writeString(directory.resolve("keep"),"original");
            try{AtomicSettings111.write(directory,"bad");throw new AssertionError("replaced nonempty directory");}catch(java.io.IOException expected){checks++;}
            ok(Files.readString(directory.resolve("keep")).equals("original"));
            try(var list=Files.list(root)){ok(list.noneMatch(p->p.getFileName().toString().startsWith(".mared-settings-")));}
        }finally{writers.shutdownNow();writers.awaitTermination(5,TimeUnit.SECONDS);try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("Audit111 executor/interrupt/rejection/accounting/atomic UTF-8 checks passed: "+checks);
    }
}
