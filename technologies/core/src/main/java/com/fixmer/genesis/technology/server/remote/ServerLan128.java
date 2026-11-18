package com.fixmer.genesis.technology.server.remote;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Supplements NeoForge's default-route pinger on each usable interface and IP family. */
public final class ServerLan128 implements AutoCloseable {
    private record Sender(MulticastSocket socket,InetAddress group){}
    private final List<Sender> senders=new ArrayList<>();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"genesis-lan128");t.setDaemon(true);return t;});
    private final byte[] packet;
    public static String packet(String motd,int port){if(port<1||port>65535)throw new IllegalArgumentException("PORT");String name=motd.replace('[','(').replace(']',')').replaceAll("[\\p{Cntrl}]","");if(name.length()>160)name=name.substring(0,160);return "[MOTD]"+name+"[/MOTD][AD]"+port+"[/AD]";}
    public ServerLan128(String motd,int port,String bind)throws IOException{
        packet=packet(motd,port).getBytes(StandardCharsets.UTF_8);InetAddress bound=bind.isBlank()?null:InetAddress.getByName(bind);int count=0;
        try{var all=NetworkInterface.getNetworkInterfaces();while(all!=null&&all.hasMoreElements()&&count++<256&&senders.size()<32){
            var network=all.nextElement();if(!network.isUp()||network.isLoopback()||!network.supportsMulticast())continue;
            var addresses=Collections.list(network.getInetAddresses());if(bound!=null&&!bound.isAnyLocalAddress()&&addresses.stream().noneMatch(bound::equals))continue;
            for(boolean ipv6:List.of(false,true)){
                if(addresses.stream().noneMatch(a->ipv6?a instanceof Inet6Address:a instanceof Inet4Address))continue;
                if(bound!=null&&!bound.isAnyLocalAddress()&&(ipv6!=(bound instanceof Inet6Address)))continue;
                MulticastSocket socket=null;try{socket=new MulticastSocket();socket.setTimeToLive(1);socket.setNetworkInterface(network);senders.add(new Sender(socket,InetAddress.getByName(ipv6?"FF75:230::60":"224.0.2.60")));socket=null;}catch(IOException unavailable){}finally{if(socket!=null)socket.close();}
            }
        }if(senders.isEmpty())throw new IOException("SERVER128_NO_LAN_INTERFACE");worker.scheduleWithFixedDelay(this::send,0,1500,TimeUnit.MILLISECONDS);}catch(IOException|RuntimeException failed){close();throw failed;}
    }
    private void send(){for(var sender:senders)try{sender.socket().send(new DatagramPacket(packet,packet.length,sender.group(),4445));}catch(IOException unavailable){/* Other interfaces and direct TCP connections remain usable. */}}
    public int interfaces(){return senders.size();}
    @Override public void close(){worker.shutdownNow();for(var sender:senders)sender.socket().close();}
}
