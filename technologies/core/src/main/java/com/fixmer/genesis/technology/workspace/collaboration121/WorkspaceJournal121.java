package com.fixmer.genesis.technology.workspace.collaboration121;
import java.io.*;import java.nio.*;import java.nio.channels.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.zip.*;
/** Single-writer, forced hash-chained records. Only an incomplete final frame is truncated.
 * A complete frame with a bad digest is corruption, never silently discarded. */
public final class WorkspaceJournal121 implements AutoCloseable {
 public static final int MAX_RECORD=8*1024*1024;public static final long MAX_BYTES=256L*1024*1024;
 private static final Set<Path> OPEN=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private final Path identity;private final FileChannel channel;private final FileLock lock;private static final int PACK=0x47575a31,MAX_RECORDS=65536;
 private final List<byte[]> records=new ArrayList<>();private byte[] head=new byte[32];private boolean closed,poisoned;private long recovered;
 public WorkspaceJournal121(Path file)throws IOException{
  file=file.toAbsolutePath().normalize();safe(file);Files.createDirectories(file.getParent());safe(file);
  identity=file;if(!OPEN.add(identity))throw new IOException("LOCKED");
  FileChannel opened;try{opened=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);}catch(IOException|RuntimeException e){OPEN.remove(identity);throw e;}channel=opened;FileLock acquired=null;
  try{acquired=channel.tryLock();if(acquired==null)throw new IOException("LOCKED");lock=acquired;load();}catch(Exception e){try{if(acquired!=null)acquired.release();}catch(IOException cleanup){e.addSuppressed(cleanup);}
   try{channel.close();}catch(IOException cleanup){e.addSuppressed(cleanup);}finally{OPEN.remove(identity);}
   if(e instanceof IOException io)throw io;throw new IOException("LOCKED_OR_CORRUPT",e);}
 }
 private static void safe(Path path)throws IOException{for(Path p=path;p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(a.isSymbolicLink()||a.isOther()||!p.toRealPath().equals(p.toRealPath(LinkOption.NOFOLLOW_LINKS)))throw new IOException("LINK");}}
 private static byte[] digest(byte[] prior,byte[] bytes){try{var d=MessageDigest.getInstance("SHA-256");d.update(prior);return d.digest(bytes);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
 private void read(ByteBuffer b)throws IOException{while(b.hasRemaining())if(channel.read(b)<0)throw new EOFException();}
 private void load()throws IOException{
  long size=channel.size();if(size>MAX_BYTES)throw new IOException("LIMIT");channel.position(0);
  while(channel.position()<size){long start=channel.position();if(size-start<4){recover(start,size);break;}var len=ByteBuffer.allocate(4);read(len);int n=len.flip().getInt();if(n<1||n>MAX_RECORD+9)throw new IOException("CORRUPT_LENGTH");if(size-channel.position()<n+32L){recover(start,size);break;}
   var payload=ByteBuffer.allocate(n);read(payload);var seal=ByteBuffer.allocate(32);read(seal);byte[] hash=digest(head,payload.array());if(!MessageDigest.isEqual(hash,seal.array()))throw new IOException("CORRUPT_HASH");unpack(payload.array());if(records.size()>=MAX_RECORDS)throw new IOException("LIMIT");records.add(payload.array());head=hash;
  }channel.position(channel.size());
 }
 private void recover(long offset,long size)throws IOException{recovered=size-offset;channel.truncate(offset);channel.force(true);}
 public synchronized void append(byte[] payload)throws IOException{
  Objects.requireNonNull(payload);if(closed||poisoned)throw new IOException("CLOSED_OR_POISONED");if(payload.length<1||payload.length>MAX_RECORD)throw new IOException("LIMIT");if(records.size()>=MAX_RECORDS)throw new IOException("LIMIT");byte[] bytes=pack(payload),hash=digest(head,bytes);if(channel.size()+bytes.length+36>MAX_BYTES)throw new IOException("LIMIT");var frame=ByteBuffer.allocate(bytes.length+36).putInt(bytes.length).put(bytes).put(hash);frame.flip();
  try{while(frame.hasRemaining())channel.write(frame);channel.force(true);}catch(IOException e){poisoned=true;throw e;}head=hash;records.add(bytes);
 }
 private static byte[] pack(byte[] raw)throws IOException{var zipped=new ByteArrayOutputStream();var deflater=new Deflater(Deflater.BEST_SPEED);try(var z=new DeflaterOutputStream(zipped,deflater)){z.write(raw);}finally{deflater.end();}boolean compressed=zipped.size()+16<raw.length;var result=new ByteArrayOutputStream();try(var out=new DataOutputStream(result)){out.writeInt(PACK);out.writeBoolean(compressed);out.writeInt(raw.length);out.write(compressed?zipped.toByteArray():raw);}return result.toByteArray();}
 private static byte[] unpack(byte[] encoded)throws IOException{if(encoded.length<9||ByteBuffer.wrap(encoded).getInt()!=PACK)return encoded.clone();try(var in=new DataInputStream(new ByteArrayInputStream(encoded))){in.readInt();boolean compressed=in.readBoolean();int length=in.readInt();if(length<1||length>MAX_RECORD)throw new IOException("CORRUPT_LENGTH");byte[] body=in.readAllBytes();if(!compressed){if(body.length!=length)throw new IOException("CORRUPT_LENGTH");return body;}var inflater=new Inflater();try{inflater.setInput(body);byte[] result=new byte[length];int total=0;while(!inflater.finished()&&total<length){int n=inflater.inflate(result,total,length-total);if(n==0)break;total+=n;}if(total!=length||!inflater.finished()||inflater.getRemaining()!=0)throw new IOException("CORRUPT_COMPRESSION");return result;}catch(DataFormatException e){throw new IOException("CORRUPT_COMPRESSION",e);}finally{inflater.end();}}}
 public synchronized List<byte[]> records(){var snapshot=List.copyOf(records);return Collections.unmodifiableList(new AbstractList<byte[]>(){public int size(){return snapshot.size();}public byte[] get(int index){try{return unpack(snapshot.get(index));}catch(IOException e){throw new UncheckedIOException(e);}}});}
 public long recoveredTailBytes(){return recovered;}
 @Override public synchronized void close()throws IOException{if(closed)return;closed=true;try{lock.release();}finally{try{channel.close();}finally{OPEN.remove(identity);}}}
}
