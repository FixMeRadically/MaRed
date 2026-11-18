import com.fixmer.genesis.technology.server.remote.RemoteRepeat71;
import java.nio.file.*;
import java.util.*;

/** Actual Java codec/status/request integration. The profile and loaded-mod proof are file fixtures. */
public final class RemoteRepeatChecks71 {
    public static void main(String[] args)throws Exception{
        var bridge=RemoteRepeat71.open(System.getenv());
        if(args[0].equals("status")){var status=bridge.status();if(status.isEmpty())throw new AssertionError("Missing heartbeat");System.out.println(status.get("phase"));return;}
        if(args[0].equals("request")){bridge.submit(UUID.fromString(args[1]),args[2],Path.of(args[3]),Path.of(args[4]));System.out.println("REQUEST_WRITTEN");return;}
        throw new IllegalArgumentException("fixture action");
    }
}
