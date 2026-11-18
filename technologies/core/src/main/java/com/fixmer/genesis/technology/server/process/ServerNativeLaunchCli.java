package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.file.Path;

/** Offline preparation only. There is intentionally no start/execute command. */
public final class ServerNativeLaunchCli {
    private ServerNativeLaunchCli(){}
    public static void main(String[] args){
        try{
            if(args.length!=3)throw new IOException("Usage: prepare CONFIG NEW_DIRECTORY | verify CAPSULE SHA256");
            ServerNativeLaunchCapsule.Frozen frozen=switch(args[0]){
                case "prepare" -> ServerNativeLaunchCapsule.server(ServerProcessConfig.load(Path.of(args[1])),Path.of(args[2]),ServerNativeOwnerProtocol.Binding.fresh());
                case "verify" -> ServerNativeLaunchCapsule.verify(Path.of(args[1]),args[2]);
                default -> throw new IOException("CAPSULE_COMMAND_NOT_SUPPORTED");
            };
            System.out.println(ServerJson.object(ServerNativeLaunchCapsule.describe(frozen)));
        }catch(IOException|IllegalArgumentException bad){System.err.println("Launch plan: "+bad.getMessage());System.exit(2);}
    }
}
