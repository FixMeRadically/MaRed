package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded local UI channel to an EXISTING owner. This class never starts or stops processes. */
public final class RemoteRepeat71 {
    private final Path root;private final byte[] key;private final Map<String,String> identity;
    private static final Set<String> PHASES=Set.of("WAITING_SERVER","ACTIVE","VALIDATING","PREPARING","READY","ARMING","ARMED","STARTING","CONNECTING","CANCELLED","FAILED","COMPLETE");
    private RemoteRepeat71(Path root,byte[] key,Map<String,String> identity){this.root=root;this.key=key;this.identity=identity;}
    public static RemoteRepeat71 open(Map<String,String> env)throws IOException{
        if(RemoteSession70.open(env)==null)throw new IOException("REMOTE71_NO_OWNER");
        Path root=FirstLaunchFiles.safe(Path.of(env.get("GENESIS_REMOTE70_ROOT")));byte[] key=FirstLaunchFiles.read(root.resolve("channel.key"),32);
        var config=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("session70"),131072));
        return new RemoteRepeat71(root,key,Map.of("run",config.get("run"),"account",config.get("account"),"inventory",config.get("inventory")));
    }
    public Path root(){return root;}
    public synchronized void control(UUID request,byte[] payload)throws IOException{if(payload.length>16384)throw new IOException("CONTROL73_SIZE");var d=new HashMap<>(identity);d.put("request",request.toString());d.put("payload",Base64.getEncoder().encodeToString(payload));FirstLaunchFiles.write(root.resolve("control73-"+request),LocalApply67.sign(key,d),true);}
    public synchronized void submit(UUID request,String action,Path cache,Path candidate)throws IOException{
        if(!Set.of("PREPARE","ARM","CANCEL").contains(action)||request.equals(new UUID(0,0)))throw new IOException("REMOTE71_ACTION");
        if(!cache.isAbsolute()||!candidate.isAbsolute()||cache.toString().length()>4096||candidate.toString().length()>4096)throw new IOException("REMOTE71_PATH");
        var data=new HashMap<>(identity);data.put("schema","71");data.put("request",request.toString());data.put("action",action);data.put("cache",cache.toString());data.put("candidate",candidate.toString());
        FirstLaunchFiles.write(root.resolve("repeat71.request"),LocalApply67.sign(key,data),true);
    }
    public synchronized Map<String,String> status()throws IOException{
        Path file=root.resolve("repeat71.status");if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return Map.of();
        var data=LocalApply67.verify(key,FirstLaunchFiles.read(file,131072));
        if(!data.keySet().equals(Set.of("schema","run","account","inventory","request","phase","at","reason","delta","reports"))||!"71".equals(data.get("schema"))||identity.entrySet().stream().anyMatch(e->!e.getValue().equals(data.get(e.getKey())))||!PHASES.contains(data.get("phase"))||!data.get("at").matches("[1-9][0-9]{11,15}")||data.get("reason").length()>1024||data.get("delta").length()>65536||data.get("reports").length()>4096)throw new IOException("REMOTE71_STATUS");
        UUID.fromString(data.get("request"));return data;
    }
}
