package com.fixmer.genesis.technology.server.remote;
import java.net.*;
import java.util.*;

/** Parses the saved endpoint, preserving bare/scoped IPv6. Host ownership compares address bytes,
 * not a scope-sensitive NetworkInterface.getByInetAddress lookup. No address text is changed for the actual connection. */
public final class ServerLocalTarget109 {
    private ServerLocalTarget109(){}
    public record Endpoint(String host,int port){}
    public enum Result { MATCH, NO_PROPERTIES, PORT_MISMATCH, HOST_UNRESOLVED, HOST_NOT_LOCAL, BIND_MISMATCH }
    public static Endpoint parse(String value){
        if(value==null||value.isBlank()||value.length()>512||value.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException("RETURN109_ENDPOINT");
        String host=value;int port=25565;
        if(value.startsWith("[")){
            int end=value.indexOf(']');if(end<2||value.indexOf(']',end+1)>=0)throw new IllegalArgumentException("RETURN109_ENDPOINT");
            host=value.substring(1,end);String suffix=value.substring(end+1);
            if(!suffix.isEmpty()){if(!suffix.startsWith(":"))throw new IllegalArgumentException("RETURN109_ENDPOINT");port=port(suffix.substring(1));}
        }else{
            if(value.contains("[")||value.contains("]"))throw new IllegalArgumentException("RETURN109_ENDPOINT");
            int colon=value.indexOf(':');if(colon>=0&&colon==value.lastIndexOf(':')){host=value.substring(0,colon);port=port(value.substring(colon+1));}
        }
        if(host.isBlank()||host.contains("/")||host.contains("\\")||host.contains("@"))throw new IllegalArgumentException("RETURN109_ENDPOINT");
        return new Endpoint(host,port);
    }
    private static int port(String text){
        if(!text.matches("[0-9]{1,5}"))throw new IllegalArgumentException("RETURN109_PORT");
        int p=Integer.parseInt(text);if(p<1||p>65535)throw new IllegalArgumentException("RETURN109_PORT");return p;
    }
    private static boolean same(InetAddress a,InetAddress b){return Arrays.equals(a.getAddress(),b.getAddress());}
    public static Result evaluate(int requestedPort,int serverPort,List<InetAddress> targets,List<InetAddress> local,List<InetAddress> binds,boolean defaultBind){
        if(requestedPort!=serverPort)return Result.PORT_MISMATCH;
        if(targets.isEmpty())return Result.HOST_UNRESOLVED;
        boolean onHost=false;
        for(InetAddress target:targets){
            if(!target.isLoopbackAddress()&&local.stream().noneMatch(a->same(target,a)))continue;
            onHost=true;
            if(defaultBind||binds.stream().anyMatch(a->same(target,a)||a.isAnyLocalAddress()&&a.getAddress().length==target.getAddress().length))return Result.MATCH;
        }
        return onHost?Result.BIND_MISMATCH:Result.HOST_NOT_LOCAL;
    }
}
