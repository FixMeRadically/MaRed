package com.fixmer.genesis.technology.creation;

import com.fixmer.genesis.technology.scripts.SourceProjects;
import com.fixmer.genesis.technology.storage.ProjectPath;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/** A browser may save only a bbmodel in its captured project, with optimistic conflict checks. */
public final class BlockbenchDocument146 {
    private final SourceProjects storage;
    private final String project;
    private String path, baseline;
    private BlockbenchDocument146(SourceProjects storage,String project,String path,String baseline) {
        this.storage=Objects.requireNonNull(storage);this.project=Objects.requireNonNull(project);this.path=path;this.baseline=baseline;
    }
    public static boolean validPath(String path) {
        return ProjectPath.valid(path)&&path.toLowerCase(Locale.ROOT).endsWith(".bbmodel");
    }
    public static BlockbenchDocument146 create(SourceProjects storage,String project) {
        return new BlockbenchDocument146(storage,project,"",null);
    }
    public static BlockbenchDocument146 open(SourceProjects storage,String project,String path)throws IOException {
        requirePath(path);return new BlockbenchDocument146(storage,project,path,storage.read(project,path));
    }
    private static void requirePath(String path)throws IOException {
        if(!validPath(path))throw new IOException("Choose a relative .bbmodel path inside the project");
    }
    public synchronized String path(){return path;}
    public String project(){return project;}
    public synchronized String content(){return baseline;}
    /** A different filename is Save As: never replace an existing file implicitly. */
    public synchronized void save(String target,String content)throws IOException {
        requirePath(target);
        if(target.equals(path)&&baseline!=null)storage.save(project,target,baseline,content);
        else storage.createFile(project,target,content);
        path=target;baseline=content;
        // A previous text-editor recovery record must not resurrect an obsolete model after restart.
        storage.checkpoint(project,target,content,content);
    }
}
