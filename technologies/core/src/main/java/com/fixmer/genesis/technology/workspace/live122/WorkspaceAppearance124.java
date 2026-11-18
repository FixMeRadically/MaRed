package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.storage.AtomicSettings111;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Local UI preference only. It never grants workspace access or identifies a server actor. */
public final class WorkspaceAppearance124 {
    private final Path root;
    public WorkspaceAppearance124(Path root){this.root=root.toAbsolutePath().normalize();}
    public static int parse(String value){
        if(value==null||!value.matches("#?[A-Fa-f0-9]{6}"))throw new IllegalArgumentException("RGB");
        return Integer.parseInt(value.replace("#",""),16);
    }
    private Path file(UUID actor)throws IOException{return FirstLaunchFiles.safe(root.resolve(actor+".rgb"));}
    public int load(UUID actor,int fallback)throws IOException{
        Path file=file(actor);if(!Files.exists(file))return fallback;
        try{return parse(new String(FirstLaunchFiles.read(file,16),java.nio.charset.StandardCharsets.US_ASCII).strip());}
        catch(IllegalArgumentException bad){throw new IOException("COLOR_PREFERENCE",bad);}
    }
    public void save(UUID actor,int rgb)throws IOException{
        if(rgb<0||rgb>0xffffff)throw new IllegalArgumentException("RGB");
        AtomicSettings111.write(file(actor),String.format(Locale.ROOT,"%06X\n",rgb));
    }
}
