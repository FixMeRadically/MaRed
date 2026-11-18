package com.fixmer.mared.technology.server.setup;
import com.google.gson.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Local host workflow only. Snapshot/owner/side/label/TTL/seed must still match the preview. */
public final class ScriptPublicationService84 {
 private final AtomicBoolean busy=new AtomicBoolean();private volatile String result="IDLE",ticket="",selection="",createdOwner="",createdLabel="";private volatile JsonObject data=new JsonObject(),composition=new JsonObject();
 public boolean busy(){return busy.get();}public String result(){return result;}public JsonObject data(){return data.deepCopy();}public JsonObject composition(){return composition.deepCopy();}
 private static String seed(){try{return DedicatedPanelService75.preferences().get("export").getAsString();}catch(Exception e){return "";}}
 private static String selected(String owner,Path snapshot,String side,String label,long ttl){return owner+"\n"+snapshot+"\n"+side+"\n"+label+"\n"+ttl+"\n"+seed();}
 public boolean canAuthor(String owner,Path snapshot,String side,String label,long ttl){return !busy()&&!ticket.isEmpty()&&selection.equals(selected(owner,snapshot,side,label,ttl));}
 public boolean canOpen(String owner){return !busy()&&!createdLabel.isEmpty()&&createdOwner.equals(owner);}
 public String createdLabel(){return createdLabel;}
 public void run(boolean author,String owner,Path snapshot,String side,String label,long ttl){
  if(author&&!canAuthor(owner,snapshot,side,label,ttl)){result="SCRIPT84_PREVIEW_REQUIRED";return;}if(!busy.compareAndSet(false,true))return;String current=selected(owner,snapshot,side,label,ttl),expected=ticket,baseline=seed();ticket="";createdOwner=createdLabel="";result="WORKING";
  Thread t=new Thread(()->{try{
   if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))throw new IllegalStateException("SCRIPT84_WINDOWS_ONLY");
   if(owner.isBlank()||!label.matches("[A-Za-z0-9_-]{1,40}")||!Set.of("CLIENT","SERVER","BOTH").contains(side))throw new IllegalStateException("SCRIPT84_ARGUMENTS");
   Path home=DedicatedPanelService75.bundle();JsonObject prepared=composition;String draft;
   if(author){draft=prepared.get("draft").getAsString();}
   else {prepared=call(home,args(home,"script-prepare",owner,snapshot.toString(),baseline,List.of("--side",side)));composition=prepared;draft=prepared.get("draft").getAsString();}
   var extra=new ArrayList<>(List.of("--label",label,"--ttl",Long.toString(ttl)));if(author)extra.addAll(List.of("--ticket",expected));JsonObject response=call(home,args(home,author?"script-author":"script-preview",owner,draft,baseline,extra));data=response;
   if(author){createdOwner=owner;createdLabel=response.get("authored").getAsString();result="AUTHORED";}else{ticket=response.get("ticket").getAsString();selection=current;result="PREVIEW";}
  }catch(Exception e){ticket="";String c=e.getMessage();result=c!=null&&c.matches("(?:SCRIPT84|PUBLICATION79|PUBLICATION83)_[A-Z_]{1,60}")?c:"SCRIPT84_REJECTED";}finally{busy.set(false);}},"genesis-script-publication84");t.setDaemon(true);t.start();
 }
 private static List<String> args(Path home,String action,String owner,String input,String baseline,List<String> extra){var a=new ArrayList<>(List.of("-3",home.resolve("tools/dedicated_server75.py").toString(),home.toString(),action,owner,input));a.addAll(extra);if(!baseline.isBlank())a.addAll(List.of("--baseline",baseline));return a;}
 private static JsonObject call(Path home,List<String> args)throws Exception{
  Path out=Files.createTempFile(home,"script84-",".json"),err=Files.createTempFile(home,"script84-",".error");Process child=null;
  try{try{child=launch("pyw.exe",args,home,out,err);}catch(java.io.IOException missing){child=launch("py.exe",args,home,out,err);}if(!child.waitFor(660,TimeUnit.SECONDS))throw new IllegalStateException("SCRIPT84_TIMEOUT");if(child.exitValue()!=0){String e=Files.size(err)<=4096?Files.readString(err).trim():"";throw new IllegalStateException(e.matches("(?:SCRIPT84|PUBLICATION79|PUBLICATION83)_[A-Z_]{1,60}")?e:"SCRIPT84_REJECTED");}if(Files.size(out)>65536)throw new IllegalStateException("SCRIPT84_RESULT_LIMIT");return JsonParser.parseString(Files.readString(out)).getAsJsonObject();}
  finally{if(child!=null&&child.isAlive()){child.destroy();if(!child.waitFor(3,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor(3,TimeUnit.SECONDS);}}for(Path p:List.of(out,err))try{Files.deleteIfExists(p);}catch(Exception ignored){}}
 }
 private static Process launch(String exe,List<String> args,Path home,Path out,Path err)throws Exception{var c=new ArrayList<String>();c.add(exe);c.addAll(args);var b=new ProcessBuilder(c).directory(home.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile());b.environment().put("PYTHONUTF8","1");b.environment().put("PYTHONIOENCODING","utf-8");return b.start();}
}
