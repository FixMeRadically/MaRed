package com.fixmer.mared.technology.server.setup;
import com.google.gson.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Local host authoring. No arbitrary paths or signing keys are accepted over the game network. */
public final class PublicationService79 {
 private final AtomicBoolean busy=new AtomicBoolean();private volatile JsonObject data=new JsonObject();private volatile String result="IDLE",previewSelection="",ticket="",authorRoot="";
 public boolean busy(){return busy.get();}public String result(){return result;}public JsonObject data(){return data.deepCopy();}
 private static String selection(String root,String client,String server,String label,long ttl,boolean first,String game,String endpoint){return root+"\n"+client+"\n"+server+"\n"+label+"\n"+ttl+"\n"+baseline()+"\n"+first+"\n"+game+"\n"+endpoint;}
 private static String baseline(){try{return DedicatedPanelService75.preferences().get("export").getAsString();}catch(Exception bad){return "";}}
 public boolean canAuthor(String root,String client,String server,String label,long ttl,boolean first,String game,String endpoint){return !busy()&&!ticket.isEmpty()&&previewSelection.equals(selection(root,client,server,label,ttl,first,game,endpoint));}
 public void run(boolean author,String root,String client,String server,String label,long ttl,boolean first,String game,String endpoint){
  String selected=selection(root,client,server,label,ttl,first,game,endpoint);if(author&&!canAuthor(root,client,server,label,ttl,first,game,endpoint)){result="PUBLICATION79_PREVIEW_REQUIRED";return;}if(!busy.compareAndSet(false,true))return;String expected=ticket,seed=baseline();ticket="";if(!author)authorRoot="";result="WORKING";
  Thread task=new Thread(()->{Path out=null,err=null;try{
   if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))throw new IllegalStateException("PUBLICATION79_WINDOWS_ONLY");
   if(root.isBlank()||client.isBlank()||server.isBlank()||!label.matches("[A-Za-z0-9_-]{1,40}"))throw new IllegalStateException("PUBLICATION79_ARGUMENTS");
   Path home=DedicatedPanelService75.bundle();List<String> args=new ArrayList<>(List.of("-3",home.resolve("tools/dedicated_server75.py").toString(),home.toString(),first?(author?"release-first-author":"release-first-preview"):(author?"release-author":"release-preview"),root,client,server,"--label",label,"--ttl",Long.toString(ttl)));if(author)args.addAll(List.of("--ticket",expected));if(first)args.addAll(List.of("--game",game,"--endpoint",endpoint));if(!first&&!seed.isBlank())args.addAll(List.of("--baseline",seed));
   out=Files.createTempFile(home,"publication79-",".json");err=Files.createTempFile(home,"publication79-",".error");Process p;try{p=launch("pyw.exe",args,home,out,err);}catch(java.io.IOException missing){p=launch("py.exe",args,home,out,err);}
   if(!com.fixmer.genesis.technology.server.remote.ServerHelperProcess116.await(p,660,TimeUnit.SECONDS)){p.destroy();if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();p.waitFor(3,TimeUnit.SECONDS);}throw new IllegalStateException("PUBLICATION79_TIMEOUT");}
   if(p.exitValue()!=0){String e=Files.size(err)<=4096?Files.readString(err).trim():"";throw new IllegalStateException(e.matches("(?:PUBLICATION79|PUBLICATION83)_[A-Z_]{1,60}")?e:"PUBLICATION79_REJECTED");}
   if(Files.size(out)>65536)throw new IllegalStateException("PUBLICATION79_RESULT_LIMIT");data=JsonParser.parseString(Files.readString(out)).getAsJsonObject();if(!author){previewSelection=selected;ticket=data.get("ticket").getAsString();}if(author)authorRoot=root;result=author?"AUTHORED":"PREVIEW";
  }catch(Exception bad){data=new JsonObject();String c=bad.getMessage();result=c!=null&&c.matches("(?:PUBLICATION79|PUBLICATION83)_[A-Z_]{1,60}")?c:"PUBLICATION79_REJECTED";}finally{for(Path p:new Path[]{out,err})if(p!=null)try{Files.deleteIfExists(p);}catch(Exception ignored){}busy.set(false);}},"genesis-publication79");task.setDaemon(true);task.start();
 }
 public boolean canActivate(String root){return !busy()&&authorRoot.equals(root)&&data.has("authored");}
 public void activate(String root,String label){if(!canActivate(root))return;DedicatedPanelService75.host("command",root,"publish-baseline","",label);}
 private static Process launch(String exe,List<String> args,Path home,Path out,Path err)throws Exception{return com.fixmer.genesis.technology.server.remote.ServerHelperProcess116.start(exe,args,home,out,err);}
}
