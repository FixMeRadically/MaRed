package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.IOException;import java.nio.file.*;import java.util.*;
/** Immutable temporary input only. Never installs jars into the active runClient profile. */
public final class ClientModSet157 {
 private ClientModSet157(){}
 public static Path stage(Path parent,Collection<Path> sources,byte[] mared)throws IOException{
  if(mared.length==0||mared.length>64*1024*1024||sources.size()>255)throw new IOException("AUTO157_CLIENT_SET_LIMIT");
  Path target=Files.createTempDirectory(FirstLaunchFiles.safe(parent),"client-set157-");
  try{
   FirstLaunchFiles.privateMode(target);long total=mared.length;var names=new HashSet<String>();
   String own="mared-"+FirstLaunchFiles.sha(mared).substring(0,16)+".jar";names.add(own.toLowerCase(Locale.ROOT));FirstLaunchFiles.write(target.resolve(own),mared,false);
   for(Path value:new LinkedHashSet<>(sources)){
    Path source=FirstLaunchFiles.safe(value);String name=source.getFileName().toString();
    if(!name.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\\.jar")||!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("AUTO157_CLIENT_JAR_NAME");
    byte[] bytes=FirstLaunchFiles.read(source,64*1024*1024);total+=bytes.length;if(total>512L*1024*1024)throw new IOException("AUTO157_CLIENT_SET_LIMIT");
    String hash=FirstLaunchFiles.sha(bytes);if(!hash.equals(FirstLaunchFiles.hash(source,64*1024*1024)))throw new IOException("AUTO157_CLIENT_INPUT_CHANGED");
    FirstLaunchFiles.write(target.resolve(name),bytes,false);
   }
   return target;
  }catch(IOException|RuntimeException error){cleanup(target);throw error;}
 }
 public static void cleanup(Path folder){try(var paths=Files.walk(folder)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}catch(IOException ignored){}}
}
