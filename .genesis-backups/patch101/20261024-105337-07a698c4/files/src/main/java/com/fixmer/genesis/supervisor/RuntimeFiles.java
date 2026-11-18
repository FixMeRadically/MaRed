package com.fixmer.genesis.supervisor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
/** Only a supervisor-owned launch directory is cleaned. Minecraft locks/world files are never unlinked. */
final class RuntimeFiles {
    final String id=UUID.randomUUID().toString();
    final Path directory;
    RuntimeFiles(Path game)throws IOException {
        directory=game.resolve("config/mared/dev/runtime").resolve(id);
        safe(directory);
        Files.createDirectories(directory);
    }
    static void safe(Path path)throws IOException {
        for(Path p=path.toAbsolutePath();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked runtime path: "+p);
    }
    Path shutdownFile() {
        return directory.resolve("shutdown.request");
    }
    void shutdown()throws IOException {
        safe(shutdownFile());
        Files.writeString(shutdownFile(),id,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
    }
    void shutdown95(String request)throws IOException{
        RestartPermit95.uuid(request);safe(shutdownFile());
        Path temp=Files.createTempFile(directory,"shutdown95-",".tmp");
        try{
            Files.writeString(temp,id+"\nrestart95="+request,StandardCharsets.UTF_8);
            try{Files.move(temp,shutdownFile(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){throw new IOException("RESTART95_ATOMIC_SIGNAL_REQUIRED",unsupported);}
        }finally{Files.deleteIfExists(temp);}
    }
    void cancelShutdown95(String request)throws IOException{safe(shutdownFile());if(Files.isRegularFile(shutdownFile(),LinkOption.NOFOLLOW_LINKS)&&Files.size(shutdownFile())<=128&&Files.readString(shutdownFile()).equals(id+"\nrestart95="+request))Files.deleteIfExists(shutdownFile());}
    void cleanup(SupervisorLog log) {
        try {
            safe(directory);
            Files.deleteIfExists(shutdownFile());
            Files.deleteIfExists(directory.resolve("launch.args"));
            Files.deleteIfExists(directory.resolve("session.properties"));
            Files.deleteIfExists(directory.resolve("game.lease"));
            // A killed JVM can leave one unfinished atomic heartbeat write in its own launch folder.
            try(var temps=Files.newDirectoryStream(directory,"session-*.tmp")){int n=0;for(Path temp:temps){if(++n>64)break;safe(temp);if(Files.isRegularFile(temp,LinkOption.NOFOLLOW_LINKS))Files.deleteIfExists(temp);}}
            Files.deleteIfExists(directory);
        }
        catch(IOException e) {
            log.warn("Launch temp cleanup: "+e.getMessage());
        }
    }
}
