package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Bounded local mailbox for one supervised root JVM. No remote commands or caller-selected save paths. */
final class ServerLifecycleChannel implements AutoCloseable {
    static final Set<String> BINDING_KEYS=Set.of("schema","run","profile","epoch","ticket","nonce");
    static final Set<String> FRAME_KEYS=Set.of("schema","run","profile","epoch","ticket","nonce","pid","start","sequence","revision","phase","ticks","tickAgeMs","requestSequence","request","outcome","saveMs","error","operation","stopRequested");
    record Request(long sequence,UUID id,long issuedAt,long expiresAt,boolean cancelled,ServerLifecycleState.Operation operation){
        Request(long sequence,UUID id,long issuedAt,long expiresAt,boolean cancelled){this(sequence,id,issuedAt,expiresAt,cancelled,ServerLifecycleState.Operation.CHECKPOINT);}
    }
    final Path run;final Properties binding;private final ServerFiles.Lease lease;
    private long sequence,lastRevision,lastTicks;private boolean closed;private Properties gameRuntime;
    private ServerLifecycleChannel(Path run,Properties binding,ServerFiles.Lease lease){this.run=run;this.binding=binding;this.lease=lease;}
    static void prepare(Path run)throws IOException {
        Properties ticket=ServerFiles.read(run.resolve("handoff.ticket")),p=new Properties();p.setProperty("schema","42");
        for(String key:List.of("run","profile","epoch","ticket"))p.setProperty(key,ticket.getProperty(key));p.setProperty("nonce",UUID.randomUUID().toString());
        if(Files.exists(run.resolve("lifecycle.binding"),LinkOption.NOFOLLOW_LINKS))throw new IOException("LIFECYCLE_BINDING_EXISTS");ServerFiles.write(run.resolve("lifecycle.binding"),p);
    }
    static Properties binding(Path run)throws IOException {
        Properties p=ServerFiles.read(run.resolve("lifecycle.binding"));if(!p.stringPropertyNames().equals(BINDING_KEYS)||!p.getProperty("schema").equals("42")||!p.getProperty("run").equals(run.getFileName().toString())||ServerFiles.number(p,"epoch",0)<1)throw new IOException("INVALID_LIFECYCLE_BINDING");
        for(String key:List.of("run","profile","ticket","nonce"))if(!ServerFiles.canonicalUuid(p.getProperty(key)))throw new IOException("INVALID_LIFECYCLE_BINDING");
        Properties ticket=ServerFiles.read(run.resolve("handoff.ticket"));for(String key:List.of("run","profile","epoch","ticket"))if(!p.getProperty(key).equals(ticket.getProperty(key)))throw new IOException("LIFECYCLE_OWNER_MISMATCH");return p;
    }
    static ServerLifecycleChannel connect(Path run)throws IOException {
        ServerFiles.safe(run);Properties binding=binding(run);var lease=new ServerFiles.Lease(run.resolve("lifecycle.child.lock"));
        try{if(Files.exists(run.resolve("lifecycle.status"),LinkOption.NOFOLLOW_LINKS))throw new IOException("LIFECYCLE_SESSION_ALREADY_USED");return new ServerLifecycleChannel(run,binding,lease);}catch(IOException|RuntimeException bad){lease.close();throw bad;}
    }
    synchronized void publish(ServerLifecycleState.Snapshot s,long pid,String start)throws IOException {
        if(closed)throw new IOException("LIFECYCLE_CLOSED");if(s.revision()<lastRevision||s.ticks()<lastTicks)return;
        unchanged();if(gameRuntime!=null&&!gameRuntime.equals(readGameRuntime(run)))throw new IOException("GAME_RUNTIME_CHANGED");if(sequence==Long.MAX_VALUE)throw new IOException("LIFECYCLE_SEQUENCE_OVERFLOW");
        Properties p=copy(binding);p.setProperty("pid",Long.toString(pid));p.setProperty("start",start);p.setProperty("sequence",Long.toString(++sequence));p.setProperty("revision",Long.toString(s.revision()));p.setProperty("phase",s.phase().name());p.setProperty("ticks",Long.toString(s.ticks()));p.setProperty("tickAgeMs",Long.toString(s.tickAgeMs()));p.setProperty("requestSequence",Long.toString(s.requestSequence()));p.setProperty("request",s.request()==null?"":s.request().toString());p.setProperty("outcome",s.outcome().name());p.setProperty("saveMs",Long.toString(s.saveMs()));p.setProperty("error",s.error());p.setProperty("operation",s.operation().name());p.setProperty("stopRequested",Boolean.toString(s.stopRequested()));
        ServerFiles.write(run.resolve("lifecycle.status"),p);lastRevision=s.revision();lastTicks=s.ticks();
    }
    /** Optional local adapter observation. It is neither remote attestation nor installation authority. */
    synchronized void recordGameRuntime(Properties facts)throws IOException{
        if(closed||gameRuntime!=null)throw new IOException("GAME_RUNTIME_ALREADY_RECORDED_OR_CLOSED");unchanged();
        if(!facts.stringPropertyNames().equals(Set.of("adapter","adapterSha256","serverClass","serverKind","minecraft","neoforge","genesis","pid","start")))throw new IOException("GAME_RUNTIME_CALLER_FIELDS");
        Properties p=copy(binding);p.putAll(facts);p.setProperty("runtime.schema","54");p.setProperty("runtime.scope","LOCAL_ADAPTER_OBSERVATION_ONLY");
        Path file=run.resolve("game.runtime");if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("GAME_RUNTIME_EXISTS");
        validateRuntime(p);ServerFiles.write(file,p);
        if(!p.equals(readGameRuntime(run)))throw new IOException("GAME_RUNTIME_PUBLICATION_CHANGED");gameRuntime=copy(p);
    }
    static Properties readGameRuntime(Path run)throws IOException{
        Properties p=ServerFiles.read(run.resolve("game.runtime"));validateRuntime(p);verifyBinding(p,binding(run));return p;
    }
    private static void validateRuntime(Properties p)throws IOException{
        var keys=new HashSet<>(BINDING_KEYS);keys.addAll(List.of("runtime.schema","runtime.scope","adapter","adapterSha256","serverClass","serverKind","minecraft","neoforge","genesis","pid","start"));
        if(!p.stringPropertyNames().equals(keys)||!p.getProperty("runtime.schema").equals("54")||!p.getProperty("runtime.scope").equals("LOCAL_ADAPTER_OBSERVATION_ONLY")||!p.getProperty("adapter").equals("com.fixmer.mared.technology.server.GenesisServerLifecycleHooks")||!p.getProperty("adapterSha256").matches("[0-9a-f]{64}")||!p.getProperty("serverClass").matches("[A-Za-z_$][A-Za-z0-9_$.]{0,239}")||!Set.of("DEDICATED","INTEGRATED").contains(p.getProperty("serverKind"))||ServerFiles.number(p,"pid",0)<1||p.getProperty("start").isBlank()||p.getProperty("start").length()>80)throw new IOException("GAME_RUNTIME_SHAPE");
        for(String key:List.of("minecraft","neoforge","genesis"))if(!p.getProperty(key).matches("[A-Za-z0-9_.+\\-]{1,96}"))throw new IOException("GAME_RUNTIME_VERSION");
    }
    synchronized Request request(long now)throws IOException {
        if(closed)return null;unchanged();Path path=run.resolve("checkpoint.request");if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return null;
        Properties p=ServerFiles.read(path);Set<String> keys=new HashSet<>(BINDING_KEYS);keys.addAll(List.of("requestSequence","request","issuedAt","expiresAt","operation"));if(!p.stringPropertyNames().equals(keys))throw new IOException("INVALID_CHECKPOINT_REQUEST");verifyBinding(p,binding);
        long n=ServerFiles.number(p,"requestSequence",0),issued=ServerFiles.number(p,"issuedAt",0),expires=ServerFiles.number(p,"expiresAt",0);String id=p.getProperty("request","");
        if(n<1||!ServerFiles.canonicalUuid(id)||issued<1||expires<=issued||expires-issued>30_000||now<issued)throw new IOException("INVALID_CHECKPOINT_WINDOW");
        Path cancel=run.resolve("checkpoint.cancel");boolean cancelled=false;
        if(Files.exists(cancel,LinkOption.NOFOLLOW_LINKS)){Properties c=ServerFiles.read(cancel);Set<String> ck=new HashSet<>(BINDING_KEYS);ck.add("request");if(!c.stringPropertyNames().equals(ck))throw new IOException("INVALID_CHECKPOINT_CANCEL");verifyBinding(c,binding);cancelled=id.equals(c.getProperty("request"));}
        try{return new Request(n,UUID.fromString(id),issued,expires,cancelled,ServerLifecycleState.Operation.valueOf(p.getProperty("operation")));}catch(IllegalArgumentException invalid){throw new IOException("INVALID_OPERATION",invalid);}
    }
    static void request(Path run,long sequence,UUID id,long now)throws IOException {
        request(run,sequence,id,now,ServerLifecycleState.Operation.CHECKPOINT);
    }
    static void request(Path run,long sequence,UUID id,long now,ServerLifecycleState.Operation operation)throws IOException {
        if(operation==null)throw new IOException("INVALID_OPERATION");if(sequence<1||now<1||now>Long.MAX_VALUE-30_000)throw new IOException("INVALID_CHECKPOINT_WINDOW");Properties p=binding(run);p.setProperty("requestSequence",Long.toString(sequence));p.setProperty("request",id.toString());p.setProperty("issuedAt",Long.toString(now));p.setProperty("expiresAt",Long.toString(now+30_000));p.setProperty("operation",operation.name());ServerFiles.write(run.resolve("checkpoint.request"),p);
    }
    static void cancel(Path run,UUID id)throws IOException{Properties p=binding(run);p.setProperty("request",id.toString());ServerFiles.write(run.resolve("checkpoint.cancel"),p);}
    static void verifyBinding(Properties p,Properties expected)throws IOException{for(String key:BINDING_KEYS)if(!expected.getProperty(key).equals(p.getProperty(key)))throw new IOException("LIFECYCLE_BINDING_MISMATCH");}
    private void unchanged()throws IOException{if(!binding.equals(binding(run)))throw new IOException("LIFECYCLE_BINDING_CHANGED");}
    static Properties copy(Properties p){Properties result=new Properties();result.putAll(p);return result;}
    public synchronized void close()throws IOException{if(closed)return;closed=true;lease.close();}
}
