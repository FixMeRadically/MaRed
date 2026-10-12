package com.fixmer.mared.technology.storage;

import com.fixmer.genesis.technology.storage.*;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local-only adapter. inbox is user supplied ZIP data; outbox contains complete saved-file exports. */
public final class ProjectTransfers {
    public record Preview(ProjectArchive.Plan plan, List<String> conflicts) {
        public Preview { conflicts = List.copyOf(conflicts); }
    }
    private ProjectTransfers() {}
    public static Path folder(String name) throws IOException {
        if (!name.equals("inbox") && !name.equals("outbox")) throw new IOException("Invalid transfer folder");
        Path folder=FMLPaths.CONFIGDIR.get().resolve("mared/transfers/"+name).toAbsolutePath().normalize();
        for(Path part=folder;part!=null;part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Transfer path contains a symlink");
        Files.createDirectories(folder);
        return folder;
    }
    public static List<String> list() throws IOException {
        var names=new ArrayList<String>();
        try(var entries=Files.newDirectoryStream(folder("inbox"))){
            int visited=0;
            for(Path path:entries){if(++visited>2000)throw new IOException("Too many inbox entries");String name=path.getFileName().toString();
                if(name.endsWith(".zip")&&RepositoryPath.valid(name)&&name.indexOf('/')<0&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))names.add(name);
            }
        }
        names.sort(String::compareTo);return List.copyOf(names);
    }
    public static Preview preview(String name,String destination) throws IOException {
        if(name.indexOf('/')>=0||!RepositoryPath.valid(name)||!name.endsWith(".zip"))throw new IOException("Invalid archive name");
        var plan=ProjectArchive.read(folder("inbox").resolve(name)).beneath(destination);
        return new Preview(plan,MaredCommandStorage.projectConflicts(plan));
    }
    public static Path export() throws IOException {
        var plan=MaredCommandStorage.projectSnapshot();
        Path output=folder("outbox").resolve("commands-"+java.util.UUID.randomUUID()+".zip");
        ProjectArchive.write(output,plan);return output;
    }
    public static void apply(Preview preview) throws IOException {
        if(!preview.conflicts().isEmpty())throw new IOException("Resolve import conflicts first");
        MaredCommandStorage.importProject(preview.plan());
    }
}
