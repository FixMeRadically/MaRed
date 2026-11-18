package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.PublicationHealth80;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Read-only local HMAC diagnostics, no subprocess, PID discovery or install action. */
public final class PublicationHealthService80 {
 private final AtomicBoolean busy=new AtomicBoolean();private volatile Map<String,String> data=Map.of();private volatile String result="IDLE",observed="";
 public boolean busy(){return busy.get();}public Map<String,String> data(){return data;}public String result(){return result;}public String observed(){return observed;}
 public void read(String root){if(!busy.compareAndSet(false,true))return;result="WORKING";Thread worker=new Thread(()->{try{var value=PublicationHealth80.inspect(Path.of(root).toAbsolutePath(),System.currentTimeMillis());data=value;observed=root;result="READ";}catch(Exception bad){data=Map.of();observed=root;result="ERROR";}finally{busy.set(false);}},"genesis-publication80-health");worker.setDaemon(true);worker.start();}
}
