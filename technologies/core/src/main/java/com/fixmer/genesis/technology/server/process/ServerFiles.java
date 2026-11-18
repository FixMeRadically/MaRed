package com.fixmer.genesis.technology.server.process;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Local supervisor metadata, never a world or mod installation transaction. */
final class ServerFiles {
    static final int MAX_PROPERTIES=64*1024;
    private static final Set<Path> OWNERS=ConcurrentHashMap.newKeySet();
    private ServerFiles() {}
    static void safe(Path path)throws IOException {for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked supervisor paths are unsupported");}
    static void directory(Path path)throws IOException {safe(path);Files.createDirectories(path);safe(path);privateMode(path,true);}
    static void privateMode(Path path,boolean directory)throws IOException {
        if(Files.getFileAttributeView(path,java.nio.file.attribute.PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)!=null)
            Files.setPosixFilePermissions(path,directory?EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE):EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
        else {
            var acl=Files.getFileAttributeView(path,java.nio.file.attribute.AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            if(acl==null)throw new IOException("Cannot restrict supervisor runtime permissions on this filesystem");
            var rule=java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class));
            if(directory)rule.setFlags(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT,java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT);
            acl.setAcl(List.of(rule.build()));
        }
    }
    static Properties read(Path path)throws IOException {
        safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Metadata/configuration file not found: "+path);
        byte[] bytes;try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(MAX_PROPERTIES+1);}if(bytes.length>MAX_PROPERTIES)throw new IOException("Configuration size limit");
        Properties result=new Properties(){@Override public synchronized Object put(Object key,Object value){if(containsKey(key))throw new IllegalArgumentException("Duplicate property: "+key);return super.put(key,value);}};
        try(var reader=new StringReader(new String(bytes,StandardCharsets.UTF_8))){result.load(reader);}catch(IllegalArgumentException bad){throw new IOException("Invalid properties: "+bad.getMessage(),bad);}Properties mutable=new Properties();mutable.putAll(result);return mutable;
    }
    static void write(Path path,Properties properties)throws IOException {
        safe(path);var text=new StringWriter();properties.store(text,"Genesis server supervisor metadata");byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_PROPERTIES)throw new IOException("Metadata size limit");
        Path temporary=Files.createTempFile(path.getParent(),".genesis-",".tmp");
        try{
            privateMode(temporary,false);
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
    static void request(Path path)throws IOException {var p=new Properties();p.setProperty("requested","true");write(path,p);}
    static long number(Properties p,String key,long fallback)throws IOException {String value=p.getProperty(key);if(value==null)return fallback;try{return Long.parseLong(value);}catch(NumberFormatException bad){throw new IOException("Invalid number: "+key,bad);}}
    static Optional<ProcessHandle> ownedProcess(long pid,String start){
        if(pid<=0||start==null||start.isEmpty())return Optional.empty();
        try{return ProcessHandle.of(pid).filter(handle->handle.isAlive()&&handle.info().startInstant().map(Instant::toString).filter(start::equals).isPresent());}catch(RuntimeException bad){return Optional.empty();}
    }
    static boolean canonicalUuid(String value){try{return UUID.fromString(value).toString().equals(value)&&!value.equals("00000000-0000-0000-0000-000000000000");}catch(IllegalArgumentException bad){return false;}}
    static boolean sameProcess(long pid,String start){return ownedProcess(pid,start).isPresent();}
    static String start(ProcessHandle handle)throws IOException {return handle.info().startInstant().orElseThrow(()->new IOException("Cannot establish process identity")).toString();}
    static final class Lease implements AutoCloseable {
        final Path key;final FileChannel channel;final FileLock lock;boolean closed;
        Lease(Path path)throws IOException {
            safe(path);key=path.getParent().toRealPath().resolve(path.getFileName());if(!OWNERS.add(key))throw new IOException("Another server supervisor already owns this directory");
            FileChannel opened=null;FileLock acquired=null;
            try{opened=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);privateMode(path,false);try{acquired=opened.tryLock();}catch(OverlappingFileLockException bad){throw new IOException("Another server supervisor already owns this directory",bad);}if(acquired==null)throw new IOException("Another server supervisor already owns this directory");channel=opened;lock=acquired;}
            catch(IOException|RuntimeException bad){if(opened!=null)try{opened.close();}catch(IOException extra){bad.addSuppressed(extra);}OWNERS.remove(key);throw bad;}
        }
        public void close()throws IOException {if(closed)return;closed=true;try{try{lock.release();}finally{channel.close();}}finally{OWNERS.remove(key);}}
    }
}
