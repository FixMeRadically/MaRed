package com.fixmer.genesis.technology.scripts;
import java.io.IOException;
import java.util.List;
/** Capability for other systems: disk-backed project discovery and reads, no write methods. */
public final class ProjectReadView {
 private final SourceProjects projects;
 public ProjectReadView(SourceProjects projects){this.projects=java.util.Objects.requireNonNull(projects);}
 public record Snapshot(SourceProjects.Project project,List<String> files,List<String> folders){public Snapshot{files=List.copyOf(files);folders=List.copyOf(folders);}}
 public SourceProjects.Catalog catalog()throws IOException{return projects.list();}
 public Snapshot snapshot(String id)throws IOException{synchronized(projects){return new Snapshot(projects.project(id),projects.entries(id),projects.folders(id));}}
 public byte[] asset(String id,String path)throws IOException{return projects.asset(id,path);}
 public String text(String id,String path)throws IOException{return projects.read(id,path);}
}
