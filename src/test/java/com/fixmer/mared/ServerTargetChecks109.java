package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.ServerLocalTarget109;
import java.net.*;import java.util.*;
/** IPv6 scope/address facts are fixtures, not a Windows adapter/runtime certification. */
public final class ServerTargetChecks109 {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("109 check "+checks);}
 static void bad(String input){try{ServerLocalTarget109.parse(input);}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("accepted "+input);}
 public static void main(String[]args)throws Exception{
  String raw="fe80::7100:fcc7:c726:8eb5";
  var p=ServerLocalTarget109.parse(raw);ok(p.host().equals(raw));ok(p.port()==25565);
  p=ServerLocalTarget109.parse("["+raw+"%13]:25566");ok(p.host().equals(raw+"%13"));ok(p.port()==25566);
  p=ServerLocalTarget109.parse("[::1]");ok(p.host().equals("::1")&&p.port()==25565);
  p=ServerLocalTarget109.parse("127.0.0.1:25565");ok(p.host().equals("127.0.0.1")&&p.port()==25565);
  p=ServerLocalTarget109.parse("localhost");ok(p.host().equals("localhost")&&p.port()==25565);
  for(String bad:Arrays.asList(null,""," ","[::1","::1]","[::1]bad","[::1]:0","[::1]:65536","host:bad","host:",":25565","foo/bar","foo\\bar","user@host","host\nname","[::1]:-1"))bad(bad);
  byte[] bytes=InetAddress.getByName(raw).getAddress();var remote=Inet6Address.getByAddress(null,bytes,0);var nic=Inet6Address.getByAddress(null,bytes,13);var other=InetAddress.getByName("fe80::1234");var v4=InetAddress.getByName("192.0.2.9");
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(remote),List.of(nic),List.of(),true)==ServerLocalTarget109.Result.MATCH);
  ok(ServerLocalTarget109.evaluate(25565,25566,List.of(remote),List.of(nic),List.of(),true)==ServerLocalTarget109.Result.PORT_MISMATCH);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(remote),List.of(other),List.of(),true)==ServerLocalTarget109.Result.HOST_NOT_LOCAL);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(remote),List.of(nic),List.of(other),false)==ServerLocalTarget109.Result.BIND_MISMATCH);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(remote),List.of(nic),List.of(nic),false)==ServerLocalTarget109.Result.MATCH);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(remote),List.of(nic),List.of(InetAddress.getByName("::")),false)==ServerLocalTarget109.Result.MATCH);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(remote),List.of(nic),List.of(InetAddress.getByName("0.0.0.0")),false)==ServerLocalTarget109.Result.BIND_MISMATCH);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(v4),List.of(v4),List.of(InetAddress.getByName("0.0.0.0")),false)==ServerLocalTarget109.Result.MATCH);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(),List.of(nic),List.of(),true)==ServerLocalTarget109.Result.HOST_UNRESOLVED);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(InetAddress.getByName("::1")),List.of(),List.of(),true)==ServerLocalTarget109.Result.MATCH);
  ok(ServerLocalTarget109.evaluate(25565,25565,List.of(other,remote),List.of(nic),List.of(),true)==ServerLocalTarget109.Result.MATCH);
  System.out.println("Target109 raw/scoped IPv6, local address bytes, endpoint/port/bind checks passed: "+checks+"; adapter addresses are explicit fixtures.");
 }
}
