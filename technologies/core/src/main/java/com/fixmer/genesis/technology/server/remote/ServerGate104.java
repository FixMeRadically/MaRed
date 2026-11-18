package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;import java.nio.file.*;import java.nio.channels.*;import java.util.*;
/** Lifetime lease and before-world observation. Absence is not universal proof that an old adapter never opened a world. */
public final class ServerGate104 implements AutoCloseable {
 private final FileChannel channel;private final FileLock lock;
 private ServerGate104(FileChannel c,FileLock l){channel=c;lock=l;}
 public static ServerGate104 beforeWorld(Map<String,String> env,Path actual)throws Exception{
  if(!"104".equals(env.get("GENESIS_INPLACE_VERSION")))return null;var session=DedicatedSession74.open(env);if(session==null||!FirstLaunchFiles.safe(actual).equals(session.profile))throw new java.io.IOException("SERVER104_PROFILE");
  Path path=session.root.resolve("game104.lock");FirstLaunchFiles.safe(path);FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock lock=null;
  try{FirstLaunchFiles.privateMode(path);lock=channel.tryLock();if(lock==null)throw new java.io.IOException("SERVER104_DIRECTORY_BUSY");byte[] key=FirstLaunchFiles.read(session.root.resolve("owner74.key"),32);String launch=env.get("GENESIS_INPLACE_LAUNCH");
   FirstLaunchFiles.write(session.root.resolve("runs").resolve(launch).resolve("world104"),LocalApply67.sign(key,Map.of("schema","104","run",session.config.get("run"),"launch",launch,"generation",Integer.toString(session.generation),"phase","WORLD_OPENING")),false);return new ServerGate104(channel,lock);
  }catch(Exception failed){if(lock!=null)lock.close();channel.close();throw failed;}
 }
 public void close()throws java.io.IOException{try{lock.close();}finally{channel.close();}}
}
