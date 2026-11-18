package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.creation.BlockbenchDocument146;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import static com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;
import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.function.LongSupplier;

/** Bounded, actor-bound snapshot transfers. No partially uploaded model enters the workspace journal. */
public final class WorkspaceModels147 {
    public static final int MAX=4*1024*1024,CHUNK=12288;
    private static final long TTL=60_000_000_000L;
    private record Read(UUID actor,UUID room,String path,byte[] bytes,long until){}
    private record Write(UUID actor,UUID room,String path,String expected,String hash,int size,String encoding,int rawSize,ByteArrayOutputStream bytes,long until){}
    private record Receipt150(UUID actor,UUID room,String path,String hash,int size,boolean created,long until){}
    private final Map<UUID,Receipt150> receipts150=new LinkedHashMap<>();
    private final Map<UUID,Read> reads=new HashMap<>();
    private final Map<UUID,Write> writes=new HashMap<>();
    private final LongSupplier clock;
    public WorkspaceModels147(){this(System::nanoTime);}
    public WorkspaceModels147(LongSupplier clock){this.clock=clock;}
    private void expire(){long now=clock.getAsLong();reads.values().removeIf(v->now-v.until>=0);writes.values().removeIf(v->now-v.until>=0);receipts150.values().removeIf(v->now-v.until>=0);}
    public static void path(String path){WorkspacePolicy121.path(path);if(!BlockbenchDocument146.validPath(path))throw new IllegalArgumentException("MODEL_PATH147");}
    public static byte[] encode(String text)throws IOException{
        if(text==null||text.length()>MAX)throw new IOException("MODEL_LIMIT147");
        ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
        if(encoded.remaining()>MAX)throw new IOException("MODEL_LIMIT147");byte[] result=new byte[encoded.remaining()];encoded.get(result);return result;
    }
    public static String decode(byte[] bytes)throws IOException{
        if(bytes.length>MAX)throw new IOException("MODEL_LIMIT147");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    public static void validate(byte[] bytes)throws IOException {
        if(bytes.length>MAX)throw new IOException("MODEL_LIMIT147");
        String text=decode(bytes);
        String shape=text.startsWith("\uFEFF")?text.substring(1):text;
        if(text.indexOf(0)>=0||!shape.isBlank()&&!shape.stripLeading().startsWith("{"))throw new IOException("MODEL_FORMAT147");
    }
    private static WorkspacePolicy121 access(WorkspaceEngine121 engine,UUID actor,String path){
        path(path);var p=WorkspacePolicy121.restore(engine.policy());p.require(actor,Capability.READ,path);
        if(!p.panel(actor,Panel.CODE,path)||!p.panel(actor,Panel.ASSETS,path))throw new SecurityException("DENIED");return p;
    }
    private final Map<WorkspaceRelease121.File,String> hashes149=new LinkedHashMap<>(32,.75f,true);
    private String hash(WorkspaceRelease121.File file){if(file==null)return "";String hash=hashes149.get(file);if(hash==null){hash=WorkspaceRelease121.sha(file.bytes());if(hashes149.size()>=32)hashes149.remove(hashes149.keySet().iterator().next());hashes149.put(file,hash);}return hash;}
    public synchronized Map<String,String> execute(String op,UUID room,WorkspaceEngine121 engine,UUID actor,Map<String,String> q)throws IOException {
        expire();String path=q.get("path");var policy=access(engine,actor,path);boolean chunk=op.equals("MODEL_GET147")||op.equals("MODEL_PUT147");var file=chunk?null:engine.file149(actor,path);String current=chunk?"":hash(file);var out=new TreeMap<String,String>();
        boolean editable=policy.allows(actor,file==null?Capability.CREATE:Capability.EDIT,path)&&(file==null||policy.allows(actor,Capability.ERASE_CODE,path));
        if(!chunk){out.put("hash147",current);out.put("editable147",Boolean.toString(editable));out.put("size147",Integer.toString(file==null?0:file.size149()));}
        switch(op){
            case "MODEL_STAT147","MODEL_WATCH147" -> { }
            case "MODEL_OPEN147" -> {
                reads.values().removeIf(r->r.actor.equals(actor)&&r.room.equals(room));if(reads.size()>=16)throw new IOException("MODEL_BUSY147");
                byte[] bytes=file==null?new byte[0]:file.bytes();if(bytes.length>MAX)throw new IOException("MODEL_LIMIT147");
                if(q.getOrDefault("encoding149","raw").equals("zlib")){var packed=ModelTransfer149.pack(bytes);bytes=packed.bytes();out.put("encoding149",packed.encoding());out.put("wireSize149",Integer.toString(bytes.length));}
                UUID token=UUID.randomUUID();reads.put(token,new Read(actor,room,path,bytes,clock.getAsLong()+TTL));out.put("token147",token.toString());
            }
            case "MODEL_GET147" -> {
                Read read=reads.get(UUID.fromString(q.get("token")));if(read==null||!read.actor.equals(actor)||!read.room.equals(room)||!read.path.equals(path))throw new SecurityException("MODEL_TOKEN147");
                int offset=Integer.parseInt(q.get("offset"));if(offset<0||offset>read.bytes.length||offset%CHUNK!=0)throw new IllegalArgumentException("MODEL_OFFSET147");
                reads.put(UUID.fromString(q.get("token")),new Read(read.actor,read.room,read.path,read.bytes,clock.getAsLong()+TTL));
                out.put("chunk147",Base64.getEncoder().encodeToString(Arrays.copyOfRange(read.bytes,offset,Math.min(read.bytes.length,offset+CHUNK))));
            }
            case "MODEL_BEGIN147" -> {
                if(!editable)throw new SecurityException("DENIED");
                String expected=q.get("expected"),digest=q.get("hash");int size=Integer.parseInt(q.get("size"));
                if(!current.equals(expected))throw new IllegalStateException("MODEL_CONFLICT147");
                if(!digest.matches("[a-f0-9]{64}")||size<0||size>MAX)throw new IllegalArgumentException("MODEL_LIMIT147");
                writes.values().removeIf(w->w.actor.equals(actor)&&w.room.equals(room));if(writes.size()>=8)throw new IOException("MODEL_BUSY147");
                UUID token=UUID.randomUUID();String encoding=q.getOrDefault("encoding149","raw");int rawSize=q.containsKey("rawSize149")?Integer.parseInt(q.get("rawSize149")):size;
                if(rawSize<0||rawSize>MAX||encoding.equals("raw")&&rawSize!=size)throw new IllegalArgumentException("MODEL_LIMIT147");
                writes.put(token,new Write(actor,room,path,expected,digest,size,encoding,rawSize,new ByteArrayOutputStream(),clock.getAsLong()+TTL));out.put("token147",token.toString());
            }
            case "MODEL_PUT147","MODEL_COMMIT147" -> {
                UUID token=UUID.fromString(q.get("token"));Write write=writes.get(token);
                if(write==null&&op.equals("MODEL_COMMIT147")){
                    var receipt=receipts150.get(token);if(receipt==null||!receipt.actor.equals(actor)||!receipt.room.equals(room)||!receipt.path.equals(path))throw new SecurityException("MODEL_TOKEN147");
                    policy.require(actor,receipt.created?Capability.CREATE:Capability.EDIT,path);if(!receipt.created)policy.require(actor,Capability.ERASE_CODE,path);
                    out.put("hash147",receipt.hash);out.put("size147",Integer.toString(receipt.size));return out;
                }
                if(write==null||!write.actor.equals(actor)||!write.room.equals(room)||!write.path.equals(path))throw new SecurityException("MODEL_TOKEN147");
                boolean canWrite=policy.allows(actor,write.expected.isEmpty()?Capability.CREATE:Capability.EDIT,path)&&(write.expected.isEmpty()||policy.allows(actor,Capability.ERASE_CODE,path));
                if(!canWrite)throw new SecurityException("DENIED");
                if(op.equals("MODEL_PUT147")){
                    byte[] bytes=Base64.getDecoder().decode(q.get("chunk"));int offset=Integer.parseInt(q.get("offset"));
                    if(bytes.length==0||bytes.length>CHUNK||offset<0||offset+bytes.length>write.size)throw new IllegalArgumentException("MODEL_OFFSET147");
                    if(offset==write.bytes.size())write.bytes.write(bytes);
                    else if(offset+bytes.length>write.bytes.size()||!Arrays.equals(bytes,Arrays.copyOfRange(write.bytes.toByteArray(),offset,offset+bytes.length)))throw new IllegalArgumentException("MODEL_OFFSET147");
                    writes.put(token,new Write(write.actor,write.room,write.path,write.expected,write.hash,write.size,write.encoding,write.rawSize,write.bytes,clock.getAsLong()+TTL));
                }else{
                    byte[] bytes=write.bytes.toByteArray();if(bytes.length!=write.size)throw new IOException("MODEL_HASH147");bytes=ModelTransfer149.unpack(bytes,write.encoding,write.rawSize);if(!WorkspaceRelease121.sha(bytes).equals(write.hash))throw new IOException("MODEL_HASH147");validate(bytes);
                    engine.putModel147(actor,path,write.expected,bytes);if(receipts150.size()>=64)receipts150.remove(receipts150.keySet().iterator().next());receipts150.put(token,new Receipt150(actor,room,path,write.hash,bytes.length,write.expected.isEmpty(),clock.getAsLong()+TTL));writes.remove(token);out.put("hash147",write.hash);out.put("size147",Integer.toString(bytes.length));
                }
            }
            default -> throw new IllegalArgumentException("OP");
        }
        return out;
    }
}
