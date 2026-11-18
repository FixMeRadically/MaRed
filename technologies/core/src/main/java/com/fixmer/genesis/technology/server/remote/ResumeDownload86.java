package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;import java.io.*;import java.net.*;import javax.net.ssl.HttpsURLConnection;import java.nio.channels.*;import java.nio.file.*;import java.util.*;import java.util.function.*;
/** Partial bytes have no install authority. Every completed object still needs hash, signature and fresh offer checks. */
public final class ResumeDownload86 {
 public static final int MAX_RECORDS=16;public static final long MAX_RESERVED=512L*1024*1024;
 private ResumeDownload86(){}
 private static IOException fail(String c){return new IOException("REMOTE_"+c);}
 private static void check(BooleanSupplier cancel)throws IOException{if(cancel.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("REMOTE_CANCELLED");}
 private static FileLock lock(FileChannel c)throws IOException{try{FileLock l=c.tryLock();if(l==null)throw fail("RESUME_BUSY");return l;}catch(OverlappingFileLockException e){throw fail("RESUME_BUSY");}}
 private static void reserve(Path records,Path locks,Path entry,byte[] binding,long bytes)throws Exception{
  try(var c=FileChannel.open(locks.resolve("budget.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var l=lock(c)){
   if(Files.exists(entry)){if(!Arrays.equals(FirstLaunchFiles.read(entry.resolve("binding86"),16384),binding))throw fail("RESUME_CACHE_INVALID");return;}
   long total=bytes;int count=0;try(var ds=Files.newDirectoryStream(records)){for(Path p:ds){FirstLaunchFiles.safe(p);if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||++count>=MAX_RECORDS)throw fail("RESUME_CACHE_FULL");var fields=ControlWire73.decode(FirstLaunchFiles.read(p.resolve("binding86"),16384));long n=Long.parseLong(fields.get("bytes"));if(n<1||n>300L*1024*1024)throw fail("RESUME_CACHE_INVALID");total+=n;}}
   if(total>MAX_RESERVED)throw fail("RESUME_CACHE_FULL");Files.createDirectory(entry);FirstLaunchFiles.privateMode(entry);FirstLaunchFiles.write(entry.resolve("binding86"),binding,false);
  }
 }
 private static void remove(Path entry,Path locks)throws IOException{
  try(var c=FileChannel.open(locks.resolve("budget.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var l=lock(c)){
   try(var ds=Files.newDirectoryStream(entry)){for(Path p:ds){FirstLaunchFiles.safe(p);String n=p.getFileName().toString();if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!n.equals("binding86")&&!n.equals("client.part")&&!n.startsWith(".next86-"))throw fail("RESUME_CACHE_INVALID");}}
   try(var ds=Files.newDirectoryStream(entry)){for(Path p:ds)Files.delete(p);}Files.delete(entry);
  }
 }
 /** Explicit cleanup: skip held transfers, preserve pins and completed download candidates. */
 public static Map<String,Integer> clearIdle(Path cache)throws IOException{
  cache=FirstLaunchFiles.safe(cache);Path records=FirstLaunchFiles.safe(cache.resolve("partials86")),locks=FirstLaunchFiles.safe(cache.resolve("locks86"));int removed=0,busy=0,invalid=0;if(!Files.exists(records))return Map.of("removed",0,"busy",0,"invalid",0);Files.createDirectories(locks);
  try(var budget=FileChannel.open(FirstLaunchFiles.safe(locks.resolve("budget.lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var held=lock(budget);var ds=Files.newDirectoryStream(records)){
   int count=0;for(Path entry:ds){if(++count>64)throw fail("RESUME_CACHE_INVALID");String id=entry.getFileName().toString();if(!id.matches("[a-f0-9]{64}")){invalid++;continue;}
    try{FirstLaunchFiles.safe(entry);try(var c=FileChannel.open(FirstLaunchFiles.safe(locks.resolve(id+".lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var l=lock(c)){
     var binding=ControlWire73.decode(FirstLaunchFiles.read(entry.resolve("binding86"),16384));if(!id.equals(FirstLaunchFiles.sha(FirstLaunchFiles.read(entry.resolve("binding86"),16384)))||!"86".equals(binding.get("schema")))throw fail("RESUME_CACHE_INVALID");prefix(entry,300L*1024*1024);
     try(var files=Files.newDirectoryStream(entry)){for(Path file:files)Files.delete(file);}Files.delete(entry);removed++;
    }}catch(IOException e){if("REMOTE_RESUME_BUSY".equals(e.getMessage()))busy++;else invalid++;}
   }
  }return Map.of("removed",removed,"busy",busy,"invalid",invalid);
 }
 private static Path prefix(Path entry,long max)throws IOException{
  Path best=null;long length=-1;int count=0;try(var ds=Files.newDirectoryStream(entry)){for(Path p:ds){if(++count>34)throw fail("RESUME_CACHE_INVALID");String n=p.getFileName().toString();if(n.equals("binding86"))continue;if(!n.equals("client.part")&&!n.startsWith(".next86-"))throw fail("RESUME_CACHE_INVALID");FirstLaunchFiles.safe(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>max)throw fail("RESUME_CACHE_INVALID");if(Files.size(p)>length){length=Files.size(p);best=p;}}}return best;
 }
 private static void transfer(URI uri,Path target,long offset,long size,String sha,BooleanSupplier cancel,LongConsumer progress)throws IOException{
  check(cancel);if(offset==size){progress.accept(offset);return;}if(!uri.getScheme().equals("https"))throw fail("TLS");var c=(HttpsURLConnection)uri.toURL().openConnection();PinnedTls159.apply(c);c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(5000);c.setUseCaches(false);c.setRequestProperty("Accept-Encoding","identity");String tag="\""+sha+"\"";
  if(offset>0){c.setRequestProperty("Range","bytes="+offset+"-");c.setRequestProperty("If-Range",tag);}long deadline=System.nanoTime()+600_000_000_000L;
  try{
   int status=c.getResponseCode();if(status==502||status==503||status==504)throw fail("HTTP_TEMPORARY");String encoding=c.getHeaderField("Content-Encoding");if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw fail("ENCODING");long declared=c.getContentLengthLong(),start=offset;
   if(status==206){if(offset==0||!tag.equals(c.getHeaderField("ETag"))||!("bytes "+offset+"-"+(size-1)+"/"+size).equals(c.getHeaderField("Content-Range"))||declared!=size-offset)throw fail("RESUME_RANGE");}
   else if(status==200){start=0;if(declared>size)throw fail("DOWNLOAD_LIMIT");}
   else throw fail(status==416?"RESUME_RANGE":"HTTP_STATUS");
   try(var out=Files.newOutputStream(target,start==0?new StandardOpenOption[]{StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING}:new StandardOpenOption[]{StandardOpenOption.WRITE,StandardOpenOption.APPEND});var in=c.getInputStream()){
    long count=start;progress.accept(count);byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){check(cancel);if(System.nanoTime()>deadline)throw fail("DOWNLOAD_TIMEOUT");if(n>size-count)throw fail("DOWNLOAD_LIMIT");out.write(b,0,n);count+=n;progress.accept(count);}if(count!=size)throw fail("DOWNLOAD_TRUNCATED");
   }check(cancel);
  }finally{c.disconnect();}
 }
 public static void fetch(Path cache,URI uri,RemoteOffer69 offer,byte[] key,byte[] signature,Path output,BooleanSupplier cancel,LongConsumer progress)throws IOException{
  try{
   offer.verify(RemoteOffer69.publicKey(key),signature);PinnedTls159.hint(offer.endpoint(),offer.signer());cache=FirstLaunchFiles.safe(cache);output=FirstLaunchFiles.safe(output);if(Files.exists(output))throw fail("RESUME_OUTPUT_EXISTS");Path records=FirstLaunchFiles.safe(cache.resolve("partials86")),locks=FirstLaunchFiles.safe(cache.resolve("locks86"));Files.createDirectories(records);Files.createDirectories(locks);FirstLaunchFiles.privateMode(records);FirstLaunchFiles.privateMode(locks);
   byte[] binding=ControlWire73.encode(Map.of("schema","86","offer",Base64.getEncoder().encodeToString(offer.encode()),"key",Base64.getEncoder().encodeToString(key),"signature",Base64.getEncoder().encodeToString(signature),"uri",uri.toString(),"bytes",Long.toString(offer.bytes())));String id=FirstLaunchFiles.sha(binding);Path entry=FirstLaunchFiles.safe(records.resolve(id));
   try(var channel=FileChannel.open(FirstLaunchFiles.safe(locks.resolve(id+".lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var held=lock(channel)){
    check(cancel);reserve(records,locks,entry,binding,offer.bytes());Path old=prefix(entry,offer.bytes()),next=Files.createTempFile(entry,".next86-",".part");FirstLaunchFiles.privateMode(next);long offset=old==null?0:Files.size(old);boolean moved=false;
    try{
     // Read old bytes only. Never append through a pre-existing hardlink supplied by another local process.
     if(old!=null)try(var in=Files.newInputStream(old,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(next,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){byte[] b=new byte[65536];long count=0;int n;while((n=in.read(b))!=-1){check(cancel);if(n>offset-count)throw fail("RESUME_CACHE_CHANGED");out.write(b,0,n);count+=n;}if(count!=offset)throw fail("RESUME_CACHE_CHANGED");}
     transfer(uri,next,offset,offer.bytes(),offer.archiveSha(),cancel,progress);
     if(Files.size(next)!=offer.bytes()||!FirstLaunchFiles.hash(next,300L*1024*1024).equals(offer.archiveSha()))throw fail("ARCHIVE_HASH");check(cancel);Files.move(next,output);moved=true;try{remove(entry,locks);}catch(IOException cleanup){/* Valid downloaded bytes remain usable; cache reservation stays conservative. */}
    }catch(IOException e){
     String code=e.getMessage();boolean invalid=Set.of("REMOTE_RESUME_RANGE","REMOTE_ENCODING","REMOTE_DOWNLOAD_LIMIT","REMOTE_ARCHIVE_HASH").contains(code==null?"":code);
     try{if(invalid)remove(entry,locks);else if(Files.exists(next)&&Files.size(next)>=offset){Files.move(next,FirstLaunchFiles.safe(entry.resolve("client.part")),StandardCopyOption.REPLACE_EXISTING);try(var ds=Files.newDirectoryStream(entry,".next86-*")){for(Path p:ds){FirstLaunchFiles.safe(p);Files.delete(p);}}}}catch(IOException persistence){e.addSuppressed(persistence);}throw e;
    }finally{if(!moved)Files.deleteIfExists(next);}
   }
  }catch(IOException e){throw e;}catch(Exception e){throw new IOException("REMOTE_RESUME_CACHE_INVALID",e);}
 }
}
