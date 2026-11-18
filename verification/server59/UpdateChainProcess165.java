import com.fixmer.genesis.technology.server.remote.*;
import java.nio.file.*;
import java.util.*;

/** Actual JVM/channel fixture. READY and world rendering are modeled, never claimed as Minecraft. */
public final class UpdateChainProcess165 {
 public static void main(String[] args)throws Exception {
  if(args[0].equals("setup")) {
   Path root=Path.of(args[1]);var offer=RemoteUpdateCli69.verifyPublic(root.resolve("public2"));
   UUID account=UUID.nameUUIDFromBytes("OfflinePlayer:Fixture165".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var room=ControlRoom73.create(root.resolve("control73"),root.resolve("identity"),offer,UUID.randomUUID(),UUID.randomUUID(),List.of(account));
   room.decision(account,"ACCEPTED");room.publish(System.currentTimeMillis());
   var config=new HashMap<>(room.config(room.member(account,false),UUID.randomUUID()));config.put("authMode","LOCAL_OFFLINE");
   Files.write(root.resolve("control73.cfg"),ControlWire73.encode(config));return;
  }
  if(args[0].equals("source")&&System.getenv("GENESIS_REMOTE70_ROOT")==null){
   var values=new HashMap<String,String>();for(int i=1;i<args.length;i++){
    if(args[i].contains("=")){var pair=args[i].split("=",2);values.put(pair[0],pair[1]);}
    else if(args[i].startsWith("--")&&i+1<args.length)values.put(args[i],args[++i]);
   }
   Path owner=Path.of(values.get("--fixture-owner")),auth=owner.resolve("run");
   String birth=Long.toString(ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli());
   var context=Map.of("sourceRun",values.get("--fixture-run"),"profile",values.get("--gameDir"),"account",values.get("--uuid"),"mode","LOCAL_OFFLINE","scope","INITIAL","pid",Long.toString(ProcessHandle.current().pid()),"birth",birth);
   com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.write(owner.resolve("source-ready165"),birth.getBytes(java.nio.charset.StandardCharsets.US_ASCII),true);
   String nonce="";long end=System.nanoTime()+120_000_000_000L;
   while(!Files.exists(owner.resolve("source-exit165"))&&System.nanoTime()<end){nonce=answer(auth,context,nonce);Thread.sleep(25);}
   if(!Files.exists(owner.resolve("source-exit165")))throw new AssertionError("source fixture deadline");
   return;
  }
  if(args[0].equals("pump")) {
   Path root=Path.of(args[1]);
   var room=ControlRoom73.restore(root.resolve("control73"),root.resolve("identity"));
   long end=System.nanoTime()+120_000_000_000L;
   while(!Files.exists(root.resolve("stop165"))&&System.nanoTime()<end){
    room.drain();
    if(room.phase().equals("PREPARING")&&room.members().stream().allMatch(m->m.state.equals("PREPARED")))room.commit();
    if(room.phase().equals("COMMITTED")&&!room.admission()&&room.installed()>=room.needed())room.admit();
    room.publish(System.currentTimeMillis());Thread.sleep(100);
   }
   return;
  }
  var session=RemoteSession70.open(System.getenv());
  List<Path> loaded;try(var files=Files.list(Path.of("mods"))){loaded=new ArrayList<>(files.toList());}
  String dev=System.getenv("MOD_CLASSES");if(dev!=null)for(String group:dev.split(";"))loaded.add(Path.of(group.split("%%",2)[1]));
  session.verifyRuntime(loaded,"1.21.1","21.1.264");
  Thread.sleep(Math.min(1500,Math.max(0,Long.getLong("fixture.delay165",0L)*3)));
  session.status("READY",0,new UUID(0,0));
  long last=0,end=System.nanoTime()+90_000_000_000L;int joins=0;String authNonce="";
  while(System.nanoTime()<end){
   authNonce=answer(session.authRoot89(),session.authContext89(),authNonce);
   var command=session.command();
   if(!command.isEmpty()){
    long seq=Long.parseLong(command.get("sequence"));
    if(seq>last){
     last=seq;UUID nonce=UUID.fromString(command.get("request"));String action=command.get("phase");
     if(action.equals("JOIN")){joins++;session.status(joins==1?"JOIN_BUSY":joins==2?"JOIN_FAILED":"WORLD_FRAME_READY",seq,nonce);}
     else if(action.equals("FOCUS")){Files.writeString(Path.of("fixture-joins165"),Integer.toString(joins));return;}
     else if(action.equals("STOP")||action.equals("CANCEL")){session.status("STOP_REQUESTED",seq,nonce);return;}
    }
   }
   Thread.sleep(25);
  }
  throw new AssertionError("fixture child deadline");
 }
 private static String answer(Path root,Map<String,String> context,String previous)throws Exception {
  if(context.isEmpty())return previous;
  try {
  var request=SessionHandoff89.request(root,context,System.currentTimeMillis());
  if(request.isEmpty()||request.get("nonce").equals(previous))return previous;
  Thread.sleep(Math.min(500,Math.max(0,Long.getLong("fixture.delay165",0L))));
  SessionHandoff89.answer(root,request,Map.of("account",context.get("account"),"name","Fixture165","accessToken","","userType","legacy"),System.currentTimeMillis());
  return request.get("nonce");
  } catch(java.io.IOException race) {return previous;}
 }
}
