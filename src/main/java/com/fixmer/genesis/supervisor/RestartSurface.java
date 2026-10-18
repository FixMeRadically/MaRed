package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Portable handover data only: no AWT/Minecraft linkage, arguments or account data. */
public record RestartSurface(int x,int y,int width,int height,int guiWidth,int guiHeight,boolean fullscreen,
                             long epoch,String generation) {
    public RestartSurface {
        if(Math.abs((long)x)>200000||Math.abs((long)y)>200000||width<64||height<64||width>32768||height>32768
            ||guiWidth<64||guiHeight<64||guiWidth>16384||guiHeight>16384||epoch<=0)throw new IllegalArgumentException("Invalid restart surface");
        UUID.fromString(generation);
    }
    public static Path path(Path game){return game.resolve("config/mared/dev/restart-surface.properties");}
    public static RestartSurface load(Path file){
        try{
            RuntimeFiles.safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>4096)return null;
            Properties p=new Properties();try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(in);}
            if(!"1".equals(p.getProperty("schema")))return null;
            RestartSurface s=new RestartSurface(n(p,"x"),n(p,"y"),n(p,"width"),n(p,"height"),n(p,"guiWidth"),n(p,"guiHeight"),Boolean.parseBoolean(p.getProperty("fullscreen")),Long.parseLong(p.getProperty("epoch")),p.getProperty("generation"));
            long age=System.currentTimeMillis()-s.epoch;
            return age>=-2000&&age<300000?s:null;
        }catch(IOException|RuntimeException e){return null;}
    }
    public void save(Path file)throws IOException{
        RuntimeFiles.safe(file);Files.createDirectories(file.getParent());
        Properties p=new Properties();p.setProperty("schema","1");p.setProperty("x",""+x);p.setProperty("y",""+y);p.setProperty("width",""+width);p.setProperty("height",""+height);
        p.setProperty("guiWidth",""+guiWidth);p.setProperty("guiHeight",""+guiHeight);p.setProperty("fullscreen",""+fullscreen);p.setProperty("epoch",""+epoch);p.setProperty("generation",generation);
        Path temp=Files.createTempFile(file.getParent(),"surface-",".tmp");
        try{try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Genesis restart presentation");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static int n(Properties p,String k){return Integer.parseInt(p.getProperty(k));}
    public float seconds(){return Math.max(0,(System.currentTimeMillis()-epoch)/1000f);}
    public static int coreRadius(int height){return Math.max(16,Math.min(48,height/7));}
    public static int textTop(int height){return Math.min(height-36,height/2+coreRadius(height)*2);}
}
