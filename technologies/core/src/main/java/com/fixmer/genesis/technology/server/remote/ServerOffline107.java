package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** A stopped-folder observation, never permission to adopt a PID or erase recovery records.
 * The real starter repeats these checks and takes the lifetime owner lock before launching. */
public final class ServerOffline107 implements AutoCloseable {
    private final List<AutoCloseable> held = new ArrayList<>();
    private ServerOffline107() {}
    private void lock(Path path, boolean create) throws Exception {
        path=FirstLaunchFiles.safe(path);
        FileChannel channel=create ? FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
                : FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        held.add(channel);
        try { FileLock lock=channel.tryLock(0,1,false);if(lock==null)throw new IOException("SERVER107_FOLDER_IN_USE");held.add(lock); }
        catch(OverlappingFileLockException busy){throw new IOException("SERVER107_FOLDER_IN_USE",busy);}
    }
    public static ServerOffline107 acquire(Path server) throws Exception {
        server=FirstLaunchFiles.safe(server);Path root=ServerApp104.root(server);
        if(!Files.isDirectory(root))throw new IOException("SERVER105_NOT_MANAGED");
        var guard=new ServerOffline107();
        try {
            guard.lock(root.resolve("start104.lock"),true);
            guard.lock(root.resolve("owner104.lock"),true);
            guard.lock(root.resolve("game104.lock"),true);
            // A pending replacement needs its journal recovery; do not bypass it with a new start.
            if(Files.exists(FirstLaunchFiles.safe(root.resolve("change104")),LinkOption.NOFOLLOW_LINKS))throw new IOException("SERVER105_RECOVERY_PENDING");
            var settings=new Properties();Path properties=FirstLaunchFiles.safe(server.resolve("server.properties"));
            if(Files.exists(properties,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.isRegularFile(properties)||Files.size(properties)>1024*1024)throw new IOException("SERVER107_PROPERTIES_REQUIRED");
                try(var reader=Files.newBufferedReader(properties)){settings.load(reader);}
            }
            String level=settings.getProperty("level-name","world");
            if(!level.matches("[A-Za-z0-9_-]{1,80}")||Set.of("mods","config","libraries",".genesis").contains(level.toLowerCase(Locale.ROOT)))throw new IOException("SERVER104_WORLD_NAME");
            Path world=FirstLaunchFiles.safe(server.resolve(level));
            if(Files.exists(world,LinkOption.NOFOLLOW_LINKS))guard.lock(world.resolve("session.lock"),false);
            int port;
            try {port=Integer.parseInt(settings.getProperty("server-port","25565"));}
            catch(NumberFormatException invalid){throw new IOException("SERVER107_PORT",invalid);}
            if(port<1||port>65535)throw new IOException("SERVER107_PORT");
            String bind=settings.getProperty("server-ip","").trim();
            // Avoid DNS on the render/poll path; nonliteral custom bindings require the real starter.
            if(!bind.isEmpty()&&!bind.matches("[0-9a-fA-F:.]+"))throw new IOException("SERVER107_BIND_REVIEW");
            ServerSocket socket=new ServerSocket();guard.held.add(socket);socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(bind.isEmpty()?"0.0.0.0":bind,port));
            return guard;
        } catch(Exception failed) {guard.close();throw failed;}
    }
    @Override public void close() {
        for(int i=held.size()-1;i>=0;i--)try{held.get(i).close();}catch(Exception ignored){}
        held.clear();
    }
    public static Map<String,String> observe(Path server,Map<String,String> stale) throws Exception {
        if(!stale.getOrDefault("phase","").equals("OWNER_UNAVAILABLE"))return stale;
        try(var guard=acquire(server)) {
            // Re-read under the locks: a newer owner/state must never be overwritten by an old poll.
            var latest=ServerApp104.state(server);
            if(!latest.getOrDefault("phase","").equals("OWNER_UNAVAILABLE"))return latest;
            var result=new HashMap<>(latest);result.put("phase","STOPPED");result.put("busy","false");result.put("error","");result.put("offline107","true");
            return Map.copyOf(result);
        } catch(IOException unavailable) {return stale;}
    }
}
