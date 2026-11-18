package com.fixmer.genesis.technology.storage;
import java.nio.file.*;import java.util.*;import java.io.*;import java.util.concurrent.*;
/** Real replacement/concurrent files plus explicit injected sharing/ACL/interrupt faults. Not a Windows certification. */
public final class AtomicSettingsChecks114 {
    static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("atomic114 "+checks);}
    static Path root,source,target;
    static void reset()throws Exception{Files.writeString(source,"new");Files.writeString(target,"old");}
    static AccessDeniedException denied(){return new AccessDeniedException(source.toString(),target.toString(),"injected sharing/ACL fault");}
    public static void main(String[] args)throws Exception{
        root=Files.createTempDirectory("Genesis atomic114 ");source=root.resolve("candidate.tmp");target=root.resolve("settings.json");
        try{
            reset();int[] calls={0};List<Long> waits=new ArrayList<>();
            AtomicSettings111.replace(source,target,true,(a,b,options)->{calls[0]++;ok(Files.readString(b).equals("old"));if(calls[0]<3)throw denied();Files.move(a,b,options);},waits::add);
            ok(calls[0]==3);ok(waits.equals(List.of(10L,20L)));ok(Files.readString(target).equals("new"));ok(!Files.exists(source));
            reset();calls[0]=0;waits.clear();
            try{AtomicSettings111.replace(source,target,true,(a,b,o)->{calls[0]++;throw denied();},waits::add);throw new AssertionError("permanent denial ignored");}catch(AccessDeniedException expected){checks++;}
            ok(calls[0]==6);ok(waits.equals(List.of(10L,20L,40L,80L,160L)));ok(Files.readString(target).equals("old"));ok(Files.readString(source).equals("new"));
            calls[0]=0;waits.clear();try{AtomicSettings111.replace(source,target,false,(a,b,o)->{calls[0]++;throw denied();},waits::add);throw new AssertionError();}catch(AccessDeniedException expected){checks++;}ok(calls[0]==1&&waits.isEmpty());
            calls[0]=0;waits.clear();try{AtomicSettings111.replace(source,target,true,(a,b,o)->{calls[0]++;throw new IOException("disk fault");},waits::add);throw new AssertionError();}catch(IOException expected){ok(expected.getMessage().equals("disk fault"));}ok(calls[0]==1&&waits.isEmpty());ok(Files.readString(target).equals("old"));
            calls[0]=0;AtomicSettings111.replace(source,target,true,(a,b,o)->{calls[0]++;if(calls[0]==1)throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"injected unsupported provider");ok(!Arrays.asList(o).contains(StandardCopyOption.ATOMIC_MOVE));Files.move(a,b,o);},millis->{throw new AssertionError("unsupported atomic move must not wait");});ok(calls[0]==2);ok(Files.readString(target).equals("new"));
            reset();try{AtomicSettings111.replace(source,target,true,(a,b,o)->{throw denied();},millis->{throw new InterruptedException("cancel");});throw new AssertionError();}catch(InterruptedIOException expected){ok(Thread.currentThread().isInterrupted());ok(expected.getSuppressed().length==1);}finally{Thread.interrupted();}ok(Files.readString(target).equals("old"));
            // The original failure case: several real writers repeatedly replace an existing target.
            var workers=Executors.newFixedThreadPool(8);List<String> payloads=new ArrayList<>();List<Future<?>> futures=new ArrayList<>();
            try{for(int i=0;i<64;i++){String payload=i+"\n"+"Русский 😀\n".repeat(1000);payloads.add(payload);futures.add(workers.submit(()->{AtomicSettings111.write(target,payload);return null;}));}for(var f:futures)f.get(15,TimeUnit.SECONDS);ok(payloads.contains(Files.readString(target)));}
            finally{workers.shutdownNow();ok(workers.awaitTermination(5,TimeUnit.SECONDS));}
            try(var paths=Files.list(root)){ok(paths.noneMatch(p->p.getFileName().toString().startsWith(".mared-settings-")));}
            Path directory=root.resolve("directory.json");Files.createDirectory(directory);Files.writeString(directory.resolve("keep"),"keep");try{AtomicSettings111.write(directory,"bad");throw new AssertionError();}catch(IOException expected){checks++;}ok(Files.readString(directory.resolve("keep")).equals("keep"));
            try(var paths=Files.list(root)){ok(paths.noneMatch(p->p.getFileName().toString().startsWith(".mared-settings-")));}
            Thread.currentThread().interrupt();try{AtomicSettings111.write(target,"must not replace");throw new AssertionError();}catch(InterruptedIOException expected){ok(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}ok(payloads.contains(Files.readString(target)));
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("Atomic114 real concurrent replacement/whole-UTF8/cleanup and injected sharing/ACL/fallback/interruption checks passed: "+checks+"; Windows runtime not exercised here.");
    }
}
