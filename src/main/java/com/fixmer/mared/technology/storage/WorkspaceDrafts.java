package com.fixmer.mared.technology.storage;

import com.fixmer.genesis.technology.storage.TextRepository;
import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.google.gson.Gson;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.neoforged.fml.loading.FMLPaths;

/** Latest-snapshot journal. Recovery creates copies, never overwrites an original script. */
public final class WorkspaceDrafts {
    public record Draft(String id,String originalName,String text) {}
    public record Recovered(String name,String originalName,String text) {}
    private record Envelope(int version,List<Draft> documents) {}
    private record Pending(TextRepository.Ticket ticket,String json) {}
    private static final Gson GSON=new Gson();
    private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Genesis-drafts");t.setDaemon(true);return t;});
    private static TextRepository shared;
    private static java.nio.file.Path sharedPath;
    private final TextRepository store;
    private final AtomicReference<Pending> pending=new AtomicReference<>();
    private final AtomicBoolean scheduled=new AtomicBoolean();
    private boolean writable=true;
    private final AtomicReference<IOException> failure=new AtomicReference<>();
    public IOException takeFailure(){return failure.getAndSet(null);}
    public WorkspaceDrafts(){
        var path=FMLPaths.CONFIGDIR.get().resolve("mared/drafts").toAbsolutePath().normalize();
        synchronized(WorkspaceDrafts.class){if(!path.equals(sharedPath)){shared=new TextRepository(path);sharedPath=path;}store=shared;}
    }
    public List<Recovered> recover() throws IOException {
        try {
            if(!store.exists("workspace"))return List.of();
            Envelope saved=GSON.fromJson(store.read("workspace"),Envelope.class);
            if(saved==null||saved.version!=1||saved.documents==null||saved.documents.size()>64)throw new IOException("Unsupported/corrupt draft journal");
            var seen=new HashSet<String>();
            for(Draft draft:saved.documents)if(draft==null||draft.id==null||!draft.id.matches("[a-f0-9]{32}")||draft.text==null||!seen.add(draft.id))throw new IOException("Invalid draft entry");
            var result=new ArrayList<Recovered>();
            for(Draft draft:saved.documents){
                String name="recovered."+draft.id;
                if(MaredCommandStorage.exists(name)&&!MaredCommandStorage.readCommand(name).equals(draft.text))name+="."+Integer.toUnsignedString(draft.text.hashCode(),16);
                if(MaredCommandStorage.exists(name)){
                    if(!MaredCommandStorage.readCommand(name).equals(draft.text))throw new IOException("Recovery copy has different content: "+name);
                }else if(!MaredCommandStorage.createCommand(name,draft.text))throw new IOException("Cannot create recovery copy: "+name);
                result.add(new Recovered(name,draft.originalName,draft.text));
            }
            if(!store.write(store.capture("workspace"),GSON.toJson(new Envelope(1,List.of()))))throw new IOException("Cannot acknowledge recovery");
            return List.copyOf(result);
        }catch(Exception error){writable=false;throw error instanceof IOException io?io:new IOException("Cannot recover drafts; journal preserved",error);}
    }
    private Pending prepare(List<Draft> documents) throws IOException {
        if(!writable)throw new IOException("Draft journal preserved after recovery failure; repair it before writing");
        if(documents.size()>64)throw new IOException("Draft journal supports at most 64 documents");
        var seen=new HashSet<String>();
        for(Draft draft:documents)if(draft==null||draft.id==null||!draft.id.matches("[a-f0-9]{32}")||draft.text==null||!seen.add(draft.id))throw new IOException("Invalid draft snapshot");
        String json=GSON.toJson(new Envelope(1,List.copyOf(documents)));
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>TextRepository.MAX_BYTES)throw new IOException("Draft snapshot exceeds 4 MiB; previous checkpoint preserved");
        if(!store.exists("workspace"))store.create("workspace",GSON.toJson(new Envelope(1,List.of())));
        return new Pending(store.capture("workspace"),json);
    }
    public void submit(List<Draft> documents) throws IOException {
        Pending snapshot=prepare(documents);if(snapshot==null)return;
        pending.set(snapshot);
        if(scheduled.compareAndSet(false,true))WRITER.execute(this::drain);
    }
    private void drain(){
        try {Pending snapshot;while((snapshot=pending.getAndSet(null))!=null){try{if(!store.write(snapshot.ticket,snapshot.json)&&store.current(snapshot.ticket))throw new IOException("Draft journal changed before checkpoint could commit");}catch(IOException error){failure.set(error);Mared.LOGGER.error("Draft checkpoint failed",error);}}}
        finally{scheduled.set(false);if(pending.get()!=null&&scheduled.compareAndSet(false,true))WRITER.execute(this::drain);}
    }
    public void flush(List<Draft> documents) throws IOException {
        Pending snapshot=prepare(documents);pending.set(null);
        if(snapshot!=null&&!store.write(snapshot.ticket,snapshot.json))throw new IOException("Draft checkpoint could not commit");
    }
}

