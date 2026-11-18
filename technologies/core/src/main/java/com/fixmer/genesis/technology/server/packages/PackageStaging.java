package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.*;
import java.util.zip.ZipFile;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Local verified content store. No downloader, installation permission or trust-on-first-use. */
public final class PackageStaging {
    private static final byte[] FORMAT="GENESIS-PACKAGE-CACHE-1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final long BUDGET=2L*1024*1024*1024;
    private PackageStaging(){}
    public static final class Prepared {
        private final Path cache;private final PackageArchive.Verified verified;private final Target target;private final Map<String,PublicKey> trust;private final int written,reused;
        private Prepared(Path cache,PackageArchive.Verified verified,Target target,Map<String,PublicKey> trust,int written,int reused){this.cache=cache;this.verified=verified;this.target=target;this.trust=Map.copyOf(trust);this.written=written;this.reused=reused;}
        public Path cache(){return cache;}public PackageArchive.Verified verified(){return verified;}public Target target(){return target;}public int writtenObjects(){return written;}public int reusedObjects(){return reused;}
        public PackageArchive.Verified recheck()throws IOException{var current=PackageArchive.verify(archive(cache,verified.archiveSha256()),trust,target);if(!current.manifestSha256().equals(verified.manifestSha256()))throw new IOException("STAGED_MANIFEST_CHANGED");for(Entry entry:current.manifest().forSide(target.side()))object(entry);return current;}
        Path object(Entry entry)throws IOException{if(!verified.manifest().forSide(target.side()).contains(entry))throw new IOException("OBJECT_NOT_IN_SELECTED_INVENTORY");Path path=objectPath(cache,entry.sha256());if(Files.size(path)!=entry.bytes()||!PackageFiles.digest(path,MAX_FILE).equals(entry.sha256()))throw new IOException("STAGED_OBJECT_CORRUPT");return path;}
    }
    public static Prepared prepare(Path cache,Path source,Map<String,PublicKey> trust,Target target)throws IOException{return prepare(cache,source,trust,target,InstallIo.NONE);}
    static Prepared prepare(Path cache,Path source,Map<String,PublicKey> trust,Target target,InstallIo.Fault fault)throws IOException{
        trust=Map.copyOf(trust);var verified=PackageArchive.verify(source,trust,target);cache=cache.toAbsolutePath().normalize();initialize(cache);
        try(var lease=new InstallIo.Lease(cache.resolve("cache.lock"))){
            long needed=Files.isRegularFile(archive(cache,verified.archiveSha256()))?0:Files.size(source);var missing=new HashSet<String>();for(Entry e:verified.manifest().forSide(target.side()))if(!Files.exists(objectPath(cache,e.sha256()),LinkOption.NOFOLLOW_LINKS)&&missing.add(e.sha256()))needed+=e.bytes();if(usage(cache)+needed>BUDGET)throw new IOException("CACHE_BUDGET_EXCEEDED");
            Path stored=archive(cache,verified.archiveSha256());if(Files.exists(stored,LinkOption.NOFOLLOW_LINKS)){if(!PackageFiles.digest(stored,MAX_ARCHIVE).equals(verified.archiveSha256()))throw new IOException("CACHED_ARCHIVE_CORRUPT");}
            else InstallIo.copy(source,stored,verified.archiveSha256(),MAX_ARCHIVE,fault,"stage.archive");
            var checked=PackageArchive.verify(stored,trust,target);if(!checked.archiveSha256().equals(verified.archiveSha256()))throw new IOException("CACHED_ARCHIVE_CHANGED");int written=0,reused=0;
            try(var zip=new ZipFile(stored.toFile())){for(Entry entry:checked.manifest().forSide(target.side())){
                Path object=objectPath(cache,entry.sha256());if(Files.exists(object,LinkOption.NOFOLLOW_LINKS)){if(Files.size(object)!=entry.bytes()||!PackageFiles.digest(object,MAX_FILE).equals(entry.sha256()))throw new IOException("STAGED_OBJECT_CORRUPT");reused++;continue;}
                Path temp=Files.createTempFile(cache.resolve("objects"),".incoming-",".tmp");try{
                    PackageFiles.privateMode(temp,false);var hash=PackageFiles.sha();long count=0;
                    try(var in=zip.getInputStream(zip.getEntry("files/"+entry.path()));var channel=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                        byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){count+=n;if(count>entry.bytes())throw new IOException("STAGED_SIZE_LIMIT");hash.update(bytes,0,n);var buffer=ByteBuffer.wrap(bytes,0,n);while(buffer.hasRemaining())channel.write(buffer);}if(count!=entry.bytes()||!HexFormat.of().formatHex(hash.digest()).equals(entry.sha256()))throw new IOException("STAGED_PAYLOAD_CHANGED");fault.at("stage.object.write");channel.force(true);fault.at("stage.object.force");
                    }Files.move(temp,object,StandardCopyOption.ATOMIC_MOVE);fault.at("stage.object.move");written++;
                }finally{Files.deleteIfExists(temp);}
            }}
            var result=new Prepared(cache,checked,target,trust,written,reused);result.recheck();return result;
        }
    }
    public static Prepared load(Path cache,String hash,Map<String,PublicKey> trust,Target target)throws IOException{return load(cache,hash,trust,target,true);}
    static Prepared metadata(Path cache,String hash,Map<String,PublicKey> trust,Target target)throws IOException{return load(cache,hash,trust,target,false);}
    private static Prepared load(Path cache,String hash,Map<String,PublicKey> trust,Target target,boolean objects)throws IOException{
        try{PackageModel.hash(hash);}catch(IllegalArgumentException bad){throw new IOException("INVALID_ARCHIVE_ID",bad);}cache=cache.toAbsolutePath().normalize();requireFormat(cache);var verified=PackageArchive.verify(archive(cache,hash),trust,target);if(!verified.archiveSha256().equals(hash))throw new IOException("CACHED_ARCHIVE_CORRUPT");var result=new Prepared(cache,verified,target,trust,0,verified.manifest().forSide(target.side()).size());if(objects)result.recheck();return result;
    }
    static Path archive(Path cache,String hash){PackageModel.hash(hash);return cache.resolve("archives").resolve(hash+".zip");}
    private static Path objectPath(Path cache,String hash){PackageModel.hash(hash);return cache.resolve("objects").resolve(hash+".blob");}
    private static void initialize(Path cache)throws IOException{
        PackageFiles.safe(cache);if(!Files.exists(cache,LinkOption.NOFOLLOW_LINKS)){Files.createDirectory(cache);PackageFiles.privateMode(cache,true);InstallIo.atomic(cache.resolve("format"),FORMAT,InstallIo.NONE,"cache.format");}
        requireFormat(cache);InstallIo.directory(cache.resolve("archives"));InstallIo.directory(cache.resolve("objects"));
    }
    private static void requireFormat(Path cache)throws IOException{if(!Arrays.equals(PackageFiles.read(cache.resolve("format"),128),FORMAT))throw new IOException("NOT_A_GENESIS_CACHE");}
    private static long usage(Path cache)throws IOException{long bytes=0;int count=0;try(var stream=Files.walk(cache,3)){var it=stream.iterator();while(it.hasNext()){Path p=it.next();if(++count>8192)throw new IOException("CACHE_ENTRY_LIMIT");PackageFiles.safe(p);if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){bytes+=Files.size(p);if(bytes>BUDGET)throw new IOException("CACHE_BUDGET_EXCEEDED");}}}return bytes;}
}
