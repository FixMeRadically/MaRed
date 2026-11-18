package com.fixmer.genesis.technology.workspace;
/** A remote revision never silently replaces a dirty buffer. */
public final class WorkspaceDraft112 {
 private final String id;private String path,base,text;private long revision;private boolean conflict;
 public WorkspaceDraft112(String id,String path,long revision,String text){this.id=id;this.path=path;this.revision=revision;this.base=this.text=text;}
 public String id(){return id;}public String path(){return path;}public String base(){return base;}public String text(){return text;}public long revision(){return revision;}public boolean dirty(){return !base.equals(text);}public boolean conflict(){return conflict;}
 public void edit(String value){text=value;}
 public void remote(String path,long revision,String value){if(revision<this.revision)return;this.path=path;if(!dirty()||text.equals(value)){base=text=value;this.revision=revision;conflict=false;}else if(revision!=this.revision||!base.equals(value))conflict=true;}
 public void acknowledged(long expected,String submitted,long revision,String stored){if(this.revision!=expected||!submitted.equals(stored)||revision<=expected){conflict=true;return;}base=stored;this.revision=revision;conflict=false;}
 public void replaceWithRemote(String path,long revision,String value){this.path=path;this.revision=revision;base=text=value;conflict=false;}
}
