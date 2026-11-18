package com.fixmer.genesis.technology.server.process;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
/** Read-only operator inspection. No prepare/consume/start/override CLI. */
public final class ServerNativeReviewCli {
 private ServerNativeReviewCli(){}
 public static void main(String[] args){try{
  Map<String,Object> result;
  if(args.length==2&&args[0].equals("intent"))result=ServerNativeLaunchIntent.inspect(Path.of(args[1]));
  else if(args.length==2&&args[0].equals("stop"))result=ServerNativeStopBarrier.inspect(Path.of(args[1]));
  else if(args.length==4&&args[0].equals("evidence")){
   var checked=ServerNativeEvidence.read(Path.of(args[1]),args[2],Path.of(args[3]));result=new LinkedHashMap<>();result.put("schema",47);result.put("state","BUNDLE_INTEGRITY_CHECKED");result.put("sha256",checked.sha());result.put("runtimeSourceSha256",checked.runtimeSourceSha());result.put("runtimeJavaSha256",checked.runtimeJavaSha());result.put("historical",true);result.put("currentSourceVerified",false);result.put("productionLaunchAllowed",false);result.put("installAuthority",false);
  }else throw new IOException("Usage: intent INTENT_FILE | stop RUN_DIRECTORY | evidence BUNDLE SHA256 HELPER");
  System.out.println(ServerJson.object(result));
 }catch(IOException|IllegalArgumentException bad){System.err.println("Native review: "+bad.getMessage());System.exit(2);}}
}
