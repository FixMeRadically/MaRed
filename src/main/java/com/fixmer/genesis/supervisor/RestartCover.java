package com.fixmer.genesis.supervisor;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** A fresh visible desktop cover is required before the game may become transparent. */
public final class RestartCover {
    private RestartCover() { }
    /** File heartbeat does not depend on Swing repaint/event-loop delays. */
    static final class Publisher implements AutoCloseable {
        private final Path file;
        private final java.util.concurrent.ScheduledExecutorService worker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"genesis-cover-heartbeat");t.setDaemon(true);return t;});
        private RestartSurface surface;
        private boolean active,closed;
        Publisher(Path file){this.file=file;worker.scheduleWithFixedDelay(this::heartbeat,400,400,java.util.concurrent.TimeUnit.MILLISECONDS);}
        synchronized void show(RestartSurface value){if(closed)return;surface=value;active=value!=null;heartbeat();}
        synchronized void hide(){active=false;if(surface!=null)write(file,surface.generation(),false,System.currentTimeMillis());}
        synchronized void heartbeat(){if(active&&!closed)write(file,surface.generation(),true,System.currentTimeMillis());}
        @Override public synchronized void close(){if(closed)return;hide();closed=true;worker.shutdownNow();}
    }
    public static Path path(Path surface){return surface.resolveSibling("restart-cover.properties");}
    public enum State { VISIBLE, HIDDEN, UNAVAILABLE }
    public static boolean visible(Path surface,String generation,long now) {return state(surface,generation,now)==State.VISIBLE;}
    public static State state(Path surface,String generation,long now) {
        try {
            Path file=path(surface);RuntimeFiles.safe(file);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>2048)return State.UNAVAILABLE;
            Properties p=new Properties();try(var in=Files.newBufferedReader(file)){p.load(in);}
            long time=Long.parseLong(p.getProperty("heartbeat")),age=now-time;
            if(!"1".equals(p.getProperty("schema"))||!generation.equals(p.getProperty("generation"))||age<0||age>300000)return State.UNAVAILABLE;
            if("false".equals(p.getProperty("visible")))return State.HIDDEN;
            return "true".equals(p.getProperty("visible"))&&age<=2000?State.VISIBLE:State.UNAVAILABLE;
        }catch(IOException|RuntimeException invalid){return State.UNAVAILABLE;}
    }
    static void write(Path surface,String generation,boolean visible,long now) {
        Path temporary=null;
        try {
            Path file=path(surface);RuntimeFiles.safe(file);Files.createDirectories(file.getParent());
            Properties p=new Properties();p.setProperty("schema","1");p.setProperty("generation",generation);p.setProperty("visible",Boolean.toString(visible));p.setProperty("heartbeat",Long.toString(now));
            temporary=Files.createTempFile(file.getParent(),".cover-",".tmp");try(var out=Files.newBufferedWriter(temporary)){p.store(out,"Genesis desktop cover availability");}
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException|RuntimeException ignored){}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
}
