package com.fixmer.genesis.technology.permissions;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;import java.nio.channels.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.*;import java.util.*;
/** Explicit MaRed authority. Vanilla operator level is deliberately not an input. Local file is checksummed, not a remote credential. */
public final class MaopPolicy113 {
 public enum Role {WORKSPACE,RESTARTER,PUBLISHER,MAINTAINER,ADMIN}
 public static final Set<String> ROOT=Set.of("PING","RESTART","STAGE","OFFER","COMMIT","CANCEL","CONTINUE_WAIT","CLOSE","SHUTDOWN","LOCAL_TEST_ON","LOCAL_TEST_OFF","WORKSPACE_CREATE","CHECKPOINT");
 public static final Map<Role,Set<String>> RIGHTS=Map.of(Role.WORKSPACE,Set.of("PING","WORKSPACE_CREATE"),Role.RESTARTER,Set.of("PING","RESTART"),Role.PUBLISHER,Set.of("PING","STAGE","OFFER","CANCEL"),Role.MAINTAINER,Set.of("PING","RESTART","STAGE","OFFER","CANCEL","COMMIT","CONTINUE_WAIT","CLOSE","CHECKPOINT"),Role.ADMIN,ROOT);
 public record State(long revision,Map<UUID,Role> subjects){public State{if(revision<0||subjects.size()>256)throw new IllegalArgumentException();subjects=Map.copyOf(subjects);}public boolean allows(UUID actor,String action){Role role=actor==null?null:subjects.get(actor);return role!=null&&RIGHTS.get(role).contains(action);}}
 public final Path file;private volatile State state;
 public MaopPolicy113(Path directory)throws IOException{directory=FirstLaunchFiles.safe(directory);Files.createDirectories(directory);FirstLaunchFiles.privateMode(directory);file=FirstLaunchFiles.safe(directory.resolve("permissions113.txt"));Path guard=FirstLaunchFiles.safe(directory.resolve("permissions113.lock"));try(var c=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var lock=c.lock(0,1,false)){FirstLaunchFiles.privateMode(guard);if(Files.exists(file))state=decode(FirstLaunchFiles.read(file,20000));else{state=new State(0,Map.of());FirstLaunchFiles.write(file,encode(state),true);}}}
 public State state(){return state;}
 public synchronized State change(UUID target,Role role,long expected)throws IOException{
  if(target==null||target.equals(new UUID(0,0)))throw new IOException("MAOP113_IDENTITY");Path guard=FirstLaunchFiles.safe(file.resolveSibling("permissions113.lock"));
  try(var c=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var lock=c.tryLock(0,1,false)){
   if(lock==null)throw new IOException("MAOP113_BUSY");State actual=decode(FirstLaunchFiles.read(file,20000));state=actual;if(actual.revision()!=expected)throw new IOException("MAOP113_STALE");if(expected==Long.MAX_VALUE)throw new IOException("MAOP113_LIMIT");var next=new TreeMap<>(actual.subjects());if(role==null)next.remove(target);else next.put(target,role);if(next.size()>256)throw new IOException("MAOP113_LIMIT");State result=new State(expected+1,next);FirstLaunchFiles.write(file,encode(result),true);state=result;return result;
  }catch(OverlappingFileLockException busy){throw new IOException("MAOP113_BUSY",busy);}
 }
 public static void requireController(byte[] key,byte[] marker,UUID run)throws IOException{if(!com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.verify(key,marker).equals(Map.of("schema","113","run",run.toString())))throw new IOException("MAOP113_CONTROLLER_REQUIRED");}
 private static byte[] hash(byte[] b){try{return MessageDigest.getInstance("SHA-256").digest(b);}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
 public static byte[] encode(State s){StringBuilder body=new StringBuilder("GENESIS-MAOP113\nrevision="+s.revision()+"\n");new TreeMap<>(s.subjects()).forEach((id,role)->body.append(id).append('=').append(role).append('\n'));byte[] raw=body.toString().getBytes(StandardCharsets.US_ASCII);return (body+"sha256="+HexFormat.of().formatHex(hash(raw))+"\n").getBytes(StandardCharsets.US_ASCII);}
 public static State decode(byte[] bytes)throws IOException{
  if(bytes.length>20000)throw new IOException("MAOP113_STATE");try{String s=new String(bytes,StandardCharsets.US_ASCII);String[] lines=s.split("\n",-1);if(lines.length<4||!lines[0].equals("GENESIS-MAOP113")||!lines[1].matches("revision=(0|[1-9][0-9]{0,18})")||!lines[lines.length-1].isEmpty())throw new IOException("MAOP113_STATE");long revision=Long.parseLong(lines[1].substring(9));var roles=new TreeMap<UUID,Role>();for(int i=2;i<lines.length-2;i++){String[] p=lines[i].split("=",-1);if(p.length!=2)throw new IOException("MAOP113_STATE");UUID id=UUID.fromString(p[0]);if(id.equals(new UUID(0,0))||!id.toString().equals(p[0])||roles.put(id,Role.valueOf(p[1]))!=null)throw new IOException("MAOP113_STATE");}State state=new State(revision,roles);if(!Arrays.equals(bytes,encode(state)))throw new IOException("MAOP113_STATE");return state;}catch(RuntimeException bad){throw new IOException("MAOP113_STATE",bad);}
 }
}
