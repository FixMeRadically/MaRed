package com.fixmer.mared;

import com.fixmer.genesis.technology.server.remote.ServerLan128;
import net.minecraft.client.server.LanServerPinger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Manual Windows/LAN acceptance; real multicast packets, not part of offline check. */
public final class ServerLanNetwork128 {
    public static void main(String[] args)throws Exception{
        String marker="Genesis LAN128 acceptance";
        try(var receiver=new MulticastSocket(null)){
            receiver.setReuseAddress(true);receiver.bind(new InetSocketAddress(4445));receiver.setSoTimeout(1000);
            int joined=0;var group=new InetSocketAddress(InetAddress.getByName("224.0.2.60"),4445);
            for(var network:Collections.list(NetworkInterface.getNetworkInterfaces()))if(network.isUp()&&!network.isLoopback()&&network.supportsMulticast()&&Collections.list(network.getInetAddresses()).stream().anyMatch(a->a instanceof Inet4Address)){try{receiver.joinGroup(group,network);joined++;}catch(Exception unavailable){}}
            if(joined==0)throw new IllegalStateException("No multicast-capable IPv4 interface");
            boolean received=false;try(var sender=new ServerLan128(marker,25565,"")){
                long deadline=System.nanoTime()+8_000_000_000L;
                while(System.nanoTime()<deadline){var packet=new DatagramPacket(new byte[2048],2048);try{receiver.receive(packet);}catch(SocketTimeoutException waiting){continue;}String text=new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.UTF_8);if(!marker.equals(LanServerPinger.parseMotd(text)))continue;if(!"25565".equals(LanServerPinger.parseAddress(text)))throw new AssertionError("Wrong game port");System.out.println("LAN128 real multicast received from "+packet.getAddress().getHostAddress()+"; game port=25565; active senders="+sender.interfaces());received=true;break;}
            }if(!received)throw new AssertionError("LAN announcement not received on this network");
        }
    }
}
