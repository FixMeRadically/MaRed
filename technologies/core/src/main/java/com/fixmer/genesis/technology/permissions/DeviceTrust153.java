package com.fixmer.genesis.technology.permissions;
import com.fixmer.genesis.technology.server.firstlaunch.*;import com.fixmer.genesis.technology.server.remote.ControlWire73;import com.fixmer.genesis.technology.storage.AtomicSettings111;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Console-owned device bindings. The controller reads the same file again at consumption time. */
public final class DeviceTrust153 {
 public final Path file;private volatile Map<String,String> trusted;
 public DeviceTrust153(Path directory)throws IOException{Files.createDirectories(FirstLaunchFiles.safe(directory));file=FirstLaunchFiles.safe(directory.resolve("devices153"));trusted=Files.exists(file)?read(file):Map.of();}
 public static Map<String,String> read(Path file)throws IOException{String text=new String(FirstLaunchFiles.read(file,131072),java.nio.charset.StandardCharsets.US_ASCII);if(!text.startsWith("GENESIS-DEVICES153\n")||!text.endsWith("\n"))throw new IOException("AUTH153_FORMAT");var data=new TreeMap<String,String>();for(String line:text.substring("GENESIS-DEVICES153\n".length()).split("\n")){if(line.isEmpty())continue;if(!line.matches("[a-f0-9-]{36}:[a-f0-9]{64}")||data.put(line,"trusted")!=null)throw new IOException("AUTH153_FORMAT");}if(data.size()>1024)throw new IOException("AUTH153_LIMIT");return Map.copyOf(data);}
 private static String key(UUID actor,String fingerprint){if(actor.equals(new UUID(0,0))||fingerprint==null||!fingerprint.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("AUTH153_FINGERPRINT");return actor+":"+fingerprint;}
 public boolean allows(UUID actor,String fingerprint){return trusted.containsKey(key(actor,fingerprint));}
 public static boolean allows(Path file,UUID actor,String fingerprint)throws IOException{return read(file).containsKey(key(actor,fingerprint));}
 public synchronized void change(UUID actor,String fingerprint,boolean allow)throws IOException{String id=key(actor,fingerprint);var next=new TreeMap<>(trusted);if(allow)next.put(id,"trusted");else next.remove(id);if(next.size()>1024||next.keySet().stream().filter(s->s.startsWith(actor+":")).count()>4)throw new IOException("AUTH153_LIMIT");AtomicSettings111.write(file,"GENESIS-DEVICES153\n"+String.join("\n",next.keySet())+(next.isEmpty()?"":"\n"));trusted=Map.copyOf(next);}
}
