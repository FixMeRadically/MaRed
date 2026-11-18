package com.fixmer.genesis.technology.workspace.collaboration121;
import java.io.*;import java.nio.*;import java.nio.channels.*;import java.nio.file.*;import java.security.*;import java.util.*;
/** Single-writer, forced hash-chained records. Only an incomplete final frame is truncated.
 * A complete frame with a bad digest is corruption, never silently discarded. */
public final class WorkspaceJournal121 implements AutoCloseable {
 public static final int MAX_RECORD=8*1024*1024;public static final long MAX_BYTES=256L*1024*1024;
 private static final Set<Path> OPEN=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private final Path identity;private final FileChannel channel;private final FileLock lock;private final List<byte[]> records=new ArrayList<>();private byte[] head=new byte[32];private boolean closed,poisoned;private long recovered;
 public WorkspaceJournal121(Path file)throws IOException{
  file=file.toAbsolutePath().normalize();safe(file);Files.createDirectories(file.getParent());safe(file);
  identity=file;if(!OPEN.add(identity))throw new IOException("LOCKED");
  FileChannel opened;try{opened=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);}catch(IOException|RuntimeException e){OPEN.remove(identity);throw e;}channel=opened;FileLock acquired=null;
  try{acquired=channel.tryLock();if(acquired==null)throw new IOException("LOCKED");lock=acquired;load();}catch(Exception e){if(acquired!=null)acquired.release();channel.close();OPEN.remove(identity);if(e instanceof IOException io)throw io;throw new IOException("LOCKED_OR_CORRUPT",e);}
 }
 private static void safe(Path path)throws IOException{for(Path p=path;p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(a.isSymbolicLink()||a.isOther()||!p.toRealPath().equals(p.toRealPath(LinkOption.NOFOLLOW_LINKS)))throw new IOException("LINK");}}
 private static byte[] digest(byte[] prior,byte[] bytes){try{var d=MessageDigest.getInstance("SHA-256");d.update(prior);return d.digest(bytes);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
 private void read(ByteBuffer b)throws IOException{while(b.hasRemaining())if(channel.read(b)<0)throw new EOFException();}
 private void load()throws IOException{
  long size=channel.size();if(size>MAX_BYTES)throw new IOException("LIMIT");channel.position(0);
  while(channel.position()<size){long start=channel.position();if(size-start<4){recover(start,size);break;}var len=ByteBuffer.allocate(4);read(len);int n=len.flip().getInt();if(n<1||n>MAX_RECORD)throw new IOException("CORRUPT_LENGTH");if(size-channel.position()<n+32L){recover(start,size);break;}
   var payload=ByteBuffer.allocate(n);read(payload);var seal=ByteBuffer.allocate(32);read(seal);byte[] hash=digest(head,payload.array());if(!MessageDigest.isEqual(hash,seal.array()))throw new IOException("CORRUPT_HASH");records.add(payload.array());head=hash;
  }channel.position(channel.size());
 }
 private void recover(long offset,long size)throws IOException{recovered=size-offset;channel.truncate(offset);channel.force(true);}
 public synchronized void append(byte[] payload)throws IOException{
  Objects.requireNonNull(payload);if(closed||poisoned)throw new IOException("CLOSED_OR_POISONED");if(payload.length<1||payload.length>MAX_RECORD||channel.size()+payload.length+36>MAX_BYTES)throw new IOException("LIMIT");byte[] bytes=payload.clone(),hash=digest(head,bytes);var frame=ByteBuffer.allocate(bytes.length+36).putInt(bytes.length).put(bytes).put(hash);frame.flip();
  try{while(frame.hasRemaining())channel.write(frame);channel.force(true);}catch(IOException e){poisoned=true;throw e;}head=hash;records.add(bytes);
 }
 public synchronized List<byte[]> records(){return records.stream().map(byte[]::clone).toList();}
 public long recoveredTailBytes(){return recovered;}
 @Override public synchronized void close()throws IOException{if(closed)return;closed=true;try{lock.release();}finally{try{channel.close();}finally{OPEN.remove(identity);}}}
}
