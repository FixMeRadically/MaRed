package com.fixmer.genesis.technology.server.waiting;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;

/** Strict small ASCII records and owner-only local storage; not arbitrary Properties input. */
final class WaitingFiles {
    static final int MAX=8192;
    private WaitingFiles() {}
    static Path safe(Path path)throws IOException{
        path=path.toAbsolutePath().normalize();for(Path p=path;p!=null;p=p.getParent()){if(Files.isSymbolicLink(p))throw new IOException("LINKED_PATH");if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&!p.toRealPath().equals(p))throw new IOException("ALIASED_PATH");}return path;
    }
    static void privateMode(Path path,boolean directory)throws IOException{
        if(Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)!=null)
            Files.setPosixFilePermissions(path,directory?EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE):EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
        else{
            var acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);if(acl==null)throw new IOException("PRIVATE_STORAGE_UNAVAILABLE");
            var rule=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if(directory)rule.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);acl.setAcl(List.of(rule.build()));
        }
    }
    static void directory(Path root)throws IOException{safe(root);Files.createDirectory(root);privateMode(root,true);}
    static byte[] encode(Map<String,String> values){
        var out=new StringBuilder();new TreeMap<>(values).forEach((k,v)->{
            if(!k.matches("[a-z][a-zA-Z0-9_]{0,39}")||!v.matches("[A-Za-z0-9_./:+=-]{0,4096}"))throw new IllegalArgumentException("Invalid record value");out.append(k).append('=').append(v).append('\n');
        });byte[] bytes=out.toString().getBytes(StandardCharsets.US_ASCII);if(bytes.length>MAX)throw new IllegalArgumentException("Record too large");return bytes;
    }
    static Map<String,String> decode(byte[] bytes)throws IOException{
        if(bytes.length>MAX)throw new IOException("RECORD_LIMIT");var values=new TreeMap<String,String>();
        String text=new String(bytes,StandardCharsets.US_ASCII);if(!text.isEmpty()&&!text.endsWith("\n"))throw new IOException("INCOMPLETE_RECORD");
        for(String line:text.split("\n")){if(line.isEmpty())continue;int split=line.indexOf('=');if(split<1)throw new IOException("INVALID_RECORD");String k=line.substring(0,split),v=line.substring(split+1);
            if(!k.matches("[a-z][a-zA-Z0-9_]{0,39}")||!v.matches("[A-Za-z0-9_./:+=-]{0,4096}")||values.put(k,v)!=null)throw new IOException("INVALID_OR_DUPLICATE_FIELD");}
        return Map.copyOf(values);
    }
    static Map<String,String> read(Path file)throws IOException{
        safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("REGULAR_FILE_REQUIRED");try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){return decode(in.readNBytes(MAX+1));}
    }
    static void write(Path file,Map<String,String> values,boolean replace)throws IOException{
        safe(file);safe(file.getParent());byte[] bytes=encode(values);
        if(!replace){try(var out=FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){privateMode(file,false);write(out,bytes);}return;}
        Path temp=Files.createTempFile(file.getParent(),".waiting56-",".tmp");
        try{privateMode(temp,false);try(var out=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){write(out,bytes);}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private static void write(FileChannel out,byte[] bytes)throws IOException{var b=ByteBuffer.wrap(bytes);while(b.hasRemaining())out.write(b);out.force(true);}
    static void keys(Map<String,String> map,String... keys)throws IOException{if(!map.keySet().equals(Set.of(keys)))throw new IOException("UNEXPECTED_FIELDS");}
    static UUID id(String value)throws IOException{try{UUID id=UUID.fromString(value);if(id.equals(new UUID(0,0))||!id.toString().equals(value))throw new IllegalArgumentException();return id;}catch(RuntimeException e){throw new IOException("INVALID_ID");}}
    static long number(String value)throws IOException{if(value==null||!value.matches("0|[1-9][0-9]{0,17}"))throw new IOException("INVALID_NUMBER");try{return Long.parseLong(value);}catch(NumberFormatException e){throw new IOException("INVALID_NUMBER");}}
    static String token(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
    static boolean equal(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));}
}
