package com.fixmer.genesis.technology.workspace.live122;
import java.nio.file.Path;import java.io.IOException;import java.util.*;
/** Account colors belong to the server store, not to a particular editor instance. */
public final class WorkspaceProfiles152 {
    private final Path root;private final WorkspaceAppearance124 files;private final Map<UUID,Integer> cache=new LinkedHashMap<>();
    public WorkspaceProfiles152(Path root){this.root=root;files=new WorkspaceAppearance124(root);}
    public boolean saved(UUID actor){return java.nio.file.Files.isRegularFile(root.resolve(actor+".rgb"),java.nio.file.LinkOption.NOFOLLOW_LINKS);}
    public static int fallback(UUID actor){int[] palette={0xA879F5,0xF28482,0x80CBC4,0xFFD166,0x7CB5FF};return palette[Math.floorMod(actor.hashCode(),palette.length)];}
    public int color(UUID actor)throws IOException{var known=cache.get(actor);if(known!=null)return known;int rgb=files.load(actor,fallback(actor));if(cache.size()>=512)cache.remove(cache.keySet().iterator().next());cache.put(actor,rgb);return rgb;}
    public void save(UUID actor,int rgb)throws IOException{files.save(actor,rgb);cache.put(actor,rgb);if(cache.size()>512)cache.remove(cache.keySet().iterator().next());}
}
