package com.fixmer.genesis.admission;

import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** Admission only: no transformers, Minecraft classes, network, foreign process control or JAR writes. */
public final class ProfileAgent93 {
    private record Held(FileChannel single,FileLock entry,FileChannel profile,FileLock reader) {}
    private static final Map<Path,Held> HELD=new HashMap<>();
    private static final Set<Path> EARLY=new HashSet<>();
    private ProfileAgent93() {}
    public static void premain(String encoded) {
        try {
            Path game=decode(encoded);
            localEnter(game);
            synchronized(ProfileAgent93.class){EARLY.add(game);}
            report(game,true);
            System.out.println("[Genesis profile93] admitted_before_main");
        } catch(IOException|RuntimeException denied) {
            String code=denied.getMessage();
            if(code==null||!code.matches("PROFILE93_[A-Z0-9_]+"))code="PROFILE93_ADMISSION_FAILED";
            System.err.println("[Genesis profile93] "+code+"; launch refused before application main");
            // This JVM has not entered the application. A normal denied start avoids a fatal-agent dump.
            System.exit(73);
        }
    }
    public static String encode(Path game) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(game.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
    }
    public static Path decode(String text)throws IOException {
        try {
            if(text==null||text.length()>16384||!text.matches("[A-Za-z0-9_-]+"))throw new IOException("PROFILE93_BAD_ARGUMENT");
            byte[] bytes=Base64.getUrlDecoder().decode(text);
            String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            Path p=Path.of(value);
            if(!p.isAbsolute()||!p.normalize().equals(p)||!encode(p).equals(text))throw new IOException("PROFILE93_BAD_ARGUMENT");
            return p;
        } catch(IllegalArgumentException e){throw new IOException("PROFILE93_BAD_ARGUMENT",e);}
    }
    // The system copy owns the locks. ModLauncher may define a second copy of these classes.
    private static Class<?> systemCopy()throws IOException {
        try{return Class.forName(ProfileAgent93.class.getName(),false,ClassLoader.getSystemClassLoader());}
        catch(ClassNotFoundException absent){return ProfileAgent93.class;}
        catch(LinkageError e){throw new IOException("PROFILE93_BRIDGE_FAILED",e);}
    }
    private static Object invoke(Class<?> owner,String method,Path game)throws IOException {
        try{return owner.getMethod(method,Path.class).invoke(null,game);}
        catch(InvocationTargetException e){if(e.getCause() instanceof IOException io)throw io;throw new IOException("PROFILE93_BRIDGE_FAILED",e.getCause());}
        catch(ReflectiveOperationException|RuntimeException e){throw new IOException("PROFILE93_BRIDGE_FAILED",e);}
    }
    public static void enter(Path game)throws IOException {
        Class<?> owner=systemCopy();
        if(owner!=ProfileAgent93.class){invoke(owner,"enter",game);return;}
        localEnter(normalize(game));
        if(!early(game))report(normalize(game),false);
    }
    public static boolean held(Path game)throws IOException {
        Class<?> owner=systemCopy();
        if(owner!=ProfileAgent93.class)return Boolean.TRUE.equals(invoke(owner,"held",game));
        synchronized(ProfileAgent93.class){return HELD.containsKey(normalize(game));}
    }
    public static boolean early(Path game)throws IOException {
        Class<?> owner=systemCopy();
        if(owner!=ProfileAgent93.class)return Boolean.TRUE.equals(invoke(owner,"early",game));
        synchronized(ProfileAgent93.class){return EARLY.contains(normalize(game));}
    }
    public static void require(Path game,String expected)throws IOException {
        Path actual=normalize(game);
        if(expected!=null&&(!actual.equals(decode(expected))||!early(actual)))throw new IOException("PROFILE93_EARLY_ADMISSION_MISSING");
        enter(actual);
    }
    private static Path normalize(Path game)throws IOException {
        if(game==null)throw new IOException("PROFILE93_BAD_PROFILE");
        Path p=game.toAbsolutePath().normalize();
        safe(p);
        if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("PROFILE93_BAD_PROFILE");
        return p;
    }
    private static void safe(Path p)throws IOException {
        for(Path n=p;n!=null;n=n.getParent()) {
            if(Files.isSymbolicLink(n))throw new IOException("PROFILE93_LINK_REFUSED");
            if(Files.exists(n,LinkOption.NOFOLLOW_LINKS)&&Files.readAttributes(n,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther())throw new IOException("PROFILE93_SPECIAL_REFUSED");
        }
    }
    private static synchronized void localEnter(Path game)throws IOException {
        game=normalize(game);
        if(HELD.containsKey(game))return;
        // One process uses one profile. Do not allow an argument/profile mismatch to take another lock.
        if(!HELD.isEmpty())throw new IOException("PROFILE93_DIFFERENT_PROFILE");
        Path dir=game.resolve("config/mared/dev");safe(dir);Files.createDirectories(dir);safe(dir);
        Path entry=dir.resolve("profile93.entry.lock"),profile=dir.resolve("profile92.lock");safe(entry);safe(profile);
        long deadline=System.nanoTime()+5_000_000_000L;
        do {
            Held h=attempt(entry,profile);
            if(h!=null){HELD.put(game,h);return;}
            try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("PROFILE93_INTERRUPTED",e);}
        }while(System.nanoTime()<deadline);
        throw new IOException("PROFILE93_IN_USE_OR_RESTORE_IN_PROGRESS");
    }
    private static Held attempt(Path entry,Path profile)throws IOException {
        FileChannel a=null,b=null;FileLock x=null,y=null;
        try {
            a=FileChannel.open(entry,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            x=a.tryLock();
            if(x==null)return null;
            b=FileChannel.open(profile,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            y=b.tryLock(0,Long.MAX_VALUE,true);
            if(y==null)return null;
            Held held=new Held(a,x,b,y);a=null;b=null;x=null;y=null;return held;
        } catch(OverlappingFileLockException busy){return null;}
        finally {
            // Success is pinned until actual JVM exit, including every shutdown hook.
            if(y!=null)y.release();if(b!=null)b.close();if(x!=null)x.release();if(a!=null)a.close();
        }
    }
    private static void report(Path game,boolean beforeMain) {
        Path temp=null;
        try {
            Path target=game.resolve("config/mared/dev/profile-admission-last93.properties");safe(target);
            Properties p=new Properties();p.setProperty("schema","93");
            p.setProperty("phase",beforeMain?"before_main":"mod_constructor_only");
            p.setProperty("pid",Long.toString(ProcessHandle.current().pid()));
            p.setProperty("recorded_ms",Long.toString(System.currentTimeMillis()));
            String launch=System.getenv("GENESIS_LAUNCH_ID");
            p.setProperty("launch",launch!=null&&launch.matches("[a-f0-9-]{36}")?launch:"unmanaged");
            temp=Files.createTempFile(target.getParent(),".admission93-",".tmp");
            try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Diagnostic only; not authority to mutate a profile or terminate a PID");}
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException|RuntimeException failure){System.err.println("[Genesis profile93] diagnostic_unavailable");}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
}
