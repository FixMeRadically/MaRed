package com.fixmer.genesis.technology.workspace;
import com.fixmer.genesis.technology.storage.AtomicSettings111;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.remote.ControlWire73;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.io.*;
/** Recovery data only. It never authorizes or automatically submits a server mutation. */
public final class WorkspaceDraftCache112 {
 private final Path root;public WorkspaceDraftCache112(Path root){this.root=root.toAbsolutePath().normalize();}
 private Path file(UUID account,UUID store,UUID project,UUID file)throws IOException{return FirstLaunchFiles.safe(root.resolve(account.toString()).resolve(store.toString()).resolve(project.toString()).resolve(file+".draft"));}
 private static void check(String text)throws IOException{try{WorkspaceWire112.unicode(text);if(text.getBytes(StandardCharsets.UTF_8).length>WorkspaceWire112.MAX_TEXT)throw new IOException("LIMIT");}catch(RuntimeException bad){throw new IOException("CORRUPT",bad);}}
 public void save(UUID account,UUID store,UUID project,WorkspaceDraft112 d)throws IOException{Path p=file(account,store,project,UUID.fromString(d.id()));WorkspaceStore112.path(d.path());check(d.base());check(d.text());byte[] bytes=ControlWire73.encode(Map.of("schema","112","file",d.id(),"path",d.path(),"revision",Long.toString(d.revision()),"base",d.base(),"text",d.text()));AtomicSettings111.write(p,new String(bytes,StandardCharsets.US_ASCII));}
 public WorkspaceDraft112 load(UUID account,UUID store,UUID project,UUID file)throws IOException{Path p=file(account,store,project,file);if(!Files.exists(p))return null;var d=ControlWire73.decode(FirstLaunchFiles.read(p,65536));if(!d.keySet().equals(Set.of("schema","file","path","revision","base","text"))||!"112".equals(d.get("schema"))||!file.toString().equals(d.get("file")))throw new IOException("CORRUPT");try{long r=Long.parseLong(d.get("revision"));if(r<1)throw new IOException("CORRUPT");WorkspaceStore112.path(d.get("path"));check(d.get("base"));check(d.get("text"));var result=new WorkspaceDraft112(file.toString(),d.get("path"),r,d.get("base"));result.edit(d.get("text"));return result;}catch(RuntimeException bad){throw new IOException("CORRUPT",bad);}}
}
