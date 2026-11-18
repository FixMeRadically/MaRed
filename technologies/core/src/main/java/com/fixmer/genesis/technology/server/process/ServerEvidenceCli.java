package com.fixmer.genesis.technology.server.process;
import java.nio.file.*;import java.util.*;
/** Evidence reads never issue process commands. Capture writes only a new private output directory. */
public final class ServerEvidenceCli {
 private ServerEvidenceCli(){}
 public static void main(String[] args){
  try{
   Map<String,Object> result;
   if(args.length==2&&args[0].equals("status"))result=ServerRestartEvidence.bundleStatus(Path.of(args[1]));
   else if(args.length==2&&args[0].equals("inspect"))result=ServerRestartEvidence.inspect(ServerProcessConfig.load(Path.of(args[1])));
   else if(args.length==4&&args[0].equals("begin"))result=ServerRestartEvidence.begin(ServerProcessConfig.load(Path.of(args[1])),args[2],Path.of(args[3]));
   else if(args.length==3&&args[0].equals("probe"))result=ServerRestartEvidence.probe(ServerProcessConfig.load(Path.of(args[1])),args[2]);
   else if(args.length==5&&args[0].equals("capture"))result=ServerRestartEvidence.capture(ServerProcessConfig.load(Path.of(args[1])),args[2],UUID.fromString(args[3]),Path.of(args[4]));
   else throw new IllegalArgumentException("Usage: inspect CONFIG | probe CONFIG EXPECTED_ADAPTER_SHA256 | capture CONFIG EXPECTED_ADAPTER_SHA256 REQUEST_UUID NEW_DIRECTORY | status BUNDLE_DIRECTORY. No process command.");
   System.out.println(ServerJson.object(result));
  }catch(Exception bad){System.err.println(ServerJson.object(Map.of("ok",false,"error",String.valueOf(bad.getMessage()),"processCommandSent",false,"installAuthority",false,"productionLaunchAllowed",false)));System.exit(2);}
 }
}
