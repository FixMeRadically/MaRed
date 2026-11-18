package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Kept open through all shutdown hooks; only process termination releases a successful client lease. */
final class GameLease {
    enum State { HELD, RELEASED, UNKNOWN }
    private static final Map<Path,Lease> LOCAL=new HashMap<>();
    static final class Lease implements AutoCloseable {
        final Path file;final String token;final FileChannel channel;final FileLock lock;
        Lease(Path file,String token,FileChannel channel,FileLock lock){this.file=file;this.token=token;this.channel=channel;this.lock=lock;}
        boolean valid(){return channel.isOpen()&&lock.isValid();}
        /** Initialization failure and tests only. Successful clients never call this during shutdown. */
        @Override public void close()throws IOException{
            synchronized(GameLease.class){
                try{try{if(lock.isValid())lock.release();}finally{channel.close();}}
                finally{LOCAL.remove(file,this);}
            }
        }
    }
    private GameLease() { }
    static String marker(String launch,String token,long pid,long start){return "genesis-game-lease-1\n"+launch+"\n"+token+"\n"+pid+"\n"+start+"\n";}
    static synchronized Lease hold(Path path,String launch,long pid,long start){
        FileChannel channel=null;boolean created=false;
        try{
            Path file=path.toAbsolutePath().normalize();RuntimeFiles.safe(file);
            channel=FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);created=true;
            String token=UUID.randomUUID().toString();ByteBuffer bytes=StandardCharsets.US_ASCII.encode(marker(launch,token,pid,start));while(bytes.hasRemaining())channel.write(bytes);
            FileLock lock=channel.tryLock();if(lock==null)throw new IOException("Lease unavailable");
            Lease lease=new Lease(file,token,channel,lock);LOCAL.put(file,lease);return lease;
        }catch(IOException|RuntimeException e){
            if(channel!=null)try{channel.close();}catch(IOException ignored) { }
            if(created)try{Files.deleteIfExists(path);}catch(IOException ignored) { }
            return null;
        }
    }
    static synchronized State probe(Path path,String expected){
        Path file=path.toAbsolutePath().normalize();
        // Closing a second descriptor can release process-wide POSIX locks. Never probe our own live lease.
        Lease local=LOCAL.get(file);if(local!=null&&local.valid())return State.HELD;
        try{
            RuntimeFiles.safe(file);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>512)return State.UNKNOWN;
            try(FileChannel channel=FileChannel.open(file,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                try(FileLock lock=channel.tryLock()){
                    if(lock==null)return State.HELD;
                    ByteBuffer bytes=ByteBuffer.allocate(513);int n;while(bytes.hasRemaining()&&(n=channel.read(bytes))>0) { }
                    bytes.flip();return StandardCharsets.US_ASCII.decode(bytes).toString().equals(expected)?State.RELEASED:State.UNKNOWN;
                }
            }
        }catch(OverlappingFileLockException e){return State.HELD;}
        catch(IOException|RuntimeException e){return State.UNKNOWN;}
    }
}
