package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Operator-controlled local files. Never follow links or load executable package instructions. */
final class PackageFiles {
    private PackageFiles(){}
    static void safe(Path path)throws IOException{
        for(Path p=path.toAbsolutePath().normalize();p!=null;p=p.getParent()){
            if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)){
                var attributes=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(attributes.isSymbolicLink()||attributes.isOther())throw new IOException("LINKED_OR_SPECIAL_PATH");
            }
        }
    }
    static byte[] read(Path path,int limit)throws IOException{safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("REGULAR_FILE_REQUIRED");try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=in.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("FILE_SIZE_LIMIT");return bytes;}}
    static String digest(Path path,long limit)throws IOException{
        safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>limit)throw new IOException("FILE_SIZE_OR_TYPE");var digest=sha();long count=0;try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){count+=n;if(count>limit)throw new IOException("FILE_SIZE_LIMIT");digest.update(buffer,0,n);}}return HexFormat.of().formatHex(digest.digest());
    }
    static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    static String digest(byte[] bytes){return HexFormat.of().formatHex(sha().digest(bytes));}
    static String keyId(PublicKey key){return digest(key.getEncoded());}
    static PublicKey publicKey(Path path)throws IOException{try{return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(read(path,1024)));}catch(GeneralSecurityException bad){throw new IOException("INVALID_ED25519_PUBLIC_KEY",bad);}}
    static PrivateKey privateKey(Path path)throws IOException{try{return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(read(path,1024)));}catch(GeneralSecurityException bad){throw new IOException("INVALID_ED25519_PRIVATE_KEY",bad);}}
    static void privateMode(Path path,boolean dir)throws IOException{
        if(Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)!=null)Files.setPosixFilePermissions(path,dir?PosixFilePermissions.fromString("rwx------"):PosixFilePermissions.fromString("rw-------"));
        else {var acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);if(acl==null)throw new IOException("PRIVATE_PERMISSIONS_UNSUPPORTED");var rule=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));if(dir)rule.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);acl.setAcl(List.of(rule.build()));}
    }
    static Properties properties(Path path)throws IOException{
        var strict=new Properties(){@Override public synchronized Object put(Object key,Object value){if(containsKey(key))throw new IllegalArgumentException("DUPLICATE_PROPERTY");return super.put(key,value);}};
        try(var reader=new InputStreamReader(new ByteArrayInputStream(read(path,65536)),java.nio.charset.StandardCharsets.UTF_8)){strict.load(reader);}catch(IllegalArgumentException bad){throw new IOException("INVALID_SPEC_PROPERTIES",bad);}var p=new Properties();p.putAll(strict);return p;
    }
}
