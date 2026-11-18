package com.fixmer.genesis.technology.server.firstlaunch;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded read-only local navigation. Selection does not grant installation authority. */
public final class LocalFileBrowser68 {
    public record Entry(Path path,String name,boolean directory,long bytes){}
    public record Listing(Path directory,List<Entry> entries,boolean truncated,int skipped){}
    private LocalFileBrowser68(){}
    public static Path directory(Path requested)throws IOException{
        Path path=FirstLaunchFiles.safe(requested);
        if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("BROWSER_NOT_DIRECTORY");return path;
    }
    public static Listing list(Path requested,boolean jarOnly,String query,BooleanSupplier cancelled)throws IOException{
        if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("BROWSER_CANCELLED");
        Path path=directory(requested);String filter=Objects.requireNonNull(query).toLowerCase(Locale.ROOT);
        if(filter.length()>256)throw new IOException("BROWSER_FILTER_LIMIT");
        var entries=new ArrayList<Entry>();int examined=0,skipped=0;boolean truncated=false;
        try(var stream=Files.newDirectoryStream(path)){
            for(Path candidate:stream){
                if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("BROWSER_CANCELLED");
                if(++examined>20000){truncated=true;break;}
                String name=candidate.getFileName().toString();if(!name.toLowerCase(Locale.ROOT).contains(filter))continue;
                try{
                    Path item=FirstLaunchFiles.safe(candidate);boolean dir=Files.isDirectory(item,LinkOption.NOFOLLOW_LINKS);
                    if(!dir&&(!jarOnly||!Files.isRegularFile(item,LinkOption.NOFOLLOW_LINKS)||!name.toLowerCase(Locale.ROOT).endsWith(".jar")))continue;
                    if(entries.size()>=1000){truncated=true;break;}
                    entries.add(new Entry(item,name,dir,dir?0:Files.size(item)));
                }catch(IOException|SecurityException rejected){skipped++;}
            }
        }
        entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(e->e.name().toLowerCase(Locale.ROOT)).thenComparing(Entry::name));
        return new Listing(path,List.copyOf(entries),truncated,skipped);
    }
    public static Path select(Path candidate,boolean directory)throws IOException{
        Path path=FirstLaunchFiles.safe(candidate);
        if(directory)return directory(path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))throw new IOException("BROWSER_JAR_REQUIRED");
        String name=path.getFileName().toString();if(!name.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\\.jar")||name.contains(".."))throw new IOException("BROWSER_JAR_NAME");
        long size=Files.size(path);if(size<1||size>64L*1024*1024)throw new IOException("BROWSER_JAR_SIZE");return path;
    }
}
