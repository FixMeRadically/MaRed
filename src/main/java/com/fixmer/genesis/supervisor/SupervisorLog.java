package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded diagnostics. A blocked console/disk must never hold the lifecycle or HTTP threads. */
final class SupervisorLog implements AutoCloseable {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm:ss");
    private record Stamp(long second,String text) { }
    private static volatile Stamp stamp;
    private static String timestamp(){
        long now=System.currentTimeMillis(),second=Math.floorDiv(now,1000);Stamp current=stamp;
        if(current==null||current.second()!=second){current=new Stamp(second,TIME.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())));stamp=current;}
        return current.text();
    }
    private static final int TAIL_MAX=4000;
    private static final int NEWLINE_BYTES=System.lineSeparator().length();
    private static final long MAX_BYTES=8L*1024*1024;
    private final Path file;
    private final Deque<String> tail=new ArrayDeque<>();
    private final BlockingQueue<String> output=new ArrayBlockingQueue<>(512);
    private final AtomicLong dropped=new AtomicLong();
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Thread sink;
    private BufferedWriter writer;
    private long fileBytes,nextFlush=System.nanoTime();
    SupervisorLog(Path file){
        this.file=file;sink=new Thread(this::drain,"supervisor-log-output");sink.setDaemon(true);sink.start();
    }
    void info(String msg){write("INFO ",msg);}void warn(String msg){write("WARN ",msg);}
    void error(String msg){write("ERROR",msg);}void child(String msg){write("MC   ",msg);}
    private void write(String level,String message){
        if(closed.get())return;
        String msg=message==null?"null":message;
        if(msg.length()>16384)msg=DiagnosticText.prefix(msg,16384)+" [truncated]";
        String line="["+timestamp()+"] ["+level+"] "+msg;
        synchronized(tail){tail.addLast(line);if(tail.size()>TAIL_MAX)tail.removeFirst();}
        if(!output.offer(line))dropped.incrementAndGet();
    }
    private void drain(){
        try{
            if(file!=null)try{
                if(file.getParent()!=null)Files.createDirectories(file.getParent());
                fileBytes=Files.exists(file)?Files.size(file):0;
                writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }catch(IOException ignored){writer=null;}
            while(!closed.get()||!output.isEmpty()){
                String line;
                try{line=output.poll(50,TimeUnit.MILLISECONDS);}catch(InterruptedException e){continue;}
                if(line==null){flushIfDue();continue;}
                long lost=dropped.getAndSet(0);if(lost>0)emit("[supervisor] Diagnostic output dropped "+lost+" lines while its bounded queue was full");
                emit(line);
                int batch=0;while(batch++<63&&(line=output.poll())!=null)emit(line);
                flushIfDue();
            }
        }finally{disableFile();}
    }
    private void flushIfDue(){
        long now=System.nanoTime();if(writer!=null&&now-nextFlush>=0){
            nextFlush=now+100_000_000L;try{writer.flush();}catch(IOException e){disableFile();}
        }
    }
    private void emit(String line){
        if(!Boolean.getBoolean("genesis.supervisor.quiet"))System.out.println(line);
        if(writer==null)return;
        try{
            int bytes=line.getBytes(StandardCharsets.UTF_8).length+NEWLINE_BYTES;
            if(fileBytes+bytes>MAX_BYTES){
                writer.close();Files.move(file,file.resolveSibling(file.getFileName()+".1"),StandardCopyOption.REPLACE_EXISTING);
                writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);fileBytes=0;
            }
            writer.write(line);writer.newLine();fileBytes+=bytes;
        }catch(IOException e){disableFile();}
    }
    private void disableFile(){if(writer!=null){try{writer.close();}catch(IOException ignored){}writer=null;}}
    List<String> tail(int n){synchronized(tail){int skip=Math.max(0,tail.size()-Math.max(0,n));var out=new ArrayList<String>();int i=0;for(String line:tail)if(i++>=skip)out.add(line);return out;}}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        boolean interrupted=Thread.interrupted();
        try{if(Thread.currentThread()!=sink)sink.join(1500);}catch(InterruptedException e){interrupted=true;}
        finally{if(interrupted)Thread.currentThread().interrupt();}
        // A blocked output sink is a daemon; bootstrap main exits after ownership is released.
    }
}
