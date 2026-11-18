package com.fixmer.genesis.technology.server.preparation;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
/** Local preparation data only. Never writes the selected game profile. */
final class PreparationFiles {
    static final int MAX_RECORD=2*1024*1024;
    private PreparationFiles(){
    }
    static Path existing(Path path)throws IOException {
        path=path.toAbsolutePath().normalize();
        safe(path);
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)||!path.equals(path.toRealPath()))throw new IOException("PREPARATION_PATH_ALIAS_OR_MISSING");
        return path;
    }
    static void safe(Path path)throws IOException{
        for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("PREPARATION_LINKED_PATH");
    }
    static void privateMode(Path path,boolean directory)throws IOException {
        if(Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)!=null)Files.setPosixFilePermissions(path,directory?EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE):EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
        else{
            var acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            if(acl==null)throw new IOException("PREPARATION_PRIVATE_STORAGE_UNAVAILABLE");
            var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if(directory)entry.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);
            acl.setAcl(List.of(entry.build()));
        }
    }
    static void newDirectory(Path path)throws IOException{
        safe(path);
        existing(path.getParent());
        Files.createDirectory(path);
        privateMode(path,true);
    }
    static byte[] read(Path path,int max)throws IOException{
        existing(path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("PREPARATION_REGULAR_FILE_REQUIRED");
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
            byte[] data=in.readNBytes(max+1);
            if(data.length>max)throw new IOException("PREPARATION_RECORD_SIZE_LIMIT");
            return data;
        }
    }
    static String digest(byte[] data){
        try{
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        }
        catch(NoSuchAlgorithmException impossible){
            throw new AssertionError(impossible);
        }
    }
    static String hash(Path path,long max)throws IOException {
        existing(path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>max)throw new IOException("PREPARATION_FILE_LIMIT");
        try{
            var sha=MessageDigest.getInstance("SHA-256");
            long count=0;
            try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                byte[] buf=new byte[65536];
                int n;
                while((n=in.read(buf))!=-1){
                    if((count+=n)>max)throw new IOException("PREPARATION_FILE_LIMIT");
                    sha.update(buf,0,n);
                }
            }
            return HexFormat.of().formatHex(sha.digest());
        }
        catch(NoSuchAlgorithmException impossible){
            throw new AssertionError(impossible);
        }
    }
    static void write(Path path,byte[] data)throws IOException {
        /* VERIFY52_WRITE_BEGIN */safe(path);
        existing(path.getParent());
        if(data.length>MAX_RECORD)throw new IOException("PREPARATION_RECORD_SIZE_LIMIT");
        Path temp=Files.createTempFile(path.getParent(),".prepare52-",".tmp");
        try{
            privateMode(temp,false);
            try(var out=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                var buf=ByteBuffer.wrap(data);
                while(buf.hasRemaining())out.write(buf);
                out.force(true);
            }
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        finally{
            Files.deleteIfExists(temp);
        }
    }
    static void disjoint(Path a,Path b)throws IOException{
        if(a.startsWith(b)||b.startsWith(a))throw new IOException("PREPARATION_STORAGE_OVERLAPS_PROFILE_OR_CACHE");
    }
    static Map<String,String> inventory(Path profile)throws IOException {
        profile=existing(profile);
        if(!Files.isDirectory(profile))throw new IOException("PREPARATION_PROFILE_REQUIRED");
        var map=new TreeMap<String,String>();
        map.put("profileIdentity",identity(profile));
        Path mods=profile.resolve("mods");
        safe(mods);
        if(!Files.exists(mods,LinkOption.NOFOLLOW_LINKS)){
            map.put("modsIdentity","ABSENT");
            map.put("count","0");
            map.put("bytes","0");
            return map;
        }
        existing(mods);
        if(!Files.isDirectory(mods,LinkOption.NOFOLLOW_LINKS))throw new IOException("PREPARATION_MODS_DIRECTORY_REQUIRED");
        map.put("modsIdentity",identity(mods));
        long total=0;
        int count=0;
        var names=new TreeSet<String>();
        var folded=new HashSet<String>();
        try(var entries=Files.list(mods)){
            var iterator=entries.iterator();
            while(iterator.hasNext()){
                Path file=iterator.next();
                if(++count>4096)throw new IOException("PREPARATION_INVENTORY_COUNT_LIMIT");
                String name=file.getFileName().toString();
                if(name.length()>240||!folded.add(name.toLowerCase(Locale.ROOT)))throw new IOException("PREPARATION_INVENTORY_CASE_COLLISION");
                names.add(name);
            }
        }
        int i=0;
        for(String name:names){
            Path file=mods.resolve(name);
            existing(file);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("PREPARATION_NESTED_OR_SPECIAL_MODS_ENTRY");
            long size=Files.size(file);
            if((total+=size)>2L*1024*1024*1024)throw new IOException("PREPARATION_INVENTORY_BYTES_LIMIT");
            String prefix="file."+(i++)+".";
            map.put(prefix+"name",name);
            map.put(prefix+"bytes",Long.toString(size));
            map.put(prefix+"sha256",hash(file,256L*1024*1024));
            if(Files.size(file)!=size)throw new IOException("PREPARATION_INVENTORY_CHANGED");
        }
        map.put("count",Integer.toString(count));
        map.put("bytes",Long.toString(total));
        return map;
    }
    private static String identity(Path path)throws IOException{
        var attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        return attrs.fileKey()==null?"UNAVAILABLE":attrs.fileKey().toString();
    }
}
