package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.scripts.SourceProjects;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import java.nio.file.*;
import java.util.*;

/** Binds a shared room to the existing Scripts project on its owner's computer. */
public final class WorkspaceScripts129 {
    private final SourceProjects sources;
    private final Path bindings;
    public WorkspaceScripts129(Path sources,Path bindings){this.sources=new SourceProjects(sources);this.bindings=bindings;}
    public List<SourceProjects.Project> projects()throws IOException{return sources.list().projects();}
    public String direction147(String source)throws IOException{return sources.direction147(source);}
    public Map<String,String> snapshot(String source)throws IOException{
        var result=new TreeMap<String,String>();
        for(String path:sources.files(source)){
            if(com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(path))continue;
            try{com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.path(path);}catch(IllegalArgumentException invalid){throw new IOException("SOURCE_PATH: "+path,invalid);}
            String current=sources.read(source,path);if(current.codePointCount(0,current.length())>16384)throw new IOException("TEXT_LIMIT: "+path);var recovery=sources.recovery(source,path);
            if(recovery!=null&&!recovery.text().equals(current))throw new IOException("SOURCE_DRAFT");
            result.put(path,current);
        }
        WorkspaceProject126.pack(result);return Map.copyOf(result);
    }
    public Map<String,String> models147(String source)throws IOException{
        var result=new TreeMap<String,String>();long total=0;
        for(String path:sources.files(source))if(com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(path)){
            String text=sources.read(source,path);var recovery=sources.recovery(source,path);
            if(recovery!=null&&!recovery.text().equals(text))throw new IOException("SOURCE_DRAFT");
            total+=text.getBytes(StandardCharsets.UTF_8).length;if(total>32L*1024*1024||result.size()>=64)throw new IOException("MODEL_LIMIT147");result.put(path,text);
        }return Map.copyOf(result);
    }
    private Path file(UUID actor,String store,UUID room)throws IOException{
        if(!store.matches("[a-f0-9]{64}"))throw new IOException("IDENTITY");
        return FirstLaunchFiles.safe(bindings.resolve(actor.toString()).resolve(store).resolve(room+".binding"));
    }
    private static final int STATE_LIMIT132=4*1024*1024;
    private Map<String,String> load(Path path)throws IOException{return WorkspaceCodec122.decode(FirstLaunchFiles.read(path,STATE_LIMIT132),STATE_LIMIT132);}
    /** A rename can temporarily contain both old and new paths. The checkpoint must not use the 64-file wire import limit. */
    private static String encodeBaseline132(Map<String,String> baseline)throws IOException{
        if(baseline.size()>128)throw new IOException("LIMIT");var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new DeflaterOutputStream(bytes))){out.writeInt(baseline.size());for(var entry:new TreeMap<>(baseline).entrySet()){out.writeUTF(entry.getKey());byte[] text=entry.getValue().getBytes(StandardCharsets.UTF_8);out.writeInt(text.length);out.write(text);}}
        if(bytes.size()>STATE_LIMIT132/2)throw new IOException("LIMIT");return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    private static Map<String,String> decodeBaseline132(Map<String,String> state)throws IOException{
        if(!state.containsKey("baseline132"))return unpack(state.get("data"));
        byte[] compressed;try{compressed=Base64.getDecoder().decode(state.get("baseline132"));}catch(IllegalArgumentException bad){throw new IOException("SOURCE_IO",bad);}
        byte[] raw=WorkspaceCodec122.inflate142(compressed,STATE_LIMIT132,"LIMIT");
        var result=new TreeMap<String,String>();try(var in=new DataInputStream(new ByteArrayInputStream(raw))){int count=in.readInt();if(count<0||count>128)throw new IOException("LIMIT");for(int i=0;i<count;i++){String path=in.readUTF();if(!com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(path))throw new IOException("SOURCE_PATH");int length=in.readInt();if(length<0||length>65536)throw new IOException("TEXT_LIMIT");byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException();String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();if(result.put(path,text)!=null)throw new IOException("SOURCE_IO");}if(in.read()!=-1)throw new IOException("SOURCE_IO");}return result;
    }
    private void save(Path path,String source,Map<String,String> baseline)throws IOException{
        Files.createDirectories(path.getParent());FirstLaunchFiles.privateMode(path.getParent());
        FirstLaunchFiles.write(path,WorkspaceCodec122.encode(Map.of("source",source,"baseline132",encodeBaseline132(baseline)),STATE_LIMIT132),true);
    }
    private static Map<String,String> unpack(String value)throws IOException{if(value==null)throw new IOException("SOURCE_IO");return value.isEmpty()?Map.of():WorkspaceProject126.unpack(value);}
    public void bind(UUID actor,String store,UUID room,String source,String packed)throws IOException{
        Path path=file(actor,store,room);
        if(Files.exists(path)){if(!source.equals(load(path).get("source")))throw new IOException("SOURCE_CHANGED");modelBaseline147(actor,store,room,source);return;}
        var baseline=new TreeMap<>(unpack(packed));baseline.keySet().removeIf(com.fixmer.genesis.technology.creation.BlockbenchDocument146::validPath);
        if(!snapshot(source).equals(baseline))throw new IOException("SOURCE_CHANGED");
        save(path,source,baseline);modelBaseline147(actor,store,room,source);
    }
    private Path modelState147(UUID actor,String store,UUID room)throws IOException{return file(actor,store,room).resolveSibling(room+".models147");}
    private Map<String,String> modelHashes147(Path state)throws IOException{
        var result=new TreeMap<String,String>();if(!Files.exists(state))return result;
        String rows=WorkspaceCodec122.decode(FirstLaunchFiles.read(state,32700),32700).getOrDefault("files","");
        for(String row:rows.lines().toList()){String[] parts=row.split("\\|",-1);if(parts.length!=2||!parts[1].matches("[a-f0-9]{64}"))throw new IOException("SOURCE_IO");WorkspaceModels147.path(parts[0]);if(result.put(parts[0],parts[1])!=null)throw new IOException("SOURCE_IO");}return result;
    }
    private void writeModelHashes147(Path state,Map<String,String> hashes)throws IOException{
        String rows=String.join("\n",new TreeMap<>(hashes).entrySet().stream().map(e->e.getKey()+"|"+e.getValue()).toList());
        FirstLaunchFiles.write(state,WorkspaceCodec122.encode(Map.of("files",rows),32700),true);
    }
    public void modelBaseline147(UUID actor,String store,UUID room,String source)throws IOException{
        Path state=modelState147(actor,store,room);if(Files.exists(state))return;var hashes=new TreeMap<String,String>();var original=decodeBaseline132(load(file(actor,store,room)));
        for(String model:sources.files(source))if(com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(model))hashes.put(model,com.fixmer.genesis.technology.workspace.collaboration121.WorkspaceRelease121.sha(original.getOrDefault(model,sources.read(source,model)).getBytes(StandardCharsets.UTF_8)));
        writeModelHashes147(state,hashes);
    }
    /** Mirror only a known clean local copy; local edits and recovery drafts are never replaced. */
    public void mirrorModel147(UUID actor,String store,UUID room,String path,String text)throws IOException{
        WorkspaceModels147.path(path);String source=load(file(actor,store,room)).get("source");modelBaseline147(actor,store,room,source);Path state=modelState147(actor,store,room);var hashes=modelHashes147(state);
        String current=sources.files(source).contains(path)?sources.read(source,path):null;
        var sha=new java.util.function.Function<String,String>(){public String apply(String value){return value==null?"":com.fixmer.genesis.technology.workspace.collaboration121.WorkspaceRelease121.sha(value.getBytes(StandardCharsets.UTF_8));}};
        if(!Objects.equals(current,text)&&current!=null&&!sha.apply(current).equals(hashes.get(path)))throw new IOException("SOURCE_DRAFT");
        var draft=sources.recovery(source,path);if(draft!=null&&!Objects.equals(draft.text(),current)&&!Objects.equals(draft.text(),text))throw new IOException("SOURCE_DRAFT");
        if(!Objects.equals(current,text)){if(current==null)sources.createFile(source,path,text);else sources.save(source,path,current,text);}
        sources.checkpoint(source,path,text,text);hashes.put(path,sha.apply(text));writeModelHashes147(state,hashes);
    }
    public boolean bound(UUID actor,String store,UUID room)throws IOException{return Files.exists(file(actor,store,room));}
    /** Every successful file step is checkpointed. Retrying after interruption is idempotent. */
    public void sync(UUID actor,String store,UUID room,String packed)throws IOException{
        Path binding=file(actor,store,room);if(!Files.exists(binding))throw new IOException("SOURCE_BINDING_MISSING");
        var state=load(binding);String source=state.get("source");modelBaseline147(actor,store,room,source);var baseline=new TreeMap<>(decodeBaseline132(state));var remote=new TreeMap<>(unpack(packed));
        boolean migrated=baseline.keySet().removeIf(com.fixmer.genesis.technology.creation.BlockbenchDocument146::validPath);remote.keySet().removeIf(com.fixmer.genesis.technology.creation.BlockbenchDocument146::validPath);if(migrated)save(binding,source,baseline);
        var local=new TreeMap<String,String>();for(String path:sources.files(source))if(!com.fixmer.genesis.technology.creation.BlockbenchDocument146.validPath(path))local.put(path,sources.read(source,path));
        var paths=new TreeSet<>(baseline.keySet());paths.addAll(remote.keySet());
        for(String path:remote.keySet())if(!com.fixmer.genesis.technology.scripts.GradleProjectImport.editable(path))throw new IOException("SOURCE_PATH");
        for(String path:paths){String old=baseline.get(path),next=remote.get(path),current=local.get(path);
            if(Objects.equals(old,next))continue;
            var draft=sources.recovery(source,path);if(draft!=null&&!Objects.equals(draft.text(),current)&&!Objects.equals(draft.text(),next))throw new IOException("SOURCE_DRAFT");
            if(!Objects.equals(current,old)&&!Objects.equals(current,next))throw new IOException("SOURCE_CHANGED");
        }
        for(String path:paths){String old=baseline.get(path),next=remote.get(path),current=local.get(path);
            if(Objects.equals(old,next))continue;
            if(sources.recovery(source,path)!=null)sources.checkpoint(source,path,current==null?"":current,current==null?"":current);
            if(!Objects.equals(current,next)){
                if(next==null)sources.trashEntry(source,path,Map.of(path,current));
                else if(current==null)sources.createFile(source,path,next);
                else sources.save(source,path,current,next);
            }
            if(next==null)baseline.remove(path);else baseline.put(path,next);
            save(binding,source,baseline);
        }
    }
}
