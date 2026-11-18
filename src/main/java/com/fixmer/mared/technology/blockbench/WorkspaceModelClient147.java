package com.fixmer.mared.technology.blockbench;

import com.fixmer.genesis.technology.workspace.live122.WorkspaceModels147;
import com.fixmer.genesis.technology.workspace.live122.ModelTransfer149;
import com.fixmer.genesis.technology.workspace.collaboration121.WorkspaceRelease121;
import com.fixmer.mared.technology.workspace.live122.WorkspaceClient122;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** A connection-pinned model channel. All callbacks are marshalled by the existing workspace transport. */
public final class WorkspaceModelClient147 implements AutoCloseable {
    public record Model(String text,String hash,boolean editable){}
    private static final Set<WorkspaceModelClient147> LIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private final WorkspaceClient122 client;
    private final String project;
    private final boolean compression149;
    private volatile boolean closed149;
    private final Set<CompletableFuture<?>> pending149=Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final ThreadPoolExecutor IO149=new ThreadPoolExecutor(1,2,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var thread=new Thread(r,"MaRed model compression");thread.setDaemon(true);return thread;});
    private <T> CompletableFuture<T> work149(Callable<T> task){
        var future=new CompletableFuture<T>();if(closed149)return CompletableFuture.failedFuture(new IOException("CLOSED"));pending149.add(future);future.whenComplete((value,error)->pending149.remove(future));
        try{IO149.execute(()->{T result=null;Exception failure=null;try{result=task.call();}catch(Exception error){failure=error;}T value=result;Exception error=failure;client.execute149(()->{if(closed149)future.completeExceptionally(new IOException("CLOSED"));else if(error!=null)future.completeExceptionally(error);else future.complete(value);});});}catch(RejectedExecutionException error){future.completeExceptionally(new IOException("MODEL_BUSY147",error));}return future;
    }
    private final Consumer<Map<String,String>> metadata;
    public WorkspaceModelClient147(Map<String,String> context,Consumer<Map<String,String>> metadata){this(new WorkspaceClient122(),context,metadata);}
    public WorkspaceModelClient147(WorkspaceClient122 transport,Map<String,String> context,Consumer<Map<String,String>> metadata){
        compression149="true".equals(context.get("compression149"));client=Objects.requireNonNull(transport);project=context.get("project");this.metadata=metadata;client.attach();LIVE.add(this);
    }
    public static void tickAll147(){for(var channel:new ArrayList<>(LIVE))channel.client.tick();}
    private CompletableFuture<Map<String,String>> request(String op,Map<String,String> fields){
        if(closed149)return CompletableFuture.failedFuture(new IOException("CLOSED"));
        var future=new CompletableFuture<Map<String,String>>();pending149.add(future);future.whenComplete((value,error)->pending149.remove(future));
        client.send(WorkspaceClient122.request(op,project,fields),data->{
            if(!"OK".equals(data.get("result")))future.completeExceptionally(new IOException(data.getOrDefault("result","MODEL_IO147")));
            else{try{metadata.accept(data);future.complete(data);}catch(RuntimeException error){future.completeExceptionally(error);}}
        });return future;
    }
    public CompletableFuture<Model> watch(String path){return request("MODEL_WATCH147",Map.of("path",path)).thenApply(d->new Model(null,d.get("hash147"),Boolean.parseBoolean(d.get("editable147"))));}
    public CompletableFuture<Model> stat(String path){return request("MODEL_STAT147",Map.of("path",path)).thenApply(d->new Model(null,d.get("hash147"),Boolean.parseBoolean(d.get("editable147"))));}
    public CompletableFuture<Model> open(String path){
        var fields=new TreeMap<String,String>(Map.of("path",path));if(compression149)fields.put("encoding149","zlib");
        return request("MODEL_OPEN147",fields).thenCompose(data->{
            int length=Integer.parseInt(data.get("size147"));if(length<0||length>WorkspaceModels147.MAX)return CompletableFuture.failedFuture(new IOException("MODEL_LIMIT147"));
            if(length==0)return data.get("hash147").isEmpty()?CompletableFuture.failedFuture(new IOException("MODEL_MISSING147")):CompletableFuture.completedFuture(new Model("",data.get("hash147"),Boolean.parseBoolean(data.get("editable147"))));
            int wireLength=Integer.parseInt(data.getOrDefault("wireSize149",Integer.toString(length)));if(wireLength<1||wireLength>WorkspaceModels147.MAX)return CompletableFuture.failedFuture(new IOException("MODEL_LIMIT147"));
            return get(path,data.get("token147"),wireLength,new ByteArrayOutputStream()).thenCompose(wire->work149(()->{
                byte[] bytes=ModelTransfer149.unpack(wire,data.getOrDefault("encoding149","raw"),length);
                if(!WorkspaceRelease121.sha(bytes).equals(data.get("hash147")))throw new CompletionException(new IOException("MODEL_HASH147"));
                try{return new Model(WorkspaceModels147.decode(bytes),data.get("hash147"),Boolean.parseBoolean(data.get("editable147")));}catch(IOException invalid){throw new CompletionException(invalid);}
            }));
        });
    }
    private CompletableFuture<byte[]> get(String path,String token,int length,ByteArrayOutputStream bytes){
        int offset=bytes.size();return request("MODEL_GET147",Map.of("path",path,"token",token,"offset",Integer.toString(offset))).thenCompose(data->{
            byte[] chunk=Base64.getDecoder().decode(data.get("chunk147"));if(chunk.length!=Math.min(WorkspaceModels147.CHUNK,length-offset))return CompletableFuture.failedFuture(new IOException("MODEL_OFFSET147"));
            bytes.writeBytes(chunk);return bytes.size()==length?CompletableFuture.completedFuture(bytes.toByteArray()):get(path,token,length,bytes);
        });
    }
    private static void text149(String text)throws IOException{
        if(text==null||text.length()>WorkspaceModels147.MAX)throw new IOException("MODEL_LIMIT147");long bytes=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(i+1)))throw new IOException("MODEL_FORMAT147");i++;bytes+=4;}else if(Character.isLowSurrogate(c))throw new IOException("MODEL_FORMAT147");else bytes+=c<128?1:c<2048?2:3;if(bytes>WorkspaceModels147.MAX)throw new IOException("MODEL_LIMIT147");}
    }
    private record Upload149(ModelTransfer149.Packed wire,String hash,int rawSize){}
    public CompletableFuture<Model> save(String path,String expected,String text){
        try{text149(text);}catch(IOException failure){return CompletableFuture.failedFuture(failure);}
        return work149(()->{byte[] bytes=WorkspaceModels147.encode(text);WorkspaceModels147.validate(bytes);return new Upload149(compression149?ModelTransfer149.pack(bytes):new ModelTransfer149.Packed(bytes,"raw"),WorkspaceRelease121.sha(bytes),bytes.length);}).thenCompose(upload->{
            var fields=new TreeMap<String,String>(Map.of("path",path,"expected",expected,"hash",upload.hash,"size",Integer.toString(upload.wire.bytes().length)));
            if(compression149){fields.put("encoding149",upload.wire.encoding());fields.put("rawSize149",Integer.toString(upload.rawSize));}
            return request("MODEL_BEGIN147",fields).thenCompose(data->put(path,data.get("token147"),upload.wire.bytes(),0)).thenApply(data->new Model(text,data.get("hash147"),true));
        });
    }
    private CompletableFuture<Map<String,String>> put(String path,String token,byte[] bytes,int offset){
        if(offset==bytes.length)return request("MODEL_COMMIT147",Map.of("path",path,"token",token));
        int next=Math.min(bytes.length,offset+WorkspaceModels147.CHUNK);
        return request("MODEL_PUT147",Map.of("path",path,"token",token,"offset",Integer.toString(offset),"chunk",Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,offset,next))))
            .thenCompose(data->put(path,token,bytes,next));
    }
    public CompletableFuture<Void> seed(Map<String,String> models){
        CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
        for(var entry:new TreeMap<>(models).entrySet())chain=chain.thenCompose(nothing->stat(entry.getKey()).thenCompose(remote->{
            String hash=WorkspaceRelease121.sha(entry.getValue().getBytes(StandardCharsets.UTF_8));
            if(hash.equals(remote.hash()))return CompletableFuture.completedFuture(null);
            if(!remote.hash().isEmpty())return CompletableFuture.completedFuture(null);
            return save(entry.getKey(),"",entry.getValue()).thenApply(saved->null);
        }));return chain;
    }
    @Override public void close(){closed149=true;LIVE.remove(this);client.detach();synchronized(pending149){for(var future:new ArrayList<>(pending149))future.completeExceptionally(new IOException("CLOSED"));pending149.clear();}}
}
