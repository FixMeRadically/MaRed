package com.fixmer.genesis.technology.server.firstlaunch;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

/** Private local first-run channel; not an authentication protocol for remote clients. */
public final class FirstLaunchFiles {
    private FirstLaunchFiles(){}
    public static Path safe(Path path)throws IOException{
        path=path.toAbsolutePath().normalize();for(Path p=path;p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&(!p.toRealPath().equals(p)||Files.isSymbolicLink(p)))throw new IOException("FIRST58_PATH_ALIAS");return path;
    }
    public static void privateMode(Path p)throws IOException{
        if(Files.getFileAttributeView(p,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)!=null)Files.setPosixFilePermissions(p,Files.isDirectory(p)?PosixFilePermissions.fromString("rwx------"):PosixFilePermissions.fromString("rw-------"));
        else{var view=Files.getFileAttributeView(p,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);if(view==null)throw new IOException("FIRST58_PRIVATE_ACL_REQUIRED");var b=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(view.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));if(Files.isDirectory(p))b.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);view.setAcl(List.of(b.build()));}
    }
    public static byte[] read(Path p,int max)throws IOException{safe(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>max)throw new IOException("FIRST58_FILE_LIMIT");try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=in.readNBytes(max+1);if(bytes.length>max)throw new IOException("FIRST58_FILE_LIMIT");return bytes;}}
    public static byte[] encode(Map<String,String> m)throws IOException{
        var b=new StringBuilder();for(var e:new TreeMap<>(m).entrySet()){if(!e.getKey().matches("[a-zA-Z][a-zA-Z0-9.]{0,39}")||!e.getValue().matches("[A-Za-z0-9_./:+-]{1,160}"))throw new IOException("FIRST58_RECORD_FIELD");b.append(e.getKey()).append('=').append(e.getValue()).append('\n');}byte[] data=b.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);if(data.length>8192)throw new IOException("FIRST58_RECORD_LIMIT");return data;
    }
    public static Map<String,String> decode(byte[] bytes)throws IOException{
        if(bytes.length>8192)throw new IOException("FIRST58_RECORD_LIMIT");String text=new String(bytes,java.nio.charset.StandardCharsets.US_ASCII);var out=new TreeMap<String,String>();for(String line:text.split("\n",-1)){if(line.isEmpty())continue;int i=line.indexOf('=');if(i<1||out.putIfAbsent(line.substring(0,i),line.substring(i+1))!=null)throw new IOException("FIRST58_RECORD_DUPLICATE");}if(!Arrays.equals(encode(out),bytes))throw new IOException("FIRST58_RECORD_CANONICAL");return Map.copyOf(out);
    }
    public static void write(Path p,byte[] bytes,boolean replace)throws IOException{
        safe(p);
        if (!replace) {
            boolean created = false;
            try (var target = FileChannel.open(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                privateMode(p);
                writeFully(target, bytes);
            } catch (IOException | RuntimeException failed) {
                if (created) {
                    try { Files.deleteIfExists(p); } catch (IOException cleanup) { failed.addSuppressed(cleanup); }
                }
                throw failed;
            }
            return;
        }
        Path temporary = Files.createTempFile(p.getParent(), ".first58-", ".tmp");
        try {
            privateMode(temporary);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                writeFully(channel, bytes);
            }
            Files.move(temporary, p, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
    }
    public static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(GeneralSecurityException e){throw new AssertionError(e);}}
    public static String hash(Path file,long max)throws IOException{safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>max)throw new IOException("FIRST58_HASH_LIMIT");try{var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];long count=0;for(int n;(n=in.read(b))>=0;){count+=n;if(count>max)throw new IOException("FIRST58_HASH_LIMIT");d.update(b,0,n);}}return HexFormat.of().formatHex(d.digest());}catch(GeneralSecurityException e){throw new AssertionError(e);}}
    public static String mac(byte[] key,byte[] bytes)throws IOException{try{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(bytes));}catch(GeneralSecurityException bad){throw new IOException(bad);}}
    public static byte[] sign(byte[] key,Map<String,String> values)throws IOException{if(values.containsKey("mac"))throw new IOException("FIRST58_MAC_FIELD");var out=new TreeMap<>(values);out.put("mac",mac(key,encode(values)));return encode(out);}
    public static Map<String,String> verify(byte[] key,byte[] bytes)throws IOException{var m=new TreeMap<>(decode(bytes));String signature=m.remove("mac");if(signature==null||!signature.matches("[a-f0-9]{64}")||!MessageDigest.isEqual(signature.getBytes(java.nio.charset.StandardCharsets.US_ASCII),mac(key,encode(m)).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("FIRST58_MAC");return Map.copyOf(m);}
    public static String inventory(Path profile)throws IOException{
        Path mods=safe(profile.resolve("mods"));
        if(!Files.isDirectory(mods))throw new IOException("FIRST58_MODS_MISSING");
        var out=new TreeMap<String,String>();
        var folded=new HashSet<String>();
        try(var stream=Files.list(mods)){
            var files=stream.iterator();
            while(files.hasNext()){
                Path file=files.next();
                String name=file.getFileName().toString();
                if(!name.toLowerCase(Locale.ROOT).endsWith(".jar"))continue;
                if(out.size()>=256)throw new IOException("FIRST58_MOD_LIMIT");
                if(!folded.add(name.toLowerCase(Locale.ROOT)))throw new IOException("FIRST58_MOD_CASE_COLLISION");
                String rel="mods/"+name;
                String digest=hash(file,64L*1024*1024);
                new com.fixmer.genesis.technology.server.packages.PackageModel.Entry(rel,com.fixmer.genesis.technology.server.packages.PackageModel.Side.BOTH,Files.size(file),digest);
                out.put(rel,digest);
            }
        }
        var body=new StringBuilder();out.forEach((k,v)->body.append(k).append('=').append(v).append('\n'));return sha(body.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
