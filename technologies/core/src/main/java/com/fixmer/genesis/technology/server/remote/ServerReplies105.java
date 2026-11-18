package com.fixmer.genesis.technology.server.remote;
import java.util.*;
/** Connection identity and control replies are independent of periodic PING responses. */
public final class ServerReplies105 {
    private record Pending(String action,long at){}
    public record Reply(UUID request,String result){}
    private final Map<UUID,Pending> requests=new LinkedHashMap<>();
    private Object connection;private UUID submitted;private Reply control;
    public synchronized void connection(Object current){if(connection!=current){connection=current;requests.clear();submitted=null;control=null;}}
    public synchronized void register(UUID id,String action,long now){requests.entrySet().removeIf(e->now-e.getValue().at()>10_000_000_000L);if(requests.size()>=8)requests.remove(requests.keySet().iterator().next());requests.put(id,new Pending(action,now));if(!action.equals("PING")){submitted=id;control=null;}}
    public synchronized boolean receive(UUID id,String result,long now){Pending p=requests.remove(id);if(p==null||now<p.at()||now-p.at()>10_000_000_000L)return false;if(!p.action().equals("PING")&&id.equals(submitted))control=new Reply(id,result);return true;}
    public synchronized UUID submitted(){return submitted;}
    public synchronized Reply control(){return control;}
}
