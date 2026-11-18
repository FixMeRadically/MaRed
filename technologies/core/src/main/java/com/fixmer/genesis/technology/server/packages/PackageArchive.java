package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Bounded, read-only verification; no payload is extracted or executed. */
public final class PackageArchive {
    public static final String MANIFEST="genesis/manifest.bin", SIGNATURE="genesis/signature.ed25519";
    private static final byte[] DOMAIN="GENESIS-UPDATE-PACKAGE-V1\0".getBytes(StandardCharsets.US_ASCII);
    private PackageArchive(){}
    public static final class Verified {
        private final Manifest manifest;private final String archiveSha256,manifestSha256;
        private Verified(Manifest manifest,String archiveSha256,String manifestSha256){this.manifest=manifest;this.archiveSha256=archiveSha256;this.manifestSha256=manifestSha256;}
        public Manifest manifest(){return manifest;}public String archiveSha256(){return archiveSha256;}public String manifestSha256(){return manifestSha256;}
    }
    public static Verified verify(Path archive,Map<String,PublicKey> trusted,Target target)throws IOException {
        Objects.requireNonNull(target);trusted=Map.copyOf(trusted);String before=PackageFiles.digest(archive,MAX_ARCHIVE);Manifest manifest;byte[] raw;
        try(var zip=new ZipFile(archive.toFile(),StandardCharsets.UTF_8)){
            var entries=new HashMap<String,ZipEntry>();var names=new HashSet<String>();var all=zip.entries();
            while(all.hasMoreElements()){
                ZipEntry entry=all.nextElement();String name=entry.getName();
                if(entry.isDirectory()||name.length()>180||!names.add(name.toLowerCase(Locale.ROOT))||entries.size()>=MAX_FILES+2)throw new IOException("ZIP_DUPLICATE_DIRECTORY_OR_COUNT");
                if(!name.equals(MANIFEST)&&!name.equals(SIGNATURE)){
                    if(!name.startsWith("files/"))throw new IOException("UNKNOWN_ZIP_ENTRY");
                    try{modPath(name.substring(6));}catch(IllegalArgumentException bad){throw new IOException("UNSAFE_ZIP_PATH",bad);}
                }
                entries.put(name,entry);
            }
            raw=small(zip,required(entries,MANIFEST),MAX_MANIFEST);manifest=PackageCodec.decode(raw);
            PublicKey key=trusted.get(manifest.signer());if(key==null||!PackageFiles.keyId(key).equals(manifest.signer()))throw new IOException("UNTRUSTED_SIGNER");
            byte[] signed=small(zip,required(entries,SIGNATURE),64);if(signed.length!=64)throw new IOException("SIGNATURE_SIZE");
            try{if(!signature(false,null,key,raw,signed))throw new IOException("SIGNATURE_MISMATCH");}catch(GeneralSecurityException bad){throw new IOException("SIGNATURE_INVALID",bad);}
            try{target.check(manifest);}catch(IllegalArgumentException bad){throw new IOException(bad.getMessage(),bad);}
            if(entries.size()!=manifest.files().size()+2)throw new IOException("ZIP_INVENTORY_MISMATCH");
            for(Entry entry:manifest.files()){
                ZipEntry stored=required(entries,"files/"+entry.path());
                if(stored.getSize()!=entry.bytes())throw new IOException("PAYLOAD_SIZE_MISMATCH");
                var hash=PackageFiles.sha();var crc=new CRC32();long count=0;
                try(var in=zip.getInputStream(stored)){byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))!=-1){count+=n;if(count>entry.bytes())throw new IOException("PAYLOAD_SIZE_LIMIT");hash.update(buffer,0,n);crc.update(buffer,0,n);}}
                if(count!=entry.bytes()||crc.getValue()!=stored.getCrc()||!HexFormat.of().formatHex(hash.digest()).equals(entry.sha256()))throw new IOException("PAYLOAD_HASH_OR_CRC_MISMATCH");
            }
        }catch(IllegalArgumentException bad){throw new IOException("INVALID_ZIP_OR_MANIFEST",bad);}
        String after=PackageFiles.digest(archive,MAX_ARCHIVE);if(!before.equals(after))throw new IOException("ARCHIVE_CHANGED_DURING_VERIFY");return new Verified(manifest,after,PackageFiles.digest(raw));
    }
    private static ZipEntry required(Map<String,ZipEntry> entries,String key)throws IOException{var entry=entries.get(key);if(entry==null)throw new IOException("MISSING_ZIP_ENTRY");return entry;}
    private static byte[] small(ZipFile zip,ZipEntry entry,int limit)throws IOException{
        if(entry.getSize()<0||entry.getSize()>limit)throw new IOException("METADATA_SIZE_LIMIT");
        try(var in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(limit+1);var crc=new CRC32();crc.update(bytes);if(bytes.length>limit||bytes.length!=entry.getSize()||crc.getValue()!=entry.getCrc())throw new IOException("METADATA_SIZE_OR_CRC");return bytes;}
    }
    private static boolean signature(boolean signing,PrivateKey privateKey,PublicKey publicKey,byte[] raw,byte[] expected)throws GeneralSecurityException{
        var signature=Signature.getInstance("Ed25519");if(signing)signature.initSign(privateKey);else signature.initVerify(publicKey);signature.update(DOMAIN);signature.update(raw);return !signing&&signature.verify(expected);
    }
    public static byte[] sign(Manifest manifest,PrivateKey key)throws IOException{
        try{var signature=Signature.getInstance("Ed25519");signature.initSign(key);signature.update(DOMAIN);signature.update(PackageCodec.encode(manifest));return signature.sign();}catch(GeneralSecurityException bad){throw new IOException("SIGNING_FAILED",bad);}
    }
    /** Local authoring only. Rejects overwrite; on failure removes only the output it created. */
    public static void create(Path output,Manifest manifest,Map<String,Path> sources,PrivateKey privateKey,PublicKey publicKey)throws IOException{
        if(!manifest.signer().equals(PackageFiles.keyId(publicKey)))throw new IOException("SIGNER_KEY_MISMATCH");
        byte[] raw=PackageCodec.encode(manifest),signed=sign(manifest,privateKey);
        try{if(!signature(false,null,publicKey,raw,signed))throw new IOException("KEY_PAIR_MISMATCH");}catch(GeneralSecurityException bad){throw new IOException("KEY_PAIR_MISMATCH",bad);}
        Set<String> expected=new HashSet<>();for(Entry entry:manifest.files())expected.add(entry.path());if(!expected.equals(sources.keySet()))throw new IOException("SOURCE_INVENTORY_MISMATCH");
        output=output.toAbsolutePath().normalize();PackageFiles.safe(output);if(!Files.isDirectory(output.getParent()))throw new IOException("OUTPUT_PARENT_MISSING");
        Path temporary=Files.createTempFile(output.getParent(),".genesis-package-",".tmp");boolean created=false,complete=false;
        try{
            PackageFiles.privateMode(temporary,false);
            try(var zip=new ZipOutputStream(Files.newOutputStream(temporary),StandardCharsets.UTF_8)){
                put(zip,MANIFEST,raw);put(zip,SIGNATURE,signed);
                zip.setLevel(Deflater.BEST_SPEED);
                for(Entry entry:manifest.files()){
                    Path source=sources.get(entry.path());PackageFiles.safe(source);if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("SOURCE_NOT_REGULAR");
                    var hash=PackageFiles.sha();long count=0;zip.putNextEntry(new ZipEntry("files/"+entry.path()));
                    try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))!=-1){count+=n;if(count>entry.bytes())throw new IOException("SOURCE_SIZE_CHANGED");hash.update(buffer,0,n);zip.write(buffer,0,n);}}
                    zip.closeEntry();if(count!=entry.bytes()||!HexFormat.of().formatHex(hash.digest()).equals(entry.sha256()))throw new IOException("SOURCE_CHANGED_DURING_CREATE");
                }
            }
            if(Files.size(temporary)>MAX_ARCHIVE)throw new IOException("ARCHIVE_SIZE_LIMIT");
            try(var channel=FileChannel.open(output,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                created=true;PackageFiles.privateMode(output,false);try(var in=Files.newInputStream(temporary)){var buffer=java.nio.ByteBuffer.allocate(64*1024);byte[] bytes=buffer.array();int n;while((n=in.read(bytes))!=-1){buffer.clear();buffer.limit(n);while(buffer.hasRemaining())channel.write(buffer);}}channel.force(true);
            }complete=true;
        }finally{Files.deleteIfExists(temporary);if(created&&!complete)Files.deleteIfExists(output);}
    }
    private static void put(ZipOutputStream zip,String name,byte[] bytes)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
}
