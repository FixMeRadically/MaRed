package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.function.LongSupplier;
/** Bounded authenticated transfer. Review and apply are separate; repeated apply never queues a second restart. */
public final class RemoteTest152 {
    public interface Backend {void authorize(UUID actor)throws Exception;Map<String,String> review(UUID actor,Path folder,String hash,long size)throws Exception;String apply(UUID actor,Path folder)throws Exception;default void cleanup()throws Exception{}default void device153(UUID actor,String fingerprint)throws Exception{}default Map<String,String> review153(UUID actor,Path folder,String hash,long size,String fingerprint)throws Exception{return review(actor,folder,hash,size);}}
    private static final class Upload {final UUID actor;final Path folder;final String hash;String device153="";final long size;long offset,time;String deployment="";Map<String,String> review;Upload(UUID actor,Path folder,String hash,long size,long now){this.actor=actor;this.folder=folder;this.hash=hash;this.size=size;time=now;}}
    private final Path root;private final Backend backend;private final LongSupplier clock;private final Map<String,Upload> uploads=new HashMap<>();
    public RemoteTest152(Path root,Backend backend,LongSupplier clock)throws IOException{this.root=FirstLaunchFiles.safe(root);this.backend=backend;this.clock=clock;Files.createDirectories(this.root);}
    public synchronized Map<String,String> execute(UUID actor,String operation,Map<String,String> q)throws Exception{return execute(actor,operation,q,"");}
    public synchronized Map<String,String> execute(UUID actor,String operation,Map<String,String> q,String fingerprint)throws Exception{
        backend.device153(actor,fingerprint);
        backend.authorize(actor);long now=clock.getAsLong();
        for(var entry:new ArrayList<>(uploads.entrySet()))if(now-entry.getValue().time>1_800_000_000_000L){if(entry.getValue().deployment.isEmpty())discard(entry.getValue());uploads.remove(entry.getKey());}
        if(operation.equals("TEST_BEGIN152")){
            long size=Long.parseLong(q.get("size"));String hash=q.get("hash");if(size<1||size>64L*1024*1024||!hash.matches("[a-f0-9]{64}"))throw new IOException("REMOTE152_LIMIT");
            backend.cleanup();uploads.entrySet().removeIf(entry->!Files.exists(entry.getValue().folder));long folders;try(var paths=Files.list(root)){folders=paths.filter(Files::isDirectory).count();}
            if(folders>=8||uploads.size()>=8||uploads.values().stream().anyMatch(u->u.actor.equals(actor)&&u.deployment.isEmpty()))throw new IOException("REMOTE152_BUSY");
            String token=UUID.randomUUID().toString();Path folder=root.resolve(token);Files.createDirectory(folder);FirstLaunchFiles.privateMode(folder);Files.createFile(folder.resolve("artifact.jar"));var upload=new Upload(actor,folder,hash,size,now);upload.device153=fingerprint;uploads.put(token,upload);return Map.of("token152",token,"offset152","0");
        }
        Upload u=uploads.get(q.get("token"));if(u==null||!u.actor.equals(actor)||!u.device153.equals(fingerprint))throw new SecurityException("DENIED");u.time=now;
        if(operation.equals("TEST_CANCEL152")){if(!u.deployment.isEmpty())throw new IOException("REMOTE152_QUEUED");discard(u);uploads.remove(q.get("token"));return Map.of("cancelled152","true");}
        if(operation.equals("TEST_PUT152")){
            if(u.review!=null||!u.deployment.isEmpty())throw new IOException("REMOTE152_SEALED");long offset=Long.parseLong(q.get("offset"));byte[] chunk=Base64.getDecoder().decode(q.get("chunk"));if(chunk.length<1||chunk.length>12288||offset<0||offset+chunk.length>u.size)throw new IOException("REMOTE152_OFFSET");
            Path jar=u.folder.resolve("artifact.jar");
            if(offset==u.offset){try(var out=java.nio.channels.FileChannel.open(jar,StandardOpenOption.WRITE)){out.position(offset);var bytes=java.nio.ByteBuffer.wrap(chunk);while(bytes.hasRemaining())out.write(bytes);}u.offset+=chunk.length;}
            else if(offset+chunk.length<=u.offset){byte[] old=new byte[chunk.length];try(var in=new RandomAccessFile(jar.toFile(),"r")){in.seek(offset);in.readFully(old);}if(!Arrays.equals(old,chunk))throw new IOException("REMOTE152_OFFSET");}
            else throw new IOException("REMOTE152_OFFSET");return Map.of("offset152",Long.toString(u.offset));
        }
        if(operation.equals("TEST_REVIEW152")){
            if(u.offset!=u.size||!FirstLaunchFiles.hash(u.folder.resolve("artifact.jar"),64L*1024*1024).equals(u.hash))throw new IOException("REMOTE152_HASH");
            if(u.review==null)u.review=Map.copyOf(backend.review153(actor,u.folder,u.hash,u.size,fingerprint));return u.review;
        }
        if(operation.equals("TEST_APPLY152")){
            if(u.review==null)throw new IOException("REMOTE152_REVIEW_REQUIRED");
            if(!u.device153.isEmpty()&&!q.containsKey("hash153"))throw new IOException("AUTH153_PREVIEW_REQUIRED");
            if(q.containsKey("hash153")&&(!u.hash.equals(q.get("hash153"))||!u.review.getOrDefault("target152","").equals(q.get("target153"))||!u.review.getOrDefault("generation152","").equals(q.get("generation153"))))throw new IOException("AUTH153_PREVIEW_CHANGED");
            if(u.deployment.isEmpty())u.deployment=backend.apply(actor,u.folder);
            return Map.of("deployment152",u.deployment,"queued152","true");
        }
        throw new IllegalArgumentException("OP");
    }
    private void discard(Upload upload)throws IOException{try(var paths=Files.walk(upload.folder)){for(Path file:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
    public static Backend server(Path server){return new Backend(){
        final Map<Path,ServerUpdate115.Review> plans=new HashMap<>();
        public void authorize(UUID actor)throws Exception{RemoteSnapshot152.authorize(server,actor);}
        public void device153(UUID actor,String fingerprint)throws Exception{RemoteSnapshot152.device153(server,actor,fingerprint);}
        public void cleanup()throws Exception{
            Path root=ServerApp104.root(server);var admin=DedicatedAdmin75.open(root);var state=admin.view();
            if(!"RUNNING".equals(state.get("phase"))||!"false".equals(state.get("busy"))||Files.exists(root.resolve("review104")))return;
            long generation=Long.parseLong(state.get("generation"));byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);Path staging=root.resolve("remote-tests152");
            try(var folders=Files.list(staging)){for(Path folder:folders.toList()){
                if(!folder.getFileName().toString().matches("[a-f0-9-]{36}")||!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))continue;
                Path marker=folder.resolve("remote152");boolean remove=false;
                if(Files.exists(marker)){var data=com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.verify(key,FirstLaunchFiles.read(marker,16384));remove=admin.run.toString().equals(data.get("run"))&&Long.parseLong(data.get("generation"))<generation;}
                else remove=System.currentTimeMillis()-Files.getLastModifiedTime(Files.exists(folder.resolve("artifact.jar"))?folder.resolve("artifact.jar"):folder).toMillis()>1_800_000L;
                if(remove)try(var files=Files.walk(folder)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(FirstLaunchFiles.safe(file));}
            }}plans.keySet().removeIf(folder->!Files.exists(folder));
        }
        public Map<String,String> review(UUID actor,Path folder,String hash,long size)throws Exception{return review153(actor,folder,hash,size,"");}
        public Map<String,String> review153(UUID actor,Path folder,String hash,long size,String fingerprint)throws Exception{RemoteSnapshot152.seal153(server,folder,actor,hash,size,fingerprint);var plan=ServerUpdate115.review(server,folder);plans.put(folder,plan);return Map.of("hash152",plan.sha256(),"target152",plan.target(),"previous152",plan.previous(),"generation152",plan.generation());}
        public String apply(UUID actor,Path folder)throws Exception{authorize(actor);var plan=plans.get(folder);if(plan==null)throw new IOException("REMOTE152_REVIEW_REQUIRED");return ServerUpdate115.submit(plan).toString();}
    };}
}
