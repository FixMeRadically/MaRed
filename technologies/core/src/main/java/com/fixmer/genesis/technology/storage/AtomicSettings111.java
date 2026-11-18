package com.fixmer.genesis.technology.storage;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/** One writer per target inside this JVM; unique temporary files and bounded Windows sharing retries. */
public final class AtomicSettings111 {
    private static final ReentrantLock[] LOCKS=new ReentrantLock[64];
    private static final boolean WINDOWS=System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows");
    static{for(int i=0;i<LOCKS.length;i++)LOCKS[i]=new ReentrantLock();}
    private AtomicSettings111(){}
    public static void write(Path target,String text)throws IOException{
        target=target.toAbsolutePath().normalize();Path parent=target.getParent();Files.createDirectories(parent);
        // Parent aliases and Windows case aliases must select the same bounded lock stripe.
        target=parent.toRealPath().resolve(target.getFileName());parent=target.getParent();
        ReentrantLock lock=LOCKS[Math.floorMod(target.hashCode(),LOCKS.length)];
        try{lock.lockInterruptibly();}catch(InterruptedException interrupted){throw interrupted(interrupted);}
        try{
            Path temporary=Files.createTempFile(parent,".mared-settings-",".tmp");
            IOException failure=null;
            try{
                byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
                try(var file=FileChannel.open(temporary,StandardOpenOption.WRITE)){ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())file.write(data);file.force(true);}
                replace(temporary,target,WINDOWS,Files::move,Thread::sleep);
            }catch(IOException bad){failure=bad;throw bad;}
            finally{
                try{Files.deleteIfExists(temporary);}catch(IOException cleanup){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}
            }
        }finally{lock.unlock();}
    }
    @FunctionalInterface interface Move {void run(Path from,Path to,CopyOption... options)throws IOException;}
    @FunctionalInterface interface Pause {void run(long millis)throws InterruptedException;}
    /** Fault seam for tests; no public retry knobs and no weakening of a normal I/O failure. */
    static void replace(Path from,Path to,boolean retrySharing,Move move,Pause pause)throws IOException{
        for(int attempt=0;;attempt++){
            try{
                try{move.run(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException unsupported){move.run(from,to,StandardCopyOption.REPLACE_EXISTING);}
                return;
            }catch(AccessDeniedException denied){
                // No deletion/copy workaround: permanent ACL/read-only errors remain errors after a short budget.
                if(!retrySharing||attempt>=5||Files.isDirectory(to,LinkOption.NOFOLLOW_LINKS))throw denied;
                try{pause.run(10L<<attempt);}catch(InterruptedException interrupted){InterruptedIOException stopped=interrupted(interrupted);stopped.addSuppressed(denied);throw stopped;}
            }
        }
    }
    private static InterruptedIOException interrupted(InterruptedException cause){Thread.currentThread().interrupt();InterruptedIOException error=new InterruptedIOException("Settings write interrupted");error.initCause(cause);return error;}
}
