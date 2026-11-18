package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded transfer of an approved input; the release digest is integrity, never deployment authority. */
public final class WorkspaceSources125 {
    public static final int CHUNK=12288, MAX_BYTES=8*1024*1024;
    public record Entry(String path,long revision,int size,String sha) {}
    private WorkspaceSources125() {}
    public static String manifest(Map<String,WorkspaceRelease121.File> files) {
        return String.join("\n",new TreeMap<>(files).values().stream()
            .map(f->f.path()+"|"+f.revision()+"|"+f.bytes().length+"|"+WorkspaceRelease121.sha(f.bytes())).toList());
    }
    public static final class Download {
        private final String hash;
        private final List<Entry> entries=new ArrayList<>();
        private final Map<String,WorkspaceRelease121.File> files=new LinkedHashMap<>();
        private ByteArrayOutputStream pending=new ByteArrayOutputStream();
        private int index;
        public Download(String hash,String manifest) {
            if(hash==null||!hash.matches("[a-f0-9]{64}")||manifest==null||manifest.length()>32700)throw new IllegalArgumentException("MANIFEST");
            this.hash=hash;long total=0;var paths=new HashSet<String>();
            for(String row:manifest.split("\n",-1)) {
                String[] p=row.split("\\|",-1);if(p.length!=4)throw new IllegalArgumentException("MANIFEST");
                String path=WorkspacePolicy121.path(p[0]);long revision=Long.parseLong(p[1]);int size=Integer.parseInt(p[2]);
                if(revision<1||size<0||size>MAX_BYTES||!p[3].matches("[a-f0-9]{64}")||!paths.add(path.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("MANIFEST");
                for(String prior:paths)if(!prior.equalsIgnoreCase(path)&&(prior.startsWith(path.toLowerCase(Locale.ROOT)+"/")||path.toLowerCase(Locale.ROOT).startsWith(prior+"/")))throw new IllegalArgumentException("PATH_COLLISION");
                total+=size;if(total>MAX_BYTES||entries.size()>=64)throw new IllegalArgumentException("LIMIT");entries.add(new Entry(path,revision,size,p[3]));
            }
        }
        public String hash(){return hash;}
        public Entry next(){return index==entries.size()?null:entries.get(index);}
        public int offset(){return pending.size();}
        public void accept(String path,int offset,String base64) {
            Entry e=next();if(e==null||!e.path().equals(path)||offset!=pending.size()||base64.length()>4*((CHUNK+2)/3))throw new IllegalArgumentException("CHUNK");
            byte[] bytes=Base64.getDecoder().decode(base64);int expected=Math.min(CHUNK,e.size()-offset);
            if(!Base64.getEncoder().encodeToString(bytes).equals(base64)||bytes.length!=expected)throw new IllegalArgumentException("CHUNK");pending.writeBytes(bytes);
            if(pending.size()==e.size()) {
                byte[] content=pending.toByteArray();if(!WorkspaceRelease121.sha(content).equals(e.sha()))throw new IllegalArgumentException("CHANGED");
                files.put(e.path(),new WorkspaceRelease121.File(e.path(),e.revision(),content));index++;pending=new ByteArrayOutputStream();
            }
        }
        public Map<String,WorkspaceRelease121.File> verified() {
            if(next()!=null||!hash.equals(WorkspaceRelease121.digest(files)))throw new IllegalStateException("INCOMPLETE_OR_CHANGED");return Map.copyOf(files);
        }
        /** A fresh, isolated working copy: no stale source, wrapper, or build outputs can enter the input. */
        public Path materialize(Path parent)throws IOException {
            var source=verified();parent=FirstLaunchFiles.safe(parent);Files.createDirectories(parent);
            Path root=Files.createTempDirectory(parent,"approved125-");
            boolean complete=false;Throwable failure=null;
            try{FirstLaunchFiles.privateMode(root);
                Path project=root.resolve("project");Files.createDirectory(project);
                for(var f:source.values()){Path p=FirstLaunchFiles.safe(project.resolve(f.path()));Files.createDirectories(p.getParent());FirstLaunchFiles.write(p,f.bytes(),false);}
                FirstLaunchFiles.write(root.resolve("workspace125"),WorkspaceCodec122.encode(Map.of("hash",hash,"files",manifest(source)),32700),false);
                complete=true;return project;
            }catch(IOException|RuntimeException problem){failure=problem;throw problem;}
            finally{if(!complete)try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}catch(IOException cleanup){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}}

        }
    }
}
