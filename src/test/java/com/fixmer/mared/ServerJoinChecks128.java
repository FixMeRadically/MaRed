package com.fixmer.mared;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import com.fixmer.mared.technology.server.firstlaunch.DedicatedLan128;
import net.minecraft.client.server.LanServerPinger;
import java.nio.file.*;
import java.util.*;

/** Actual signed session/address checks and vanilla LAN packet format. No player identity is spoofed. */
public final class ServerJoinChecks128 {
    private static int checks;
    private static void ok(boolean b){if(!b)throw new AssertionError("Join128 #"+checks);checks++;}
    private interface Action{void run()throws Exception;}
    private static void reject(Action a)throws Exception{try{a.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Missing rejection #"+checks);}
    private static void sign(Path p,byte[] key,Map<String,String> fields)throws Exception{Files.createDirectories(p.getParent());FirstLaunchFiles.write(p,LocalApply67.sign(key,fields),Files.exists(p));}
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("Genesis join128 ");try{
            ok(ServerJoin128.address("","25565").equals("127.0.0.1:25565"));ok(ServerJoin128.address("0.0.0.0","24444").equals("127.0.0.1:24444"));ok(ServerJoin128.address("::","25565").equals("[::1]:25565"));ok(ServerJoin128.address("192.168.1.134","25565").equals("192.168.1.134:25565"));
            for(String bad:List.of("0","65536","-1","x","25565 extra"))reject(()->ServerJoin128.address("",bad));
            for(String bad:List.of("bad host","../host","host/path","user@host"))reject(()->ServerJoin128.address(bad,"25565"));
            ok(ServerJoin128.sameAddress("localhost","127.0.0.1:25565"));ok(ServerJoin128.sameAddress("[::1]:25565","localhost:25565"));ok(!ServerJoin128.sameAddress("127.0.0.1:4445","localhost:25565"));ok(!ServerJoin128.sameAddress("remote.example:25565","localhost:25565"));ok(!ServerJoin128.sameAddress("bad host","localhost"));
            Path root=ServerApp104.root(folder);byte[] key=new byte[32];Arrays.fill(key,(byte)128);Files.createDirectories(root);Files.write(root.resolve("owner74.key"),key);String run=UUID.randomUUID().toString(),launch=UUID.randomUUID().toString();
            sign(root.resolve("owner104"),key,Map.of("schema","104","run",run,"profile",folder.toString()));Path channel=root.resolve("runs").resolve(launch);
            sign(channel.resolve("launch104"),key,Map.of("schema","104","run",run,"profile",folder.toString(),"launch",launch,"generation","1"));
            var session=new HashMap<>(Map.of("schema","74","run",run,"generation","1","port","24444","bind",""));sign(channel.resolve("server-1.session"),key,session);
            var state=new HashMap<>(Map.of("phase","RUNNING","updated",Long.toString(System.currentTimeMillis()),"busy","false","run",run,"generation","1","launch",launch));
            Files.writeString(folder.resolve("server.properties"),"server-port=4445\n");ok(ServerJoin128.target(folder,state).address().equals("127.0.0.1:24444"));
            for(String phase:List.of("STARTING","STOPPED","FAILED","OWNER_UNAVAILABLE","RESTARTING")){var changed=new HashMap<>(state);changed.put("phase",phase);reject(()->ServerJoin128.target(folder,changed));}
            var changed=new HashMap<>(state);changed.put("updated","1");reject(()->ServerJoin128.target(folder,changed));changed.putAll(state);changed.put("busy","true");reject(()->ServerJoin128.target(folder,changed));changed.putAll(state);changed.put("run",UUID.randomUUID().toString());reject(()->ServerJoin128.target(folder,changed));
            session.put("generation","2");sign(channel.resolve("server-1.session"),key,session);reject(()->ServerJoin128.target(folder,state));session.put("generation","1");sign(channel.resolve("server-1.session"),new byte[32],session);reject(()->ServerJoin128.target(folder,state));
            ok(DedicatedLan128.advertise("",true,false));ok(DedicatedLan128.advertise("0.0.0.0",true,false));ok(!DedicatedLan128.advertise("127.0.0.1",true,false));ok(!DedicatedLan128.advertise("::1",true,false));ok(!DedicatedLan128.advertise("",false,false));ok(!DedicatedLan128.advertise("",true,true));
            String packet=ServerLan128.packet("Genesis local server",25565);ok(LanServerPinger.parseAddress(packet).equals("25565"));ok(LanServerPinger.parseMotd(packet).equals("Genesis local server"));ok(!LanServerPinger.parseAddress(packet).equals("4445"));
            ok(LanServerPinger.parseAddress(ServerLan128.packet("fake[/MOTD][AD]4445[/AD]",25565)).equals("25565"));reject(()->ServerLan128.packet("x",0));ok(ServerJoin128.lanAddresses("127.0.0.1","25565").isEmpty());ok(ServerJoin128.lanAddresses("192.168.1.134","24444").equals(List.of("192.168.1.134:24444")));
            System.out.println("Join128 checks passed: "+checks+"; signed live endpoint, stale/changed/forged sessions, actual game port and vanilla LAN format; UI and real world entry tested separately.");
        }finally{try(var paths=Files.walk(folder)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
