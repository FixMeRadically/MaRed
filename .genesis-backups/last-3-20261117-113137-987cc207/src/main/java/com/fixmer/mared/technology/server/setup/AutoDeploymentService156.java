package com.fixmer.mared.technology.server.setup;
import com.fixmer.genesis.technology.scripts.BuildSnapshot84;
import com.fixmer.genesis.technology.server.remote.*;
import com.google.gson.*;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** One owner-local job. Automatic names/sets/preview/signing; explicit Launch authorizes the consent/commit chain. */
public final class AutoDeploymentService156 {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MaRed-apply156");t.setDaemon(true);return t;});
 private static volatile boolean busy;private static volatile String state="IDLE",label="",job="",root="",error="",preparedSeal="",commitRequest="",preparedRun="",preparedSide="BOTH";
 private AutoDeploymentService156(){}
 public static boolean busy(){return busy;}public static String state(){return state;}public static String label(){return label;}public static String error(){return error;}public static String root(){return root;}
 public static boolean available(){try{return !InPlaceServerService104.folder().isBlank()&&Files.isRegularFile(Path.of(InPlaceServerService104.folder()).resolve("server.properties"));}catch(Exception invalid){return false;}}
 public static boolean canLaunch(){try{return !busy&&(state.equals("READY")||state.equals("COMMIT_REJECTED")||state.equals("ERROR")&&!label.isBlank()&&(error.equals("AUTO156_WAITING_FOR_CONSENT")||error.equals("AUTO156_CONNECT_TIMEOUT")||error.equals("AUTO156_DIFFERENT_CONNECTION")||error.equals("AUTO156_DISTRIBUTION_SETUP_REQUIRED")||(error.startsWith("HTTPS78_")||error.startsWith("HTTPS159_"))))&&root.equals(ServerApp104.root(Path.of(InPlaceServerService104.folder())).toString());}catch(Exception invalid){return false;}}
 public static void refresh(){try{if(!root.equals(ServerApp104.root(Path.of(InPlaceServerService104.folder())).toString())||!InPlaceServerService104.matchesFolder128(InPlaceServerService104.folder()))return;}catch(Exception absent){return;}String currentRun=InPlaceServerService104.state().getOrDefault("run","");if(!busy&&!preparedRun.isBlank()&&!currentRun.isBlank()&&!preparedRun.equals(currentRun)){label="";preparedRun="";state="ERROR";error="AUTO156_STALE_JOB";return;}if(state.equals("COMMIT_QUEUED")){
   var deployment=ServerLocal105.deployment(InPlaceServerService104.state(),preparedRun,label,commitRequest);
   state=deployment.phase();error=deployment.error();
  }}
 public static String side(BuildSnapshot84.Snapshot snapshot){try{String value=DedicatedPanelService75.preferences().getAsJsonObject("projectSides156").get(snapshot.projectId()).getAsString();return Set.of("CLIENT","SERVER","BOTH").contains(value)?value:"BOTH";}catch(Exception ignored){return "BOTH";}}
 public static synchronized void prepare(BuildSnapshot84.Snapshot snapshot){
  refresh();if(busy||snapshot==null||state.equals("COMMIT_QUEUED")||state.equals("OFFERING"))return;if(!available()){state="ERROR";error="SERVER156_SELECT_FOLDER";return;}if(state.equals("READY")&&preparedSeal.equals(snapshot.seal())&&preparedSide.equals(side(snapshot))&&canLaunch())return;preparedSeal=snapshot.seal();preparedSide=side(snapshot);String selectedSide=preparedSide;try{root=ServerApp104.root(Path.of(InPlaceServerService104.folder())).toString();}catch(Exception e){state="ERROR";error="SERVER156_SELECT_FOLDER";return;}
  busy=true;state="PREPARING";error="";label="";preparedRun="";commitRequest="";job=UUID.randomUUID().toString();String owner=root,id=job;
  var mc=net.minecraft.client.Minecraft.getInstance();var prefs=ServerConnectionScreen156.forRoot(owner);String game=prefs.has("publication79game")?prefs.get("publication79game").getAsString():"",endpoint=prefs.has("publication79endpoint")?prefs.get("publication79endpoint").getAsString():"https://localhost:8443/";
  IO.execute(()->{Path clientSet=null;try{var checked=BuildSnapshot84.verify(snapshot.directory(),()->Thread.currentThread().isInterrupted());if(!checked.seal().equals(snapshot.seal()))throw new IllegalStateException("SCRIPT84_SNAPSHOT_CHANGED");
   String folder=Path.of(owner).getParent().getParent().toString();
   ensureRunning(owner,folder,mc);
   state="PREPARING";
   mc.execute(()->InPlaceServerService104.poll(folder));
   requireSelected(owner);clientSet=clientSet157();requireSelected(owner);var response=call(List.of("auto-prepare156",owner,snapshot.directory().toString(),clientSet.toString(),id,"--game",game,"--endpoint",endpoint,"--side",selectedSide));label=response.get("label").getAsString();preparedRun=response.get("run").getAsString();state="READY";
  }catch(Exception e){state="ERROR";error=code(e);}finally{if(clientSet!=null)ClientModSet157.cleanup(clientSet);busy=false;}});
 }
 private static void requireSelected(String owner)throws Exception{
  if(!owner.equals(ServerApp104.root(Path.of(InPlaceServerService104.folder())).toString()))
   throw new IllegalStateException("AUTO156_TARGET_CHANGED");
 }
 private static void ensureRunning(String owner,String folder,net.minecraft.client.Minecraft mc)throws Exception{
  boolean attempted=false;
  long deadline=System.nanoTime()+450_000_000_000L;
  while(System.nanoTime()<deadline){
   requireSelected(owner);
   var observed=ServerLocal105.observe(Path.of(folder));
   var start=ServerLocal105.deploymentStart(observed,InPlaceServerService104.working());
   if(start.equals("READY"))return;
   if(start.equals("BLOCKED")){
    String reason=observed.getOrDefault("error","");
    throw new IllegalStateException(reason.isBlank()?"AUTO156_SERVER_NOT_READY":reason);
   }
   state="STARTING_SERVER";
   if(!attempted&&start.equals("START")){
    var queued=new CompletableFuture<Void>();
    mc.execute(()->{
     try{requireSelected(owner);InPlaceServerService104.action(folder,"start");queued.complete(null);}
     catch(Exception invalid){queued.completeExceptionally(invalid);}
    });
    queued.get(5,TimeUnit.SECONDS);
    attempted=true;
   }
   if(attempted&&!InPlaceServerService104.working()){
    String result=InPlaceServerService104.result();
    if(!result.isBlank()&&!result.equals("DONE"))throw new IllegalStateException(result);
   }
   Thread.sleep(500);
  }
  throw new IllegalStateException("AUTO156_SERVER_START_TIMEOUT");
 }
 public static synchronized void launch(){refresh();if(!canLaunch())return;busy=true;state="OFFERING";error="";String owner=root,id=job;
  var prefs=ServerConnectionScreen156.forRoot(owner);String cert=prefs.has("auto156cert")?prefs.get("auto156cert").getAsString():"",key=prefs.has("auto156key")?prefs.get("auto156key").getAsString():"",bind=prefs.has("auto156bind")?prefs.get("auto156bind").getAsString():"127.0.0.1";
  var mc=net.minecraft.client.Minecraft.getInstance();boolean join=mc.getConnection()==null;String folder=Path.of(owner).getParent().getParent().toString();
  IO.execute(()->{try{
   requireSelected(owner);
   if(join){state="CONNECTING";var requested=new java.util.concurrent.atomic.AtomicReference<String>();long deadline=System.nanoTime()+120_000_000_000L;boolean connected=false;
    while(System.nanoTime()<deadline){
     var ready=new CompletableFuture<Boolean>();
     mc.execute(()->{
      try{
       requireSelected(owner);
       if(mc.getConnection()!=null&&mc.level!=null&&mc.player!=null){
        if(requested.get()==null||mc.getCurrentServer()==null||!ServerJoin128.sameAddress(requested.get(),mc.getCurrentServer().ip))
         throw new IllegalStateException("AUTO156_DIFFERENT_CONNECTION");
        ready.complete(true);return;
       }
       InPlaceServerService104.poll(folder);
       if(requested.get()==null&&InPlaceServerService104.matchesFolder128(folder)&&InPlaceServerService104.canJoin128()){
        requested.set(InPlaceServerService104.joinAddress128());
        InPlaceServerService104.join128(mc.screen);
       }
       ready.complete(false);
      }catch(Exception invalid){ready.completeExceptionally(invalid);}
     });
     if(ready.get(5,TimeUnit.SECONDS)){connected=true;break;}
     Thread.sleep(500);
    }
    if(!connected)throw new IllegalStateException("AUTO156_CONNECT_TIMEOUT");
    state="OFFERING";
   }
   requireSelected(owner);
   var response=call(List.of("auto-launch156",owner,id,"--cert",cert,"--key",key,"--bind",bind));commitRequest=response.has("request")?response.get("request").getAsString():"";state=response.get("phase").getAsString();}catch(Exception e){state="ERROR";error=code(e);}finally{busy=false;}});
 }
 private static Path clientSet157()throws Exception{
  var sources=new LinkedHashSet<Path>();
  for(var info:net.neoforged.fml.ModList.get().getModFiles()){
   var ids=info.getMods().stream().map(mod->mod.getModId()).toList();
   if(ids.stream().allMatch(id->Set.of("minecraft","neoforge").contains(id)))continue;
   if(ids.contains("mared")){
    if(ids.stream().anyMatch(id->!id.equals("mared")))throw new java.io.IOException("AUTO157_DEV_MOD_JAR_REQUIRED");
    continue;
   }
   Path source=info.getFile().getFilePath();
   if(source.getFileSystem()!=FileSystems.getDefault()||!Files.isRegularFile(source)||!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))throw new java.io.IOException("AUTO157_DEV_MOD_JAR_REQUIRED");
   sources.add(source.toAbsolutePath().normalize());
  }
  var mared=InPlaceServerService104.modJar(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize());
  return ClientModSet157.stage(DedicatedPanelService75.bundle(),sources,mared.bytes());
 }
 private static String code(Exception error){while((error instanceof ExecutionException||error instanceof CompletionException)&&error.getCause() instanceof Exception cause)error=cause;String code=error.getMessage();return code!=null&&code.matches("[A-Z][A-Z0-9_]{1,120}")?code:"AUTO156_OPERATION_FAILED";}
 private static List<String> helperInput158(List<String> input){
  var result=new ArrayList<String>();for(int i=0;i<input.size();i++){String arg=input.get(i);if(Set.of("--game","--endpoint","--cert","--key","--bind").contains(arg)&&i+1<input.size()&&input.get(i+1).isBlank()){i++;continue;}result.add(arg);}return result;
 }
 private static JsonObject call(List<String> input)throws Exception{
  if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))throw new IllegalStateException("AUTO156_WINDOWS_ONLY");
  Path bundle=DedicatedPanelService75.bundle(),out=Files.createTempFile(bundle,"automatic156-",".json"),err=Files.createTempFile(bundle,"automatic156-",".error");Process process=null;
  try{var args=new ArrayList<>(List.of("-3",bundle.resolve("tools/dedicated_server75.py").toString(),bundle.toString()));args.addAll(helperInput158(input));try{process=ServerHelperProcess116.start("pyw.exe",args,bundle,out,err);}catch(java.io.IOException noWindowless){process=ServerHelperProcess116.start("py.exe",args,bundle,out,err);}
   if(!ServerHelperProcess116.await(process,660,TimeUnit.SECONDS))throw new IllegalStateException("AUTO156_TIMEOUT");
   if(process.exitValue()!=0){String code=Files.size(err)<4096?Files.readString(err).trim():"";throw new IllegalStateException(code.matches("[A-Z][A-Z0-9_]{1,120}")?code:"AUTO156_OPERATION_FAILED");}
   if(Files.size(out)>65536)throw new IllegalStateException("AUTO156_RESULT_LIMIT");return JsonParser.parseString(Files.readString(out)).getAsJsonObject();
  }finally{if(process!=null&&process.isAlive()){process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();}Files.deleteIfExists(out);Files.deleteIfExists(err);}
 }
}
