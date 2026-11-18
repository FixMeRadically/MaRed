package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.channels.*;import java.nio.file.*;import java.util.*;
/** Same persistent supervisor.lock inode; no PID/port inference or lock-file deletion. */
final class OwnerLease94 {
 private static final Map<Path,Guard> HELD=new HashMap<>();
 private OwnerLease94(){}
 private static Path file(Path config)throws IOException{Path p=config.toAbsolutePath().normalize().resolveSibling("supervisor.lock");RuntimeFiles.safe(p);return p;}
 static synchronized boolean owned(Path config)throws IOException{return HELD.containsKey(file(config));}
 static synchronized Guard acquire(Path config)throws IOException {
  Path p=file(config);if(HELD.containsKey(p))throw new IOException("COLD94_OWNER_IN_USE");
  FileChannel channel=FileChannel.open(p,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
  try{FileLock lock=channel.tryLock();if(lock==null)throw new IOException("COLD94_OWNER_IN_USE");Guard guard=new Guard(p,channel,lock);HELD.put(p,guard);return guard;}
  catch(IOException|RuntimeException e){channel.close();throw new IOException("COLD94_OWNER_IN_USE",e);}
 }
 static synchronized boolean available(Path config)throws IOException {
  if(owned(config))return false;
  try(var guard=acquire(config)){return true;}catch(IOException busy){if("COLD94_OWNER_IN_USE".equals(busy.getMessage()))return false;throw busy;}
 }
 static final class Guard implements AutoCloseable {
  private final Path path;private FileChannel channel;private FileLock lock;
  private Guard(Path path,FileChannel channel,FileLock lock){this.path=path;this.channel=channel;this.lock=lock;}
  public void close()throws IOException{synchronized(OwnerLease94.class){if(channel==null)return;FileChannel c=channel;FileLock l=lock;channel=null;lock=null;try{l.release();}finally{try{c.close();}finally{HELD.remove(path,this);}}}}
 }
}
