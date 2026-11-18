package com.fixmer.genesis.technology.creation;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** One authenticated loopback peer. Queues and generation are never reused across reconnects. */
public final class DesktopBridge148 implements AutoCloseable {
    public static final int MAX_WIRE=26*1024*1024;
    public record Incoming150(long generation,String json){}
    private static final class Peer150 {
        final Socket socket;final long generation;
        final BlockingQueue<Incoming150> incoming=new ArrayBlockingQueue<>(4);
        final BlockingQueue<byte[]> outgoing=new ArrayBlockingQueue<>(4);
        volatile boolean active=true;
        Peer150(Socket socket,long generation){this.socket=socket;this.generation=generation;}
        void close(){active=false;incoming.clear();outgoing.clear();try{socket.close();}catch(IOException ignored){}}
    }
    private final ServerSocket server;
    private final String token;
    private final AtomicLong generations150=new AtomicLong();
    private final ExecutorService workers=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"MaRed desktop bridge");t.setDaemon(true);return t;});
    private volatile Peer150 peer;
    private volatile Socket authenticating150;
    private volatile boolean closed;
    private volatile String error="";
    public DesktopBridge148()throws IOException {
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);token=HexFormat.of().formatHex(bytes);
        server=new ServerSocket();server.bind(new InetSocketAddress("127.0.0.1",0),1);server.setSoTimeout(500);
        workers.execute(this::listen);
    }
    public String pairing(){return "127.0.0.1:"+server.getLocalPort()+"/"+token;}
    public long connection150(){Peer150 current=peer;return !closed&&current!=null&&current.active?current.generation:0;}
    public boolean connected(){return connection150()!=0;}
    public String error(){return error;}
    public Incoming150 poll150(){Peer150 current=peer;return current==null||!current.active?null:current.incoming.poll();}
    public String poll(){Incoming150 packet=poll150();return packet==null?null:packet.json;}
    public boolean send(String json){return send150(connection150(),json);}
    public boolean send150(long generation,String json){
        Peer150 current=peer;if(closed||current==null||!current.active||generation==0||current.generation!=generation||json==null||json.indexOf('\n')>=0||json.length()>MAX_WIRE)return false;
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_WIRE)return false;return current.outgoing.offer(bytes);
    }
    private void listen(){
        while(!closed){Socket socket=null;Peer150 accepted=null;try{
            socket=server.accept();authenticating150=socket;if(closed)throw new IOException("BRIDGE_CLOSED");socket.setTcpNoDelay(true);socket.setSoTimeout(5000);
            var input=new BufferedInputStream(socket.getInputStream(),32768);String line=line(input,256);
            if(!MessageDigest.isEqual(("MARED148 "+token).getBytes(StandardCharsets.UTF_8),line.getBytes(StandardCharsets.UTF_8)))throw new IOException("PAIRING");
            if(closed)throw new IOException("BRIDGE_CLOSED");accepted=new Peer150(socket,generations150.incrementAndGet());peer=accepted;authenticating150=null;error="";
            Peer150 current=accepted;workers.execute(()->write(current));
            while(!closed&&current.active){
                socket.setSoTimeout(30000);String message=line(input,MAX_WIRE);if(message.equals("{\"type\":\"ping\"}"))continue;
                if(!current.incoming.offer(new Incoming150(current.generation,message)))throw new IOException("BRIDGE_QUEUE");
            }
        }catch(SocketTimeoutException idle){if(accepted!=null)error="BRIDGE_TIMEOUT";}
         catch(IOException|RuntimeException failure){if(!closed)error=failure.getMessage()==null?"BRIDGE_IO":failure.getMessage();}
         finally{if(accepted!=null){accepted.close();if(peer==accepted)peer=null;}if(socket!=null)try{socket.close();}catch(IOException ignored){}if(authenticating150==socket)authenticating150=null;}
        }
    }
    private void write(Peer150 current){
        try{var stream=current.socket.getOutputStream();while(!closed&&current.active){byte[] message=current.outgoing.poll(500,TimeUnit.MILLISECONDS);if(message!=null&&current.active){stream.write(message);stream.write('\n');stream.flush();}}}
        catch(IOException|InterruptedException failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();current.close();}
    }
    public static String line(InputStream input,int limit)throws IOException {
        if(limit<0||limit>MAX_WIRE)throw new IOException("BRIDGE_LIMIT");var bytes=new ByteArrayOutputStream(Math.min(limit,8192));int b;
        while((b=input.read())!=-1){if(b=='\n')return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();if(bytes.size()>=limit)throw new IOException("BRIDGE_LIMIT");bytes.write(b);}
        throw new EOFException("BRIDGE_CLOSED");
    }
    @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}Socket pending=authenticating150;if(pending!=null)try{pending.close();}catch(IOException ignored){}Peer150 current=peer;if(current!=null)current.close();workers.shutdownNow();}
}
