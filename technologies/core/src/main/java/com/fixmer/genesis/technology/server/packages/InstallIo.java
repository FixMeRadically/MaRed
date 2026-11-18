package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Trusted local storage primitives. No claim of parent-directory fsync on every platform. */
final class InstallIo {
    private InstallIo(){}
    private static final Set<Path> HELD=ConcurrentHashMap.newKeySet();
    interface Fault {void at(String point)throws IOException;}
    static final Fault NONE=point->{};
    static void directory(Path path)throws IOException{PackageFiles.safe(path);Files.createDirectories(path);PackageFiles.safe(path);PackageFiles.privateMode(path,true);}
    static void atomic(Path path,byte[] bytes,Fault fault,String point)throws IOException{
        PackageFiles.safe(path);Path temp=Files.createTempFile(path.getParent(),".genesis-",".tmp");
        try{PackageFiles.privateMode(temp,false);try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);fault.at(point+".write");channel.force(true);fault.at(point+".force");}Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);fault.at(point+".move");}finally{Files.deleteIfExists(temp);}
    }
    static void copy(Path source,Path destination,String hash,long limit,Fault fault,String point)throws IOException{copy(source,destination,hash,limit,fault,point,false,null,destination.getParent());}
    static void copyChecked(Path source,Path destination,String hash,long limit,Fault fault,String point,String expectedDestination)throws IOException{copy(source,destination,hash,limit,fault,point,true,expectedDestination,destination.getParent());}
    // Transaction-owned scratch is outside mods: a killed JVM must not leave an unowned partial .jar-adjacent file.
    static void copyChecked(Path source,Path destination,String hash,long limit,Fault fault,String point,String expectedDestination,Path transactionScratch)throws IOException{
        directory(transactionScratch);copy(source,destination,hash,limit,fault,point,true,expectedDestination,transactionScratch);
    }
    private static void copy(Path source,Path destination,String hash,long limit,Fault fault,String point,boolean checkDestination,String expectedDestination,Path scratch)throws IOException{
        PackageFiles.safe(source);PackageFiles.safe(destination);if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("SOURCE_NOT_REGULAR");PackageFiles.safe(scratch);Path temp=Files.createTempFile(scratch,".genesis-",".tmp");
        try{PackageFiles.privateMode(temp,false);var digest=PackageFiles.sha();long count=0;
            try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var channel=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1){count+=n;if(count>limit)throw new IOException("COPY_SIZE_LIMIT");digest.update(data,0,n);var buffer=ByteBuffer.wrap(data,0,n);while(buffer.hasRemaining())channel.write(buffer);}if(!HexFormat.of().formatHex(digest.digest()).equals(hash))throw new IOException("COPY_HASH_MISMATCH");fault.at(point+".write");channel.force(true);fault.at(point+".force");
            }PackageFiles.safe(destination);if(checkDestination)same(expectedDestination,observed(destination));Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);fault.at(point+".move");
        }finally{Files.deleteIfExists(temp);}
    }
    static String observed(Path file)throws IOException{PackageFiles.safe(file);return Files.exists(file,LinkOption.NOFOLLOW_LINKS)?PackageFiles.digest(file,PackageModel.MAX_FILE):null;}
    static byte[] framed(String body){return (body+PackageFiles.digest(body.getBytes(StandardCharsets.UTF_8))+"\n").getBytes(StandardCharsets.UTF_8);}
    static String unframe(byte[] bytes,int lines)throws IOException{
        String text=new String(bytes,StandardCharsets.UTF_8);String[] fields=text.split("\n",-1);if(fields.length!=lines+2||!fields[fields.length-1].isEmpty())throw new IOException("INVALID_METADATA_FRAME");int at=text.lastIndexOf('\n',text.length()-2);String body=text.substring(0,at+1);if(!PackageFiles.digest(body.getBytes(StandardCharsets.UTF_8)).equals(fields[lines]))throw new IOException("METADATA_CHECKSUM");return body;
    }
    static void same(String expected,String actual)throws IOException{if(!Objects.equals(expected,actual))throw new IOException("FILE_PRECONDITION_CHANGED");}
    static final class Lease implements AutoCloseable {
        final Path key;final FileChannel channel;final FileLock lock;boolean closed;
        Lease(Path path)throws IOException{
            PackageFiles.safe(path);key=path.getParent().toRealPath().resolve(path.getFileName());synchronized(HELD){if(HELD.contains(key))throw new IOException("PROFILE_OR_CACHE_BUSY");if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))for(Path held:HELD)if(Files.isSameFile(path,held))throw new IOException("PROFILE_OR_CACHE_BUSY");HELD.add(key);}FileChannel open=null;
            try{open=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);PackageFiles.privateMode(path,false);FileLock acquired;try{acquired=open.tryLock();}catch(OverlappingFileLockException bad){throw new IOException("PROFILE_OR_CACHE_BUSY",bad);}if(acquired==null)throw new IOException("PROFILE_OR_CACHE_BUSY");channel=open;lock=acquired;}
            catch(IOException|RuntimeException bad){if(open!=null)try{open.close();}catch(IOException extra){bad.addSuppressed(extra);}HELD.remove(key);throw bad;}
        }
        public void close()throws IOException{if(closed)return;closed=true;try{try{lock.release();}finally{channel.close();}}finally{HELD.remove(key);}}
    }
}
