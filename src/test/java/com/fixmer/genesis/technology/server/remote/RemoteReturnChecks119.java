package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.remote.RemoteSession70;
import java.nio.file.*;
import java.util.*;
/** Real seal/runtime/command check; loaded paths and menu frame are fixtures, not Minecraft. */
public final class RemoteReturnChecks119 {
 public static void main(String[] args)throws Exception{
  var session=RemoteSession70.open(System.getenv());if(args.length>0&&args[0].equals("normal")){if(session.returning119())throw new AssertionError("legacy mode changed");session.status("READY",0,new UUID(0,0));return;}if(!session.returning119())throw new AssertionError("missing menu-only gate");
  List<Path> loaded;try(var files=Files.list(Path.of("mods"))){loaded=files.toList();}
  session.verifyRuntime(loaded,"1.21.1","21.1.264");
  if(args.length>0&&args[0].equals("repeat-status")){var bridge=RemoteRepeat71.open(System.getenv());if(!Set.of("RETURNING","RESTORED").contains(bridge.status().get("phase")))throw new AssertionError("return state missing");return;}
  if(args.length>0&&args[0].equals("reject-join")){
   try{session.command();throw new AssertionError("JOIN accepted");}catch(java.io.IOException expected){if(!expected.getMessage().equals("RETURN119_JOIN_FORBIDDEN"))throw expected;}
  }else{
   session.status("MENU_FRAME_READY",0,new UUID(0,0));long until=System.nanoTime()+120_000_000_000L;
   while(System.nanoTime()<until){var command=session.command();if("STOP".equals(command.get("phase"))){session.status("STOP_REQUESTED",Long.parseLong(command.get("sequence")),UUID.fromString(command.get("request")));break;}Thread.sleep(20);}
  }
  System.out.println("Return119 real signed menu session verified; game frame is fixture");
 }
}
