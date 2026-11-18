package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.server.remote.ServerApp104;import com.fixmer.mared.Mared;
import java.nio.file.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.zip.*;
/** Deploy app only; starts the same server folder. No client shutdown or external launch UI. */
public final class InPlaceServerService104 {
 private static final AtomicBoolean busy=new AtomicBoolean(),reading=new AtomicBoolean();private static volatile Map<String,String> state=Map.of("phase","STOPPED");private static volatile String result="",selected="";private static long next;
 private InPlaceServerService104(){}
 public static boolean busy(){return busy.get();}public static String result(){return result;}public static Map<String,String> state(){return Map.copyOf(state);}
 public static String folder(){try{var p=DedicatedPanelService75.preferences();if(p.has("folder104"))return p.get("folder104").getAsString();if(p.has("source"))return p.get("source").getAsString();return ServerPanelService66.preferencesRead().get("server").getAsString();}catch(Exception ignored){return "";}}
 public static void folder(String value){var p=DedicatedPanelService75.preferences();p.addProperty("folder104",value);DedicatedPanelService75.save(p);}
 private static Path modJar()throws Exception{
  var candidates=new LinkedHashSet<Path>();var bases=new LinkedHashSet<Path>();try{Path source=Path.of(Mared.class.getProtectionDomain().getCodeSource().getLocation().toURI());if(Files.isRegularFile(source))candidates.add(source);bases.add(source);}catch(Exception ignored){}
  bases.add(Path.of(System.getProperty("user.dir")));bases.add(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
  for(Path origin:bases){Path base=origin;for(int n=0;base!=null&&n<7;n++,base=base.getParent())if(Files.isRegularFile(base.resolve("build.gradle"))&&Files.isDirectory(base.resolve("build/libs"))){try(var paths=Files.list(base.resolve("build/libs"))){for(Path p:paths.limit(64).toList())if(p.getFileName().toString().endsWith(".jar"))candidates.add(p);}break;}}
  Path mods=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("mods");if(Files.isDirectory(mods))try(var paths=Files.list(mods)){for(Path p:paths.limit(256).toList())if(p.getFileName().toString().endsWith(".jar"))candidates.add(p);}
  byte[] marker;try(var in=InPlaceServerService104.class.getResourceAsStream("/com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class")){if(in==null)throw new IOException("SERVER104_CLIENT_BUILD");marker=in.readAllBytes();}
  byte[] bundle;try(var in=InPlaceServerService104.class.getResourceAsStream("/genesis/server59/controller.zip")){if(in==null)throw new IOException("SERVER104_CLIENT_BUILD");bundle=in.readAllBytes();}
  var accepted=new ArrayList<Path>();for(Path candidate:candidates)try(var z=new ZipFile(candidate.toFile())){var e=z.getEntry("com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class");if(e!=null&&Arrays.equals(z.getInputStream(e).readAllBytes(),marker)&&z.getEntry("com/fixmer/mared/Mared.class")!=null&&z.getEntry("genesis/server59/controller.zip")!=null&&Arrays.equals(z.getInputStream(z.getEntry("genesis/server59/controller.zip")).readAllBytes(),bundle))accepted.add(candidate);}catch(IOException ignored){}
  if(accepted.size()!=1)throw new IOException("SERVER104_BUILD_JAR_REQUIRED");return accepted.get(0);
 }
 public static void poll(String folder){if(folder.isBlank())return;if(!folder.equals(selected)){selected=folder;state=Map.of("phase","STOPPED");next=0;}if(System.nanoTime()<next||!reading.compareAndSet(false,true))return;next=System.nanoTime()+1_000_000_000L;Thread t=new Thread(()->{try{var latest=ServerApp104.state(Path.of(folder));if(folder.equals(selected))state=latest;}catch(Exception failed){if(folder.equals(selected))state=Map.of("phase","OWNER_UNAVAILABLE");}finally{reading.set(false);}},"genesis-server104-view");t.setDaemon(true);t.start();}
 public static void action(String folder,String action){if(!busy.compareAndSet(false,true))return;result="";Thread t=new Thread(()->{try{
  Path server=Path.of(folder).toAbsolutePath();folder(folder);
  var prefs=DedicatedPanelService75.preferences();if(action.equals("start")&&prefs.has("root")&&prefs.has("source")&&Path.of(prefs.get("source").getAsString()).toAbsolutePath().normalize().equals(server.normalize())){String old=prefs.get("root").getAsString();if(!old.isBlank()&&Files.exists(Path.of(old).resolve("started74")))throw new IOException("SERVER104_LEGACY_WORLD_REVIEW");}
  Path jar=modJar();Path app;try(var in=Files.newInputStream(jar)){app=ServerApp104.deploy(server,InPlaceServerService104.class.getResourceAsStream("/genesis/server59/controller.zip"),in);}
  Path logs=ServerApp104.root(server).resolve("logs");Files.createDirectories(logs);Process process=ServerApp104.helper(app,action,server,DedicatedPanelService75.java(),logs.resolve("launch.log"));if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroy();throw new IOException("SERVER104_HELPER_TIMEOUT");}
  if(process.exitValue()!=0){String code="SERVER104_START_FAILED";Path log=logs.resolve("launch.log");if(Files.size(log)<32768)for(String line:Files.readString(log).split("\\R"))if(line.matches("[A-Z][A-Z0-9_]{1,100}"))code=line;throw new IOException(code);}result="DONE";
 }catch(Exception failed){try{ServerApp104.diagnostic(Path.of(folder).toAbsolutePath(),failed);}catch(Exception ignored){}String code=failed.getMessage();result=code!=null&&code.matches("[A-Z][A-Z0-9_]{1,100}")?code:"SERVER104_START_FAILED";}finally{busy.set(false);}},"genesis-server104-action");t.setDaemon(true);t.start();}
}
