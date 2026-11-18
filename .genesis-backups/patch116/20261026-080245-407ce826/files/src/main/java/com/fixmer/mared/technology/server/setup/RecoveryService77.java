package com.fixmer.mared.technology.server.setup;
import com.google.gson.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Local operator only. Never accepts a server-supplied path or takes over a running process. */
public final class RecoveryService77 {
 private final AtomicBoolean busy=new AtomicBoolean();private volatile JsonObject data=new JsonObject();private volatile String result="IDLE",created="";
 public boolean busy(){return busy.get();}public JsonObject data(){return data.deepCopy();}public String result(){return result;}public String created(){return created;}
 public void scan(String root){execute(root,null,false,false);}
 public void repair(String root){execute(root,null,false,true);}
 public void recover(String root,JsonObject selected,boolean older){execute(root,selected.deepCopy(),older,false);}
 private void execute(String root,JsonObject selected,boolean older,boolean repair){if(!busy.compareAndSet(false,true))return;result="WORKING";if(selected==null)data=new JsonObject();
  Thread task=new Thread(()->{Process child=null;try{
   if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))throw new IllegalStateException("DEDICATED77_WINDOWS_ONLY");
   Path home=DedicatedPanelService75.bundle();List<String> args=new ArrayList<>(List.of("-3",home.resolve("tools/dedicated_server75.py").toString(),home.toString(),repair?"repair":selected==null?"catalog":"recover",Path.of(root).toAbsolutePath().toString()));
   if(selected!=null){args.add(selected.get("generation").getAsString());args.add(selected.get("ticket").getAsString());args.add("--java");args.add(DedicatedPanelService75.java().toString());if(older)args.add("--allow-older");}
   Path stdout=Files.createTempFile(home,"recovery77-",".json"),stderr=Files.createTempFile(home,"recovery77-",".error");
   try{
    try{child=launch("pyw.exe",args,home,stdout,stderr);}catch(java.io.IOException absent){child=launch("py.exe",args,home,stdout,stderr);}
    if(!child.waitFor(selected==null&&!repair?180:1200,TimeUnit.SECONDS)){child.destroy();if(!child.waitFor(3,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor(3,TimeUnit.SECONDS);}result="DEDICATED77_OPERATION_TIMEOUT";return;}
    if(child.exitValue()!=0){try{if(Files.size(stdout)<65536)data=JsonParser.parseString(Files.readString(stdout,StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception ignored){}String error=Files.size(stderr)<=4096?Files.readString(stderr).trim():"";result=error.matches("(?:DEDICATED[0-9]{2}|RECOVERY78|HTTPS78|PUBLICATION79)_[A-Z0-9_]{1,80}")?error:"DEDICATED77_REFUSED";return;}
    if(Files.size(stdout)>65536)throw new IllegalStateException("DEDICATED77_OUTPUT");JsonObject value=JsonParser.parseString(Files.readString(stdout,StandardCharsets.UTF_8)).getAsJsonObject();
    if(selected==null&&!repair){data=value;result="SCANNED";}else{created=value.get("root").getAsString();JsonObject p=DedicatedPanelService75.preferences();p.addProperty("root",created);DedicatedPanelService75.save(p);result="CREATED";}
   }finally{Files.deleteIfExists(stdout);Files.deleteIfExists(stderr);}
  }catch(Exception bad){result="DEDICATED77_REFUSED";}finally{busy.set(false);}},"genesis-recovery77");task.setDaemon(true);task.start();
 }
 private static Process launch(String exe,List<String> args,Path home,Path stdout,Path stderr)throws Exception{List<String> cmd=new ArrayList<>();cmd.add(exe);cmd.addAll(args);ProcessBuilder b=new ProcessBuilder(cmd).directory(home.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());b.environment().put("PYTHONUTF8","1");b.environment().put("PYTHONIOENCODING","utf-8");return b.start();}
}
