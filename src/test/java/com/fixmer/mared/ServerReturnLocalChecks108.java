package com.fixmer.mared;
import com.fixmer.mared.technology.server.setup.ServerReturnClient108;
import java.nio.file.*;import java.util.*;
/** Actual local-interface checks only. Does not initialize or connect Minecraft. */
public final class ServerReturnLocalChecks108 {
 static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("local108 "+checks);}
 public static void main(String[]args)throws Exception{
  Path dir=Files.createTempDirectory("Genesis local108 "),p=dir.resolve("server.properties");var local=new ServerReturnClient108.Ticket(UUID.randomUUID(),"127.0.0.1",25565);
  ok(!ServerReturnClient108.matchesLocal(local,dir));Files.writeString(p,"server-port=25565\n");ok(ServerReturnClient108.matchesLocal(local,dir));ok(ServerReturnClient108.matchesLocal(new ServerReturnClient108.Ticket(UUID.randomUUID(),"::1",25565),dir));ok(!ServerReturnClient108.matchesLocal(new ServerReturnClient108.Ticket(UUID.randomUUID(),"127.0.0.1",25566),dir));ok(!ServerReturnClient108.matchesLocal(new ServerReturnClient108.Ticket(UUID.randomUUID(),"203.0.113.7",25565),dir));ok(!ServerReturnClient108.matchesLocal(null,dir));
  Files.writeString(p,"server-port=25565\nserver-ip=127.0.0.1\n");ok(ServerReturnClient108.matchesLocal(local,dir));ok(!ServerReturnClient108.matchesLocal(new ServerReturnClient108.Ticket(UUID.randomUUID(),"::1",25565),dir));
  System.out.println("Return108 actual local-interface/port/bind eligibility checks passed: "+checks+"; Minecraft is not initialized.");
 }
}
