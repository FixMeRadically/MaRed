package com.fixmer.mared.commands.storage;

import com.fixmer.genesis.technology.storage.TextRepository;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import net.neoforged.fml.loading.FMLPaths;

/** Persistent configuration: read failures never become an empty list eligible for overwrite. */
public final class MaredPersistentStorage {
    private MaredPersistentStorage() {}
    private static Path path;
    private static TextRepository repository;
    private static List<String> cached;
    private static String cachedText;
    private static TextRepository repo() {
        Path current=FMLPaths.CONFIGDIR.get().resolve("mared").toAbsolutePath().normalize();
        if(!current.equals(path)){path=current;repository=new TextRepository(current);cached=null;cachedText=null;}
        return repository;
    }
    public static synchronized List<String> load() {
        var store=repo();
        if(cached!=null)return new ArrayList<>(cached);
        try {
            String text=store.exists("persistent")?store.read("persistent"):null;
            var names=new ArrayList<String>();
            if(text!=null)for(String line:text.split("\\R")){
                String name=line.trim();if(!name.isEmpty()&&!name.startsWith("#"))names.add(name);
            }
            cachedText=text;cached=List.copyOf(names);return new ArrayList<>(names);
        }catch(IOException error){throw new UncheckedIOException("Cannot read persistent.txt; configuration preserved",error);}
    }
    public static synchronized void save(List<String> names) {
        var store=repo();load();
        String text=String.join("\n",List.copyOf(names))+(names.isEmpty()?"":"\n");
        try {
            if(cachedText==null)store.create("persistent",text);
            else if(!store.write(store.capture("persistent",cachedText),text))throw new IOException("Persistent configuration changed during save");
            cachedText=text;cached=List.copyOf(names);
        }catch(IOException error){cached=null;cachedText=null;throw new UncheckedIOException("Cannot save persistent.txt; previous configuration preserved",error);}
    }
    public static synchronized void add(String name) {
        if(name==null||name.isEmpty())return;
        var names=load();if(!names.contains(name)){names.add(name);save(names);}
    }
    public static synchronized void remove(String name) {
        if(name==null)return;
        var names=load();if(names.remove(name))save(names);
    }
    public static synchronized boolean isPersistent(String name){return name!=null&&load().contains(name);}
    public static synchronized void invalidateCache(){cached=null;cachedText=null;}
}
