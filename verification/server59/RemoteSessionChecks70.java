import com.fixmer.genesis.technology.server.remote.RemoteSession70;
import java.nio.file.*;
import java.util.*;

/** Actual pure-JDK seal/loaded-path/status channel check. Paths are supplied fixtures, not ModList. */
public final class RemoteSessionChecks70 {
    public static void main(String[] args)throws Exception{
        var session=RemoteSession70.open(System.getenv());if(session==null)throw new AssertionError("Missing session");
        List<Path> loaded;try(var files=Files.list(Path.of("mods"))){loaded=files.toList();}
        session.verifyRuntime(loaded,"1.21.1","21.1.264");session.status("READY",0,new UUID(0,0));System.out.println("Remote70 seal, runtime file paths and signed READY verified");
    }
}
