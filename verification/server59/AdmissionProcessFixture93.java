package com.fixmer.genesis.supervisor;
import com.fixmer.genesis.admission.ProfileAgent93;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;
/** Explicit JVM fixture. No Minecraft/world/Windows native readiness is simulated as real. */
public final class AdmissionProcessFixture93 {
 public static void main(String[] args)throws Exception {
  String mode=args[0];Path game=Path.of(args[1]);
  if(mode.equals("writer")){try(var w=ProfileLease92.writer(game)){System.out.println("WRITER_READY");System.out.flush();System.in.read();}return;}
  if(mode.equals("probe")){try(var w=ProfileLease92.writer(game)){return;}catch(IOException busy){System.exit(3);}return;}
  if(mode.equals("option")){System.out.println(ProfileLaunch93.forGradle(null,Path.of(args[2]),game));return;}
  if(mode.equals("argfile")) {
   String option=ProfileLaunch93.forGradle(null,Path.of(args[2]),game);
   List<String> command=List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),option,"-cp",System.getProperty("java.class.path"),AdmissionProcessFixture93.class.getName(),"require",game.toString());
   RuntimeFiles runtime=new RuntimeFiles(game);
   Process child=new ProcessBuilder(JavaArgumentFile.wrap(command,runtime)).inheritIO().start();
   System.exit(child.waitFor());return;
  }
  if(mode.equals("parent")) {
   String option=ProfileLaunch93.forGradle(null,Path.of(args[2]),game);
   new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),option,"-cp",System.getProperty("java.class.path"),AdmissionProcessFixture93.class.getName(),"holdfile",game.toString())
       .redirectErrorStream(true).redirectOutput(game.resolve("orphan-fixture93.log").toFile()).start();
   System.in.read();return;
  }
  Files.writeString(game.resolve("main-entered93"),"main");
  if(mode.equals("require")){ProfileLease92.start(game);return;}
  if(mode.equals("bridge")) {
   URL[] cp=Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))).map(Path::of).map(Path::toUri).map(u->{try{return u.toURL();}catch(MalformedURLException e){throw new RuntimeException(e);}}).toArray(URL[]::new);
   try(var loader=new URLClassLoader(cp,ClassLoader.getPlatformClassLoader())) {
    Class<?> alternate=loader.loadClass(ProfileLease92.class.getName());
    if(alternate==ProfileLease92.class)throw new AssertionError("not a distinct loader");
    alternate.getMethod("start",Path.class).invoke(null,game);
    try{alternate.getMethod("writer",Path.class).invoke(null,game);throw new AssertionError("writer accepted");}catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof IOException))throw expected;}
   }
   if(!ProfileAgent93.early(game)||!ProfileAgent93.held(game))throw new AssertionError("lost early reader");
  }
  if(mode.equals("hook"))Runtime.getRuntime().addShutdownHook(new Thread(()->{try{Files.writeString(game.resolve("hook-entered93"),"hook");Thread.sleep(1400);}catch(Exception e){throw new RuntimeException(e);}}));
  if(mode.equals("crash"))throw new IllegalStateException("INTENTIONAL_FIXTURE_BEFORE_MARED");
  if(mode.equals("holdfile")){System.out.println("GAME_MAIN_READY");System.out.flush();while(!Files.exists(game.resolve("fixture-stop93")))Thread.sleep(25);return;}
  System.out.println("GAME_MAIN_READY");System.out.flush();System.in.read();
 }
}
