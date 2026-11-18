package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** The address comes from the signed running session, not edited next-start properties or a remote packet. */
public final class ServerJoin128 {
    public record Target(String address,String label,List<String> lan){public Target{lan=List.copyOf(lan);}}
    private ServerJoin128(){}
    public static String address(String bind,String port)throws IOException{
        if(bind==null||port==null||!port.matches("[0-9]{1,5}"))throw new IOException("SERVER128_ADDRESS");
        int value=Integer.parseInt(port);if(value<1||value>65535)throw new IOException("SERVER128_ADDRESS");
        String host=bind.isEmpty()||bind.equals("0.0.0.0")?"127.0.0.1":bind.equals("::")||bind.equals("0:0:0:0:0:0:0:0")?"::1":bind;
        if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
        String result=(host.contains(":")?"["+host+"]":host)+":"+value;
        try{var parsed=ServerLocalTarget109.parse(result);if(!parsed.host().equals(host)||parsed.port()!=value)throw new IllegalArgumentException();}catch(IllegalArgumentException invalid){throw new IOException("SERVER128_ADDRESS",invalid);}
        return result;
    }
    public static boolean sameAddress(String a,String b){try{return canonical(a).equals(canonical(b));}catch(IllegalArgumentException bad){return false;}}
    private static String canonical(String value){var e=ServerLocalTarget109.parse(value);String host=e.host().toLowerCase(Locale.ROOT);if(Set.of("localhost","127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(host))host="loopback";return host+"|"+e.port();}
    public static List<String> lanAddresses(String bind,String port)throws Exception{
        address(bind,port);if(!bind.isEmpty()&&!Set.of("0.0.0.0","::","0:0:0:0:0:0:0:0").contains(bind)){var a=java.net.InetAddress.getByName(bind);return a.isLoopbackAddress()?List.of():List.of(address(bind,port));}
        var result=new TreeSet<String>();var interfaces=java.net.NetworkInterface.getNetworkInterfaces();int count=0;
        while(interfaces!=null&&interfaces.hasMoreElements()&&count++<256){var network=interfaces.nextElement();if(!network.isUp()||network.isLoopback())continue;for(var a:Collections.list(network.getInetAddresses()))if(a instanceof java.net.Inet4Address&&a.isSiteLocalAddress()&&!a.isLoopbackAddress())result.add(address(a.getHostAddress(),port));}
        return result.stream().limit(8).toList();
    }
    public static Target target(Path server,Map<String,String> state)throws Exception{
        if(!ServerView105.of(state,true,false).phase().equals("RUNNING")||"true".equals(state.get("busy")))throw new IOException("SERVER128_NOT_READY");
        server=FirstLaunchFiles.safe(server);Path root=ServerApp104.root(server);byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);
        var owner=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner104"),16384));
        if(!"104".equals(owner.get("schema"))||!server.toString().equals(owner.get("profile"))||!Objects.equals(state.get("run"),owner.get("run")))throw new IOException("SERVER128_SESSION_CHANGED");
        UUID launch;String generation=state.getOrDefault("generation","");try{launch=UUID.fromString(state.get("launch"));if(!launch.toString().equals(state.get("launch"))||launch.equals(new UUID(0,0))||!generation.matches("[0-9]{1,9}"))throw new IllegalArgumentException();}catch(Exception bad){throw new IOException("SERVER128_SESSION_CHANGED",bad);}
        Path channel=FirstLaunchFiles.safe(root.resolve("runs").resolve(launch.toString()));
        var binding=LocalApply67.verify(key,FirstLaunchFiles.read(channel.resolve("launch104"),16384));
        if(!binding.equals(Map.of("schema","104","run",owner.get("run"),"launch",launch.toString(),"generation",generation,"profile",server.toString())))throw new IOException("SERVER128_SESSION_CHANGED");
        var session=LocalApply67.verify(key,FirstLaunchFiles.read(channel.resolve("server-"+generation+".session"),16384));
        if(!"74".equals(session.get("schema"))||!owner.get("run").equals(session.get("run"))||!generation.equals(session.get("generation")))throw new IOException("SERVER128_SESSION_CHANGED");
        String name=server.getFileName()==null?"server":server.getFileName().toString();name=name.replaceAll("[\\p{Cntrl}]","");if(name.length()>64)name=name.substring(0,64);
        List<String> lan;try{lan=lanAddresses(session.get("bind"),session.get("port"));}catch(Exception unavailable){lan=List.of();}return new Target(address(session.get("bind"),session.get("port")),"Genesis · "+name,lan);
    }
}
