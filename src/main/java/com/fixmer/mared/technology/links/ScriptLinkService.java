package com.fixmer.mared.technology.links;
import com.fixmer.genesis.technology.links.*;
import com.fixmer.genesis.technology.links.ScriptLinks.*;
import com.fixmer.genesis.technology.storage.TextRepository;
import com.fixmer.mared.commands.control.MaredFuncCommand;
import com.fixmer.mared.commands.engine.*;
import net.neoforged.fml.loading.FMLPaths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.Path;
/** Bounded single worker owns IO/compilation. One immutable snapshot is published to UI and server. */
public final class ScriptLinkService {
    private static volatile ScriptLinkService instance;
    public static ScriptLinkService get() {
        var value=instance;
        if(value!=null)return value;
        synchronized(ScriptLinkService.class) {
            if(instance==null) {
                instance=new ScriptLinkService(FMLPaths.CONFIGDIR.get().resolve("mared"));
                instance.refresh();
            }
            return instance;
        }
    }
    public static void changed() {
        var value=instance;
        if(value!=null)value.refresh();
    }
    public record Snapshot(long revision,CapabilityRegistry.Snapshot<ScriptLibrary.Target> catalog,Project project,ScriptLinks.Index index,Map<String,String> errors,String projectText,boolean loaded) {
        public String problem(AiProfile profile) {
            var c=project.binding(profile.condition());
            var a=project.binding(profile.action());
            if(c==null||a==null)return "Missing AI binding";
            String e=catalog.problem(c);
            return e!=null?e:catalog.problem(a);
        }
    }
    private record Cached(String text,ScriptExports.Module module,Map<String,MaredScriptContext.Func> functions) {
    }
    private final TextRepository scripts,links;
    private final Map<String,Cached> cache=new HashMap<>();
    private final CapabilityRegistry<ScriptLibrary.Target> registry=new CapabilityRegistry<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),r-> {
        var t=new Thread(r,"genesis-links");
        t.setDaemon(true);
        return t;
    }
    ,new ThreadPoolExecutor.AbortPolicy());
    private final Set<CompletableFuture<Snapshot>> pending=ConcurrentHashMap.newKeySet();
    private final AtomicLong requested=new AtomicLong();
    private final AtomicBoolean scheduled=new AtomicBoolean();
    private volatile Snapshot snapshot=new Snapshot(0,new CapabilityRegistry.Snapshot<>(0,Map.of()),Project.empty(),new ScriptLinks.Index(Project.empty()),Map.of(),null,false);
    public ScriptLinkService(Path mared) {
        scripts=new TextRepository(mared.resolve("commands"));
        links=new TextRepository(mared.resolve("genesis"));
    }
    public Snapshot snapshot() {
        return snapshot;
    }
    public void refresh() {
        requested.incrementAndGet();
        schedule();
    }
    private void schedule() {
        if(!scheduled.compareAndSet(false,true))return;
        try {
            worker.execute(()-> {
                long processed=0;
                try {
                    do {
                        processed=requested.get();
                        load();
                    }
                    while(processed!=requested.get());
                }
                finally {
                    scheduled.set(false);
                    if(processed!=requested.get())schedule();
                }
            }
            );
        }
        catch(RejectedExecutionException error) {
            scheduled.set(false);
        }
    }
    private void load() {
        var errors=new LinkedHashMap<String,String>();
        var entries=new ArrayList<CapabilityRegistry.Entry<ScriptLibrary.Target>>();
        var modules=new LinkedHashMap<UUID,List<String>>();
        var nextCache=new HashMap<String,Cached>();
        String raw=null;
        Project project=Project.empty();
        try {
            if(links.exists("links"))raw=links.read("links");
            project=ScriptLinkCodec.read(raw);
        }
        catch(Exception error) {
            errors.put("links.txt",message(error));
        }
        try {
            var names=scripts.list();
            if(names.size()>4096)throw new IllegalStateException("Script file limit exceeded");
            long total=0;
            for(String name:names) {
                try {
                    String text=scripts.read(name);
                    total+=text.length();
                    if(total>16_777_216)throw new IllegalStateException("Script catalog text limit exceeded");
                    var cached=cache.get(name);
                    if(cached==null||!cached.text.equals(text))cached=compile(text);
                    nextCache.put(name,cached);
                    if(cached.module.id()!=null)modules.computeIfAbsent(cached.module.id(),k->new ArrayList<>()).add(name);
                }
                catch(Exception error) {
                    errors.put(name,message(error));
                }
                if(total>16_777_216)throw new IllegalStateException("Script catalog text limit exceeded");
            }
            cache.clear();
            cache.putAll(nextCache);
            for(var entry:nextCache.entrySet()) {
                var value=entry.getValue();
                if(value.module.id()==null)continue;
                if(modules.get(value.module.id()).size()!=1) {
                    errors.put(entry.getKey(),"Duplicate module UUID in "+modules.get(value.module.id()));
                    continue;
                }
                for(var export:value.module.exports()) {
                    var fn=value.functions.get(export.function());
                    var ref=new ScriptExports.Reference(value.module.id(),export.id(),export.kind());
                    var symbol=new CapabilityRegistry.Symbol(ref,entry.getKey(),export.function(),export.title(),fn.params);
                    if(entries.size()>=16384)throw new IllegalStateException("Export catalog limit exceeded");
                    entries.add(new CapabilityRegistry.Entry<>(symbol,new ScriptLibrary.Target(value.functions,export.function())));
                }
            }
        }
        catch(Exception error) {
            errors.put("catalog",message(error));
            entries.clear();
            cache.clear();
        }
        entries.sort(Comparator.comparing(e->e.symbol().source()+"/"+e.symbol().function()));
        registry.replace(entries);
        var previous=snapshot;
        var immutableErrors=Map.copyOf(errors);
        var catalog=registry.snapshot();
        long revision=previous.revision();
        if(!previous.loaded()||catalog.revision()!=previous.catalog().revision()||!Objects.equals(raw,previous.projectText())||!immutableErrors.equals(previous.errors()))revision++;
        snapshot=new Snapshot(revision,catalog,project,new ScriptLinks.Index(project),immutableErrors,raw,true);
    }
    private static Cached compile(String text) {
        var metadata=ScriptExports.read(text);
        var functions=new LinkedHashMap<String,MaredScriptContext.Func>();
        if(!metadata.exports().isEmpty()) {
            for(var command:MaredScriptParser.parse(text))if(command instanceof MaredFuncCommand fn) {
                if(fn.parameters().size()>128)throw new IllegalArgumentException("Function parameter limit");
                if(functions.putIfAbsent(fn.name(),new MaredScriptContext.Func(fn.parameters(),fn.body()))!=null)throw new IllegalArgumentException("Duplicate function: "+fn.name());
                if(functions.size()>1024)throw new IllegalArgumentException("Function limit");
            }
            var validation=new MaredScriptContext(null,null,null);
            functions.forEach((name,fn)->validation.registerFunction(name,fn.params,fn.body));
            for(var export:metadata.exports())if(!functions.containsKey(export.function()))throw new IllegalArgumentException("Export function not declared at top level: "+export.function());
        }
        return new Cached(text,metadata,Map.copyOf(functions));
    }
    private static String message(Throwable error) {
        String value=error.getMessage();
        return value==null?error.getClass().getSimpleName():value.substring(0,Math.min(1024,value.length()));
    }
    private CompletableFuture<Snapshot> mutation(java.util.concurrent.Callable<Void> task) {
        var future=new CompletableFuture<Snapshot>();
        pending.add(future);
        future.whenComplete((value,error)->pending.remove(future));
        try {
            worker.execute(()-> {
                try {
                    task.call();
                    load();
                    future.complete(snapshot);
                }
                catch(Exception error) {
                    future.completeExceptionally(error);
                }
            }
            );
        }
        catch(RejectedExecutionException error) {
            future.completeExceptionally(error);
        }
        return future;
    }
    private void save(Project project,String expected)throws Exception {
        String current=links.exists("links")?links.read("links"):null;
        if(!Objects.equals(current,expected))throw new IllegalStateException("Links changed externally; refresh before saving");
        String data=ScriptLinkCodec.write(project);
        if(data.length()>1_048_576)throw new IllegalArgumentException("Link document size limit");
        if(current==null) {
            if(!links.create("links",data))throw new IllegalStateException("Links created concurrently");
        }
        else if(!links.write(links.capture("links",expected),data))throw new IllegalStateException("Link save conflict");
    }
    public CompletableFuture<Snapshot> put(AiProfile profile,Binding condition,Binding action,String expected) {
        return mutation(()-> {
            load();
            if(!snapshot.loaded()||snapshot.catalog().problem(condition)!=null||snapshot.catalog().problem(action)!=null)throw new IllegalArgumentException("Resolve both functions and their arguments before saving");
            var project=ScriptLinkCodec.read(expected).with(profile,condition,action);
            save(project,expected);
            return null;
        }
        );
    }
    public CompletableFuture<Snapshot> remove(UUID profile,String expected) {
        return mutation(()-> {
            save(ScriptLinkCodec.read(expected).without(profile),expected);
            return null;
        }
        );
    }
    public CompletableFuture<Snapshot> installDemo() {
        return mutation(()-> {
            String source;
            try(var in=ScriptLinkService.class.getResourceAsStream("/genesis/examples/follow.mr")) {
                if(in==null)throw new IllegalStateException("Demo resource missing");
                source=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            }
            if(scripts.exists("genesis.follow")) {
                if(!scripts.read("genesis.follow").equals(source))throw new IllegalStateException("genesis.follow exists; existing user code is preserved");
            }
            else if(!scripts.create("genesis.follow",source))throw new IllegalStateException("Demo file created concurrently");
            load();
            if(snapshot.errors().containsKey("links.txt"))throw new IllegalStateException(snapshot.errors().get("links.txt"));
            if(snapshot.catalog().problem(GenesisAiDemo.condition())!=null||snapshot.catalog().problem(GenesisAiDemo.action())!=null)throw new IllegalStateException("Demo exports unresolved; inspect catalog errors");
            var demo=GenesisAiDemo.profile();
            if(snapshot.project().profiles().stream().noneMatch(p->p.id().equals(demo.id())))save(snapshot.project().with(demo,GenesisAiDemo.condition(),GenesisAiDemo.action()),snapshot.projectText());
            return null;
        }
        );
    }
    public void close() {
        worker.shutdownNow();
        for(var future:pending)future.completeExceptionally(new RejectedExecutionException("Script link service closed"));
    }
}
