package com.fixmer.mared.technology.server.setup;
import com.google.gson.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
/** One-time local preparation plus startup. Owner independence is never bypassed. */
public final class SimpleServerService103 {
 private static final AtomicBoolean busy=new AtomicBoolean();private static volatile String state="IDLE",error="";
 private SimpleServerService103(){}
 public static boolean busy(){return busy.get()||DedicatedPanelService75.busy();}public static String state(){return state;}public static String error(){return error;}
 public static String pref(String key){try{return DedicatedPanelService75.preferences().get(key).getAsString();}catch(Exception e){return "";}}
 public static void setup(String source){if(!busy.compareAndSet(false,true))return;state="CHECKING";error="";Thread t=new Thread(()->{try{
  if(!pref("root").isBlank())throw new IllegalStateException("SERVER103_ALREADY_CONFIGURED");
  var p=DedicatedPanelService75.preferences();p.addProperty("source",source);DedicatedPanelService75.save(p);
  DedicatedPanelService75.host("init","",source,"","r1");await();if(!DedicatedPanelService75.result().equals("DONE")||pref("root").isBlank())throw new IllegalStateException(errorCode());
  startNow();
 }catch(Exception e){error=code(e.getMessage());state="ERROR";}finally{busy.set(false);}},"genesis-server103-setup");t.setDaemon(true);t.start();}
 public static void start(){if(!busy.compareAndSet(false,true))return;state="STARTING";error="";Thread t=new Thread(()->{try{startNow();}catch(Exception e){error=code(e.getMessage());state="ERROR";}finally{busy.set(false);}},"genesis-server103-start");t.setDaemon(true);t.start();}
 private static void startNow()throws Exception{
  String root=pref("root");if(root.isBlank())throw new IllegalStateException("SERVER103_ROOT_REQUIRED");
  launcher(root);state="STARTING";DedicatedPanelService75.host("run",root,"","","r1");await();
  for(int n=0;n<40;n++){String code=errorCode();if(code.equals("DEDICATED75_START_FROM_TERMINAL")){state="LAUNCHER";return;}if(Files.isRegularFile(Path.of(root).resolve("view75"))){state="STARTED";return;}if(DedicatedPanelService75.result().equals("REFUSED")||DedicatedPanelService75.result().equals("RECOVERY_REQUIRED"))throw new IllegalStateException(code);Thread.sleep(250);}
  if(DedicatedPanelService75.result().equals("START_FROM_TERMINAL")){state="LAUNCHER";return;}throw new IllegalStateException("SERVER103_OWNER_NOT_CONFIRMED");
 }
 private static void await()throws InterruptedException{long end=System.nanoTime()+TimeUnit.MINUTES.toNanos(11);while(DedicatedPanelService75.busy()&&System.nanoTime()<end)Thread.sleep(50);if(DedicatedPanelService75.busy())throw new IllegalStateException("SERVER103_SETUP_TIMEOUT");}
 private static String code(String s){return s!=null&&s.matches("[A-Z][A-Z0-9_]{1,100}")?s:"SERVER103_OPERATION_FAILED";}
 private static String errorCode(){try{Path file=DedicatedPanelService75.bundle().resolve("panel75-error.log");if(Files.isRegularFile(file)&&Files.size(file)<16384)for(String line:Files.readString(file).split("\\R"))if(line.matches("[A-Z][A-Z0-9_]{1,100}"))return line;}catch(Exception ignored){}return "SERVER103_OPERATION_FAILED";}
 private static Process launchHelper(List<String> command,Path home)throws java.io.IOException{var builder=new ProcessBuilder(command).directory(home.toFile()).redirectError(home.resolve("panel75-error.log").toFile());builder.environment().put("PYTHONUTF8","1");builder.environment().put("PYTHONIOENCODING","utf-8");return builder.start();}
 private static void launcher(String root)throws Exception{
  Path home=DedicatedPanelService75.bundle();List<String> args=List.of("-3",home.resolve("tools/dedicated_server75.py").toString(),home.toString(),"launcher103",root);Process process;var command=new ArrayList<String>();command.add("pyw.exe");command.addAll(args);try{process=launchHelper(command,home);}catch(java.io.IOException missing){command.set(0,"py.exe");process=launchHelper(command,home);}
  if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroy();throw new IllegalStateException("SERVER103_SETUP_TIMEOUT");}if(process.exitValue()!=0)throw new IllegalStateException(errorCode());process.getInputStream().close();
 }
}
