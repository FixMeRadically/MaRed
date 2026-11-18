package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** Download preparation lives outside disposable game generations. It cannot close the game. */
public final class RemoteService69 {
    private static final AtomicBoolean busy=new AtomicBoolean(),cancel=new AtomicBoolean();
    private static volatile String phase="IDLE",error="";private static volatile long received;
    private static volatile RemoteClient69 client;private static volatile RemoteClient69.Preview preview;private static volatile RemoteClient69.Download download;
    private static volatile String game="",endpoint="";
    private RemoteService69(){}
    public static boolean busy(){return busy.get();}public static String phase(){return phase;}public static String error(){return error;}
    public static long received(){return received;}public static RemoteClient69.Preview preview(){return preview;}public static RemoteClient69.Download download(){return download;}
    public static String game(){return game;}public static String endpoint(){return endpoint;}
    public static Path root(){String local=System.getenv("LOCALAPPDATA");return local==null?Path.of(System.getProperty("user.home"),".genesis","remote69"):Path.of(local,"Genesis","remote69");}
    private interface Operation{void run()throws Exception;}
    private static void work(String next,Operation operation){
        if(!busy.compareAndSet(false,true))return;cancel.set(false);error="";phase=next;
        Thread thread=new Thread(()->{try{operation.run();}catch(Exception failure){error=RemoteUpdateCli69.safeCode(failure);phase=error.equals("REMOTE_CANCELLED")?"CANCELLED":"FAILED";}finally{busy.set(false);}},"genesis-remote69");thread.setDaemon(true);thread.start();
    }
    public static void inspect(String address,String url){
        if(busy())return;final String neo=ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();
        work("CHECKING",()->{preview=null;download=null;received=0;game=RemoteOffer69.gameAddress(address);endpoint=RemoteOffer69.base(url);client=new RemoteClient69(root(),"1.21.1",neo);var candidate=client.inspect(game,endpoint,cancel::get);if(cancel.get())throw new java.io.InterruptedIOException("REMOTE_CANCELLED");preview=candidate;phase=candidate.trusted()?"TRUSTED":"UNTRUSTED";});
    }
    public static void trust(){
        var candidate=preview;if(candidate==null||busy()||!phase.equals("UNTRUSTED"))return;
        work("TRUSTING",()->{client.trust(candidate);if(cancel.get())throw new java.io.InterruptedIOException("REMOTE_CANCELLED");preview=client.inspect(candidate.offer().game(),candidate.offer().endpoint(),cancel::get);phase="TRUSTED";});
    }
    public static void downloadPackage(){
        var candidate=preview;if(candidate==null||!candidate.trusted()||busy()||!phase.equals("TRUSTED"))return;
        work("DOWNLOADING",()->{received=0;download=client.download(candidate,cancel::get,n->received=n);phase="READY";});
    }
    public static void cancel(){if(busy()){cancel.set(true);phase="CANCELLING";}}
}
