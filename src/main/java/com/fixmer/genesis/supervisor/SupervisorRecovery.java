package com.fixmer.genesis.supervisor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** JDK-only activation/recovery client. Never kills by port/PID and never stops an active game. */
public final class SupervisorRecovery {
    private SupervisorRecovery() { }
    public static final class Guard implements AutoCloseable {
        private final FileChannel channel;private final FileLock lock;
        private Guard(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
        @Override public void close()throws IOException{try{lock.release();}finally{channel.close();}}
    }
    public static Guard activationGuard(Path config)throws IOException{
        Path path=config.resolveSibling("activation.lock");RuntimeFiles.safe(path);
        FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try{
            FileLock lock=channel.tryLock();if(lock==null)throw new IOException("Another client is already activating this supervisor");
            return new Guard(channel,lock);
        }catch(IOException|RuntimeException e){channel.close();throw new IOException("Supervisor activation is already in progress",e);}
    }
    public static void awaitAvailable(int port,Path config,Properties saved,Consumer<String> status)throws IOException,InterruptedException{
        long deadline=System.nanoTime()+15_000_000_000L;boolean retiring=false,known=false;
        while(System.nanoTime()<deadline){
            if(!listening(port)){
                if(lockFree(config))return;
                status.accept("Waiting for the previous supervisor to release its session lock...");
                Thread.sleep(100);continue;
            }
            String token=saved.getProperty("controlToken","");
            if(token.length()<32)throw occupied(port,"no saved owner credentials; existing process left untouched");
            Properties owner;
            try{owner=identity(port,token);}
            catch(IOException e){if(known){Thread.sleep(100);continue;}throw occupied(port,"owner cannot be verified; existing process left untouched");}
            if(!matches(owner,config,saved,port))throw occupied(port,"different owner/configuration; existing process left untouched");
            known=true;
            boolean canRetire=Boolean.parseBoolean(owner.getProperty("canRetire")),closing=Boolean.parseBoolean(owner.getProperty("gameClosing"));
            if(canRetire&&!retiring){
                status.accept("Retiring the previous supervisor after its game session ended...");
                try{retiring=post(port,token,"/retire",saved.getProperty("instanceId"),0);}catch(IOException ignored){/* The watchdog may have closed it first. */}
            }else if(!retiring&&!closing){
                throw occupied(port,"an active game/handoff still owns this supervisor (game PID "+owner.getProperty("gamePid","unknown")+"); it was not stopped");
            }
            Thread.sleep(100);
        }
        throw occupied(port,"the previous owner has not finished shutting down; no new client was launched");
    }
    public static Properties identity(int port,String token)throws IOException{
        Response response=request(port,token,"GET","/identity",null,0);
        if(response.code!=200)throw new IOException("Supervisor identity unavailable: HTTP "+response.code);
        Properties p=new Properties();p.load(new StringReader(response.body));
        if(!"genesis-supervisor".equals(p.getProperty("service"))||!"4".equals(p.getProperty("protocol"))||!"10".equals(p.getProperty("lifecycleVersion")))throw new IOException("Supervisor lifecycle version is not supported; close the previous instance once");
        return p;
    }
    public static boolean matches(Properties owner,Path config,Properties saved,int port){
        try{return saved.getProperty("instanceId","").equals(owner.getProperty("instanceId"))
            &&config.toAbsolutePath().normalize().equals(Path.of(owner.getProperty("configPath")).toAbsolutePath().normalize())
            &&Integer.parseInt(owner.getProperty("controlPort"))==port;}
        catch(RuntimeException e){return false;}
    }
    public static boolean post(int port,String token,String path,String instance,long parent)throws IOException{
        Response response=request(port,token,"POST",path,instance,parent);
        return response.code==202&&response.body.contains("\"ok\":true");
    }
    private record Response(int code,String body) { }
    private static Response request(int port,String token,String method,String path,String instance,long parent)throws IOException{
        HttpURLConnection connection=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(750);connection.setReadTimeout(1000);
        connection.setRequestMethod(method);connection.setRequestProperty("X-Genesis-Token",token);
        if(instance!=null)connection.setRequestProperty("X-Genesis-Instance",instance);
        if(parent>0)connection.setRequestProperty("X-Genesis-Parent",Long.toString(parent));
        try{
            int code=connection.getResponseCode();InputStream stream=code>=400?connection.getErrorStream():connection.getInputStream();
            String body="";
            if(stream!=null)try(stream){byte[] bytes=stream.readNBytes(16385);if(bytes.length>16384)throw new IOException("Supervisor response too large");body=new String(bytes,StandardCharsets.UTF_8);}
            return new Response(code,body);
        }finally{connection.disconnect();}
    }
    static boolean listening(int port)throws IOException{
        try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),500);return true;}
        catch(ConnectException e){return false;}
    }
    static boolean lockFree(Path config)throws IOException{
        Path path=config.resolveSibling("supervisor.lock");RuntimeFiles.safe(path);
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            try{FileLock lock=channel.tryLock();if(lock==null)return false;lock.release();return true;}
            catch(OverlappingFileLockException e){return false;}
        }
    }
    private static IOException occupied(int port,String reason){return new IOException("Port "+port+": "+reason);}
}
