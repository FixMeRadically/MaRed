package com.fixmer.genesis.technology.scripts;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;

/** Durable single-project opt-in and attempted input hash. No Minecraft, compiler or network dependencies. */
public final class AutoBuildJournal {
    public record State(String root, String project, boolean enabled, boolean paused,
                        boolean retry, String hash, String outcome, String detail) {
        public State {
            Objects.requireNonNull(root); Objects.requireNonNull(project); Objects.requireNonNull(hash);
            Objects.requireNonNull(outcome); Objects.requireNonNull(detail);
            if ((!root.isEmpty() && !Path.of(root).isAbsolute()) || project.length() > 128
                || project.contains("/") || project.contains("\\") || project.equals("..") || project.startsWith("."))
                throw new IllegalArgumentException("Invalid automation project/root");
            if (!hash.isEmpty() && !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid input hash");
            if (!Set.of("OFF","SELECTED","BASELINE","BUILDING","FAILED","STALE","RESTARTING","CANCELLED").contains(outcome)
                || detail.length() > 1024 || (enabled && (root.isBlank() || project.isBlank())))
                throw new IllegalArgumentException("Invalid automation state");
        }
        public static State off() { return new State("", "", false, false, false, "", "OFF", ""); }
        public boolean sameProject(String r, String p) { return root.equals(r) && project.equals(p); }
        public State select(Path r, String p, boolean on) {
            String path = r.toAbsolutePath().normalize().toString();
            boolean same = sameProject(path, p);
            return new State(path,p,on,false,false,same?hash:"",on?"SELECTED":"OFF","");
        }
        public State pause(boolean value) { return new State(root,project,enabled,value,retry,hash,outcome,detail); }
        public State note(String message) { return new State(root,project,enabled,paused,retry,hash,outcome,shortText(message)); }
        public State stop() { return new State(root,project,false,false,false,hash,"OFF",""); }
        public State retryOnce() { return new State(root,project,enabled,false,true,hash,outcome,detail); }
        public State result(String nextHash, String phase, String message) {
            return new State(root,project,enabled,paused,false,nextHash,phase,shortText(message));
        }
    }
    public record Claim(State state, boolean accepted) { }
    private final Path file;
    public AutoBuildJournal(Path file) { this.file = file.toAbsolutePath().normalize(); }
    private static String shortText(String text) {
        if (text == null) return "";
        text = text.replace('\0',' ').replace('\r',' ').replace('\n',' ');
        return text.substring(0,Math.min(1024,text.length()));
    }
    private static void safe(Path p) throws IOException {
        for (Path q=p; q!=null; q=q.getParent()) if (Files.isSymbolicLink(q)) throw new IOException("Linked automation path");
    }
    private static boolean flag(Properties p,String key) throws IOException {
        String raw=p.getProperty(key);
        if (!"true".equals(raw) && !"false".equals(raw)) throw new IOException("Invalid automation flag: "+key);
        return Boolean.parseBoolean(raw);
    }
    public State read() throws IOException {
        safe(file);
        if (!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) return State.off();
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>16384)
            throw new IOException("Invalid automation journal / size limit");
        Properties p=new Properties();
        try (var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { p.load(in); }
        catch (IllegalArgumentException e) { throw new IOException("Malformed automation journal",e); }
        try {
            if (!"1".equals(p.getProperty("schema"))) throw new IllegalArgumentException("Unsupported automation schema");
            if(!p.stringPropertyNames().equals(Set.of("schema","root","project","enabled","paused","retry","hash","outcome","detail")))
                throw new IllegalArgumentException("Unknown or missing automation fields");
            for (String key:List.of("root","project","hash","outcome","detail"))
                if (!p.containsKey(key)) throw new IllegalArgumentException("Incomplete automation journal");
            return new State(p.getProperty("root"),p.getProperty("project"),flag(p,"enabled"),flag(p,"paused"),flag(p,"retry"),
                p.getProperty("hash"),p.getProperty("outcome"),p.getProperty("detail"));
        } catch (IllegalArgumentException e) { throw new IOException("Invalid automation journal",e); }
    }
    public State update(UnaryOperator<State> change) throws IOException {
        safe(file);Files.createDirectories(file.getParent());
        Path lock=file.resolveSibling(file.getFileName()+".lock");safe(lock);
        try (var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
            FileLock acquired;
            try { acquired=channel.tryLock(); }
            catch (OverlappingFileLockException e) { throw new IOException("Automation journal is busy",e); }
            if (acquired==null) throw new IOException("Automation journal is busy");
            try (acquired) {
                State old=read(),next=Objects.requireNonNull(change.apply(old));
                if (old.equals(next)) return old;
                Properties p=new Properties();p.setProperty("schema","1");
                p.setProperty("root",next.root());p.setProperty("project",next.project());
                p.setProperty("enabled",Boolean.toString(next.enabled()));p.setProperty("paused",Boolean.toString(next.paused()));
                p.setProperty("retry",Boolean.toString(next.retry()));p.setProperty("hash",next.hash());
                p.setProperty("outcome",next.outcome());p.setProperty("detail",next.detail());
                Path temp=Files.createTempFile(file.getParent(),"automation-",".tmp");
                try {
                    try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"MaRed background automation; explicit opt-in, one selected project");}
                    try(var flush=FileChannel.open(temp,StandardOpenOption.WRITE)){flush.force(true);}
                    safe(file);
                    if(!read().equals(old))throw new IOException("Automation settings changed externally; update refused");
                    try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
                } finally {Files.deleteIfExists(temp);}
                return next;
            }
        }
    }
    /** Persist BEFORE starting a build. A crash/failed candidate must not create an automatic retry loop. */
    public Claim claim(String root,String project,String fingerprint) throws IOException {
        boolean[] accepted={false};
        State next=update(s->{
            if (!s.enabled() || s.paused() || !s.sameProject(root,project) || (!s.retry() && s.hash().equals(fingerprint))) return s;
            accepted[0]=true;return s.result(fingerprint,"BUILDING","");
        });
        return new Claim(next,accepted[0]);
    }
}
