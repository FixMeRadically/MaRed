package com.fixmer.genesis.technology.server;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Owns an immutable snapshot; failed cleanup remains retryable and observable. */
public final class RuntimeBundle implements AutoCloseable  {
    private static final java.util.Set<RuntimeBundle> PENDING=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    public static void retryCleanup() {
        for(RuntimeBundle b:List.copyOf(PENDING))b.close();
    }
    private final Path root;
    private final List<Path> paths;
    private final String entry;
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile String cleanupError="";
    public RuntimeBundle(Path root,List<Path> paths,String entry) {
        this.root=root.toAbsolutePath().normalize();
        this.paths=paths.stream().map(p->p.toAbsolutePath().normalize()).toList();
        if(paths.isEmpty()||this.paths.stream().anyMatch(p->!p.startsWith(this.root)))throw new IllegalArgumentException("Classpath must belong to snapshot");
        if(!com.fixmer.genesis.technology.scripts.JavaSourceRunner.validMainClass(entry)||protectedName(entry))throw new IllegalArgumentException("Invalid runtime entry class");
        this.entry=entry;
    }
    public String entry() {
        return entry;
    }
    public Path root() {
        return root;
    }
    public String cleanupError() {
        return cleanupError;
    }
    public URLClassLoader loader(ClassLoader parent)throws IOException {
        if(closed.get())throw new IOException("Runtime snapshot closed");
        for(Path p:paths) {
            for(Path a=p;a!=null;a=a.getParent())if(Files.isSymbolicLink(a))throw new IOException("Linked runtime snapshot");
            if(!p.toRealPath().startsWith(root.toRealPath()))throw new IOException("Runtime classpath escapes snapshot");
        }
        URL[] urls=new URL[paths.size()];
        for(int i=0;i<urls.length;i++)urls[i]=paths.get(i).toUri().toURL();
        return new URLClassLoader(urls,parent) {
            protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(protectedName(name))return getParent()==null?Class.forName(name,false,null):getParent().loadClass(name);
                Class<?> type=findLoadedClass(name);
                if(type==null)try {
                    type=findClass(name);
                }
                catch(ClassNotFoundException absent) {
                    type=super.loadClass(name,false);
                }
                if(resolve)resolveClass(type);
                return type;
            }
            public URL getResource(String name) {
                URL own=findResource(name);
                return own==null?super.getResource(name):own;
            }
            public Enumeration<URL> getResources(String name)throws IOException {
                var found=new LinkedHashSet<URL>();
                var own=findResources(name);
                while(own.hasMoreElements())found.add(own.nextElement());
                var parent=getParent()==null?super.getResources(name):getParent().getResources(name);
                while(parent.hasMoreElements())found.add(parent.nextElement());
                return Collections.enumeration(found);
            }
        };
    }
    public static boolean protectedName(String name) {
        for(String p:List.of("java.","javax.","jdk.","sun.","com.fixmer.genesis.","com.fixmer.mared.","net.minecraft.","net.neoforged.","com.mojang.","org.slf4j.","io.netty.","com.google.common.","it.unimi.dsi.fastutil."))if(name.startsWith(p))return true;
        return false;
    }
    @Override public synchronized void close() {
        closed.set(true);
        try {
            Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path p,java.nio.file.attribute.BasicFileAttributes a)throws IOException {
                    Files.deleteIfExists(p);return FileVisitResult.CONTINUE;
                }
                public FileVisitResult postVisitDirectory(Path p,IOException error)throws IOException {
                    if(error!=null)throw error;Files.deleteIfExists(p);return FileVisitResult.CONTINUE;
                }
            });
            cleanupError="";
            PENDING.remove(this);
        }
        catch(NoSuchFileException missing) {
            cleanupError="";
            PENDING.remove(this);
        }
        catch(IOException error) {
            cleanupError=error.toString();
            if(PENDING.size()<128)PENDING.add(this);
        }
    }
}
