package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;import java.io.*;import java.util.concurrent.*;
/** Actual JVM fixture. It never loads Minecraft, opens a world or claims game readiness. */
public class RecoveryProcessFixture92 {
 public static void main(String[]args)throws Exception{
  String mode=args[0];Path game=Path.of(args[1]);Files.createDirectories(game);
  if(mode.equals("prepare")){var d=new ModDeployment(game);Path one=jar(game,"1"),two=jar(game,"2");String token=com.fixmer.genesis.supervisor.ApprovalFixture120.stage(d,one,hash(one),Map.of(),"fixture");d.beforeLaunch();d.confirm(token);DevResume(game);com.fixmer.genesis.supervisor.ApprovalFixture120.stage(d,two,hash(two),Map.of("example","1"),"fixture");return;}
  if(mode.equals("reader")){ProfileLease92.start(game);try{ProfileLease92.writer(game);throw new AssertionError("same-JVM writer accepted");}catch(IOException expected){}System.out.println("READER_READY");System.out.flush();System.in.read();return;}
  if(mode.equals("writer")){try(var w=ProfileLease92.writer(game)){System.out.println("WRITER_READY");System.out.flush();System.in.read();}return;}
  if(mode.equals("probe")){try(var w=ProfileLease92.writer(game)){System.out.println("FREE");}catch(IOException busy){System.out.println("BUSY");System.exit(3);}return;}
  if(mode.equals("game")){
   ProfileLease92.start(game);Files.writeString(game.resolve("fixture-starts92.txt"),"START\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   if(Files.exists(game.resolve("fixture-normal-close92.flag"))){GameSessionClient.start();GameSessionClient.normalClosing();if(Files.readString(game.resolve("fixture-normal-close92.flag")).contains("nonzero"))System.exit(17);return;}
   if(ModJarMetadata.read(game.resolve("mods/mared-dev-example.jar")).version().equals("2")){System.err.println("FIXTURE_LOADER_CRASH92 — no Minecraft loaded");System.exit(17);}
   GameSessionClient.start();GameSessionClient.milestone("title_ready");Files.writeString(game.resolve("fixture-menu92.txt"),"FIXTURE_MENU_NOT_MINECRAFT");
   long end=System.nanoTime()+30_000_000_000L;while(System.nanoTime()<end){String raw=System.getenv("GENESIS_SHUTDOWN_FILE");if(raw!=null&&Files.exists(Path.of(raw)))break;Thread.sleep(20);}GameSessionClient.normalClosing();return;
  }
  throw new IllegalArgumentException();
 }
 static void DevResume(Path game)throws Exception{com.fixmer.mared.dev.storage.DevResumeStore.prepare(game.resolve("config/mared/dev/resume.properties"),com.fixmer.mared.dev.storage.DevResumeStore.Target.local("fixture-world"),999,System.currentTimeMillis());}
 static Path jar(Path game,String version)throws Exception{Path path=game.resolve("fixture-v"+version+".jar");try(var z=new java.util.jar.JarOutputStream(Files.newOutputStream(path))){z.putNextEntry(new java.util.jar.JarEntry("META-INF/neoforge.mods.toml"));z.write(("modLoader=\"javafml\"\n[[mods]]\nmodId=\"example\"\nversion=\""+version+"\"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();}return path;}
 static String hash(Path path)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
