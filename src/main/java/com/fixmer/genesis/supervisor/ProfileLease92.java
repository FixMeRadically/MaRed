package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.channels.*;import java.nio.file.*;import java.util.*;
/** Cooperative profile admission: games hold shared leases through all shutdown hooks; recovery writes exclusively. */
public final class ProfileLease92 {
 private record Held(FileChannel channel,FileLock lock,Thread owner){}
 private static final Map<Path,Held> WRITERS=new HashMap<>();
 private ProfileLease92(){}
 private static Path file(Path game)throws IOException{Path p=game.toAbsolutePath().normalize().resolve("config/mared/dev/profile92.lock");RuntimeFiles.safe(p);Files.createDirectories(p.getParent());return p;}
 public static void start(Path game)throws IOException{
  synchronized(ProfileLease92.class){if(WRITERS.containsKey(file(game)))throw new IOException("PROFILE92_IN_USE");}
  com.fixmer.genesis.admission.ProfileAgent93.require(game,System.getenv(ProfileLaunch93.REQUIRED));
 }
 static synchronized boolean ownsWriter92(Path game)throws IOException{Held h=WRITERS.get(file(game));return h!=null&&h.owner()==Thread.currentThread();}
 public static synchronized Guard writer(Path game)throws IOException{
  Path p=file(game);if(WRITERS.containsKey(p)||com.fixmer.genesis.admission.ProfileAgent93.held(game))throw new IOException("PROFILE92_IN_USE");
  FileChannel c=FileChannel.open(p,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
  try{FileLock l=c.tryLock();if(l==null)throw new IOException("PROFILE92_IN_USE");Held h=new Held(c,l,Thread.currentThread());WRITERS.put(p,h);return new Guard(p,h);}catch(IOException|RuntimeException e){c.close();throw new IOException("PROFILE92_IN_USE",e);}
 }
 public static final class Guard implements AutoCloseable{
  private final Path file;private Held held;private Guard(Path p,Held h){file=p;held=h;}
  public void close()throws IOException{synchronized(ProfileLease92.class){if(held==null)return;Held h=held;held=null;WRITERS.remove(file,h);try{h.lock.release();}finally{h.channel.close();}}}
 }
}
