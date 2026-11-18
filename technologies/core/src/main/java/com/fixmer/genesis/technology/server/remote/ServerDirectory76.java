package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Explicit connection hints only; this directory never grants publisher trust. */
public final class ServerDirectory76 {
 private ServerDirectory76(){}
 private static Path file(Path root,String game)throws IOException{Path folder=FirstLaunchFiles.safe(root.resolve("servers76"));Files.createDirectories(folder);FirstLaunchFiles.privateMode(folder);return folder.resolve(FirstLaunchFiles.sha(RemoteOffer69.gameAddress(game).getBytes(StandardCharsets.UTF_8))+".server");}
 public static void save(Path root,String game,String endpoint)throws IOException{save(root,game,endpoint,"ONLINE");}
 public static void save(Path root,String game,String endpoint,String mode)throws IOException{game=RemoteOffer69.gameAddress(game);endpoint=RemoteOffer69.base(endpoint);validateMode(game,mode);FirstLaunchFiles.write(file(root,game),ControlWire73.encode(Map.of("schema","76","game",game,"endpoint",endpoint,"mode",mode)),true);}
 private static void validateMode(String game,String mode)throws IOException{if(!Set.of("ONLINE","LOCAL_OFFLINE").contains(mode)||mode.equals("LOCAL_OFFLINE")&&!Set.of("localhost","127.0.0.1","[::1]").contains(game.substring(0,game.lastIndexOf(':'))))throw new IOException("REMOTE70_OFFLINE");}
 private static Map<String,String> entry(Path root,String game)throws IOException{game=RemoteOffer69.gameAddress(game);Path p=file(root,game);if(!Files.exists(p))return Map.of();var d=ControlWire73.decode(FirstLaunchFiles.read(p,8192));if(!Set.of(Set.of("schema","game","endpoint"),Set.of("schema","game","endpoint","mode")).contains(d.keySet())||!d.get("schema").equals("76")||!d.get("game").equals(game)||!RemoteOffer69.base(d.get("endpoint")).equals(d.get("endpoint")))throw new IOException("BOOTSTRAP76_DIRECTORY");validateMode(game,d.getOrDefault("mode","ONLINE"));return d;}
 public static String find(Path root,String game)throws IOException{return entry(root,game).get("endpoint");}
 public static String mode(Path root,String game)throws IOException{return entry(root,game).getOrDefault("mode","ONLINE");}
 public static void forget(Path root,String game)throws IOException{Files.deleteIfExists(file(root,game));}
}
