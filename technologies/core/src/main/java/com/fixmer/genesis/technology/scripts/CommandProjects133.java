package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.storage.*;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One-time migration. Original Commands files are never changed; long-name mapping is retained. */
public final class CommandProjects133 {
    private CommandProjects133(){}
    public static synchronized void migrate(Path destination,Path legacy)throws IOException{
        destination=FirstLaunchFiles.safe(destination);legacy=FirstLaunchFiles.safe(legacy);
        if(destination.startsWith(legacy)||legacy.startsWith(destination))throw new IOException("Commands source and destination overlap");
        Files.createDirectories(destination);
        try(var channel=FileChannel.open(destination.resolve(".commands-migration135.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            java.nio.channels.FileLock lock;
            try{lock=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Another editor is migrating Commands",busy);}
            if(lock==null)throw new IOException("Another process is migrating Commands");
            try(lock){migrateOwned(destination,legacy);}
        }
    }
    private static void migrateOwned(Path destination,Path legacy)throws IOException{
        Path marker=destination.resolve(".legacy-commands133");
        if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)){
            if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid Commands migration marker");
            return;
        }
        var sources=new SourceProjects(destination);var catalog=sources.list();
        for(var project:catalog.projects()){
            var metadata=new Properties();Path path=destination.resolve(project.id()).resolve("genesis-project.properties");
            try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                byte[] data=input.readNBytes(4097);if(data.length>4096)throw new IOException("Migration metadata limit");
                metadata.load(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString()));
            }
            if("true".equals(metadata.getProperty("legacy_commands133"))){Files.writeString(marker,project.id(),StandardOpenOption.CREATE_NEW);return;}
        }
        var plan=ProjectArchive.snapshot(new TextRepository(legacy));
        if(plan.documents().isEmpty()&&plan.directories().isEmpty()){Files.writeString(marker,"empty",StandardOpenOption.CREATE_NEW);return;}
        // Reserve names of malformed directories too: their data may still belong to the user.
        Set<String> names=new HashSet<>();try(var entries=Files.list(destination)){for(Path path:entries.toList())names.add(path.getFileName().toString().toLowerCase(Locale.ROOT));}
        String id="LegacyCommands";for(int n=2;names.contains(id.toLowerCase(Locale.ROOT));n++)id="LegacyCommands"+n;
        Map<String,String> folders=new LinkedHashMap<>(),documents=new LinkedHashMap<>();var used=new HashMap<String,Set<String>>();
        var all=new ArrayList<>(plan.directories());all.addAll(plan.documents().keySet());
        for(String folder:plan.directories().stream().sorted(Comparator.comparingInt(CommandProjects133::depth).thenComparing(String::compareTo)).toList()){
            String parent=RepositoryPath.parent(folder),mapped=parent.isEmpty()?"":folders.get(parent);
            int remaining=1;for(String path:all)if(path.startsWith(folder+"/"))remaining=Math.max(remaining,depth(path)-depth(folder));
            int limit=Math.min(64,256-(mapped.isEmpty()?0:mapped.length()+1)-2*remaining-3);
            folders.put(folder,allocate(mapped,RepositoryPath.leaf(folder),"",limit,used));
        }
        for(String source:plan.documents().keySet()){
            String parent=RepositoryPath.parent(source),mapped=parent.isEmpty()?"":folders.get(parent);
            int limit=Math.min(64,256-(mapped.isEmpty()?0:mapped.length()+1));
            documents.put(source,allocate(mapped,RepositoryPath.leaf(source),".mr",limit,used));
        }
        Path temporary=Files.createTempDirectory(destination,".commands-migrate-");
        try{
            var staging=new SourceProjects(temporary);staging.createEmpty(id);
            for(String folder:folders.values())staging.createDirectory(id,folder);
            for(var entry:plan.documents().entrySet())staging.createFile(id,documents.get(entry.getKey()),entry.getValue());
            Path project=temporary.resolve(id),privateDir=Files.createDirectory(project.resolve(".genesis"));
            var mapping=new Properties();folders.forEach((from,to)->mapping.setProperty("folder/"+from,to));documents.forEach((from,to)->mapping.setProperty("file/"+from,to));
            var writer=new StringWriter();mapping.store(writer,"Original Commands identifier -> migrated path");Files.writeString(privateDir.resolve("migration-paths135.properties"),writer.toString());
            Files.writeString(project.resolve("genesis-project.properties"),"legacy_commands133=true\n",StandardOpenOption.APPEND);
            // No ATOMIC_MOVE here: its replacement behavior is provider-specific when the target exists.
            Files.move(project,destination.resolve(id));Files.writeString(marker,id,StandardOpenOption.CREATE_NEW);
        }finally{if(Files.exists(temporary))try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private static int depth(String path){return path.split("/",-1).length;}
    private static String allocate(String parent,String original,String extension,int limit,Map<String,Set<String>> used)throws IOException{
        if(limit<=extension.length())throw new IOException("Commands path cannot fit destination limits");
        var names=used.computeIfAbsent(parent,k->new HashSet<>());String leaf=original+extension;
        for(int attempt=0;;attempt++){
            if(leaf.length()<=limit&&ProjectPath.valid(parent.isEmpty()?leaf:parent+"/"+leaf)&&!names.contains(leaf.toLowerCase(Locale.ROOT))){names.add(leaf.toLowerCase(Locale.ROOT));return ProjectPath.child(parent,leaf);}
            String hash=com.fixmer.genesis.technology.workspace.collaboration121.WorkspaceRelease121.sha((original+"/"+attempt).getBytes(StandardCharsets.UTF_8));
            int budget=limit-extension.length(),tagLength=Math.min(10,Math.max(1,budget/2));String tag=hash.substring(0,tagLength);
            int cut=Math.min(original.length(),budget-tagLength);if(cut>0&&cut<original.length()&&Character.isHighSurrogate(original.charAt(cut-1))&&Character.isLowSurrogate(original.charAt(cut)))cut--;
            leaf=original.substring(0,cut)+tag+extension;
            if(attempt>10000)throw new IOException("Commands filename collision limit");
        }
    }
}
