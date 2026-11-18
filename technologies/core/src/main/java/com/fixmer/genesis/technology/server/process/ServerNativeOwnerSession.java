package com.fixmer.genesis.technology.server.process;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.fixmer.genesis.technology.server.process.ServerNativeOwnerProtocol.*;

/** Guardian-side diagnostic owner connection. No production game launcher factory. */
final class ServerNativeOwnerSession implements AutoCloseable {
    private final Process owner;
    private final Binding binding;
    private final BlockingQueue<String> replies=new ArrayBlockingQueue<>(8);
    private final AtomicReference<String> readFailure=new AtomicReference<>();
    private final long timeoutMs;
    private Event event;
    private long sequence;
    private boolean poisoned,closed,gracefulStopRequested,cleanFinished;
    private ServerNativeOwnerSession(Process owner,Binding binding,long timeoutMs)throws IOException {
        this.owner=owner;this.binding=binding;this.timeoutMs=timeoutMs;
        Thread stdout=new Thread(this::read,"genesis-native-owner-output");stdout.setDaemon(true);stdout.start();
        Thread stderr=new Thread(()->{try(var input=owner.getErrorStream()){byte[] buffer=new byte[1024];while(input.read(buffer)>=0){/* Drain; never expose helper stderr/nonce to server status. */}}catch(IOException ignored){}},"genesis-native-owner-error");stderr.setDaemon(true);stderr.start();
        try{write(hello(binding));event=receive(0);if(event.phase()!=Phase.NEW)throw new IOException("NATIVE_HANDSHAKE_STATE");}
        catch(IOException bad){poison();throw bad;}
    }
    static ServerNativeOwnerSession diagnostic(Path helper,Binding binding)throws IOException {
        if(!System.getProperty("os.name","").startsWith("Windows"))throw new IOException("NATIVE_WINDOWS_REQUIRED");
        helper=helper.toAbsolutePath().normalize();ServerFiles.safe(helper);
        if(!Files.isRegularFile(helper)||Files.size(helper)>32L*1024*1024||!helper.getFileName().toString().endsWith(".exe"))throw new IOException("NATIVE_INVALID_HELPER");
        return new ServerNativeOwnerSession(new ProcessBuilder(helper.toString(),"--owner-session").start(),binding,10_000);
    }
    synchronized Event start()throws IOException{return send(Verb.START);}
    synchronized Event status()throws IOException{return send(Verb.STATUS);}
    synchronized Event stop()throws IOException{Event result=send(Verb.STOP);gracefulStopRequested=true;return result;}
    synchronized Event force(boolean confirmed)throws IOException{if(!confirmed)throw new IOException("NATIVE_FORCE_CONFIRMATION_REQUIRED");return send(Verb.FORCE);}
    synchronized Event awaitEmpty(long timeout)throws IOException {
        if(timeout<1||timeout>60_000)throw new IllegalArgumentException("Invalid drain deadline");long start=System.nanoTime();
        while(event.phase()!=Phase.EMPTY){long remaining=timeout-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);if(remaining<=0)throw new IOException("NATIVE_STOP_TIMEOUT");send(Verb.STATUS,Math.min(timeoutMs,remaining));if(event.phase()!=Phase.EMPTY)try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("NATIVE_WAIT_INTERRUPTED");}}
        return event;
    }
    synchronized void finish()throws IOException {
        if(poisoned||closed)throw new IOException("NATIVE_SESSION_UNAVAILABLE");
        Event result=send(Verb.CLOSE);
        try{if(!owner.waitFor(timeoutMs,TimeUnit.MILLISECONDS)||owner.exitValue()!=0)throw new IOException("NATIVE_OWNER_EXIT_UNCONFIRMED");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();poison();throw new IOException("NATIVE_WAIT_INTERRUPTED");}
        catch(IOException failure){poison();throw failure;}
        cleanFinished=gracefulStopRequested&&result.pid()>0&&result.active()==0&&result.exitCode()==0&&!result.forced();closed=true;
        closeStreams();
    }
    synchronized void disconnectOwnerPipe()throws IOException {
        poisoned=true;cleanFinished=false;owner.getOutputStream().close();
        try{if(!owner.waitFor(timeoutMs,TimeUnit.MILLISECONDS)){owner.destroyForcibly();throw new IOException("NATIVE_OWNER_PIPE_CLOSE_TIMEOUT");}}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();owner.destroyForcibly();throw new IOException("NATIVE_WAIT_INTERRUPTED");}
        closed=true;closeStreams();
    }
    synchronized Map<String,Object> snapshot(){
        boolean unknown=poisoned||!closed&&(!owner.isAlive()||readFailure.get()!=null);
        var result=new LinkedHashMap<String,Object>();result.put("channelAvailable",!unknown&&!closed);result.put("historical",closed);result.put("report","native_owner_v44");result.put("run",binding.run().toString());result.put("epoch",binding.epoch());result.put("sequence",sequence);
        result.put("phase",unknown?"UNKNOWN":event==null?"HANDSHAKE":event.phase().name());result.put("childPid",event==null?0:event.pid());result.put("activeProcesses",unknown?-1:event==null?-1:event.active());result.put("exitCode",event==null?-1:event.exitCode());result.put("forced",event!=null&&event.forced());result.put("gracefulDiagnosticComplete",cleanFinished);result.put("installAuthority",false);result.put("liveContainmentProof",false);return result;
    }
    private Event send(Verb verb)throws IOException {return send(verb,timeoutMs);}
    private Event send(Verb verb,long budget)throws IOException {
        if(poisoned||closed||sequence==Long.MAX_VALUE)throw new IOException("NATIVE_SESSION_UNAVAILABLE");
        boolean allowed=switch(verb){case START->event.phase()==Phase.NEW;case STOP->event.phase()==Phase.RUNNING||event.phase()==Phase.EMPTY;case FORCE->event.phase()==Phase.RUNNING||event.phase()==Phase.STOPPING||event.phase()==Phase.EMPTY;case CLOSE->event.phase()==Phase.NEW||event.phase()==Phase.EMPTY;case STATUS->event.phase()!=Phase.CLOSED;};
        if(!allowed)throw new IOException("NATIVE_COMMAND_NOT_ALLOWED");
        try{long next=sequence+1;write(command(binding,next,verb));Event received=receive(next,budget);transition(event,received,verb);event=received;sequence=next;return event;}
        catch(IOException bad){poison();throw bad;}
    }
    private void write(String line)throws IOException {byte[] bytes=(line+"\n").getBytes(StandardCharsets.US_ASCII);owner.getOutputStream().write(bytes);owner.getOutputStream().flush();}
    private Event receive(long expected)throws IOException {return receive(expected,timeoutMs);}
    private Event receive(long expected,long budget)throws IOException {
        try{String line=replies.poll(budget,TimeUnit.MILLISECONDS);if(line==null)throw new IOException(readFailure.get()==null?"NATIVE_RESPONSE_TIMEOUT":readFailure.get());Event received=ServerNativeOwnerProtocol.event(line,binding,expected);String failure=readFailure.get();
            if(failure!=null&&(!failure.equals("NATIVE_OWNER_EOF")||received.phase()!=Phase.CLOSED))throw new IOException(failure);
            return received;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("NATIVE_WAIT_INTERRUPTED");}
    }
    private void read(){
        try(var input=owner.getInputStream()){
            var line=new ByteArrayOutputStream();int value;
            while((value=input.read())>=0){
                if(value=='\n'){if(!replies.offer(line.toString(StandardCharsets.US_ASCII)))throw new IOException("NATIVE_OUTPUT_FLOOD");line.reset();}
                else{if(value<32||value>126||line.size()>=MAX_LINE)throw new IOException("NATIVE_INVALID_FRAME");line.write(value);}
            }
            readFailure.compareAndSet(null,"NATIVE_OWNER_EOF");
        }catch(IOException bad){readFailure.compareAndSet(null,"NATIVE_OUTPUT_FAILED");}
    }
    private void poison(){poisoned=true;cleanFinished=false;owner.destroyForcibly();closeStreams();}
    private void closeStreams(){
        try{owner.getOutputStream().close();}catch(IOException ignored){}
        // Stream close can wait behind a blocked reader if a broken helper leaked a
        // pipe to another process. Never block the guardian thread on that lock.
        Thread closer=new Thread(()->{try{owner.getInputStream().close();}catch(IOException ignored){}try{owner.getErrorStream().close();}catch(IOException ignored){}},"genesis-native-pipe-close");closer.setDaemon(true);closer.start();
    }
    @Override public synchronized void close(){if(!closed){if(event!=null&&!poisoned&&(event.phase()==Phase.EMPTY||event.phase()==Phase.NEW))try{finish();return;}catch(IOException ignored){}poison();closed=true;}}
}
