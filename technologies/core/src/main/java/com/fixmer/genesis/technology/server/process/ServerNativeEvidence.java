package com.fixmer.genesis.technology.server.process;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Integrity of a locally reviewed diagnostic bundle; never an OS/installation capability. */
final class ServerNativeEvidence {
 static final List<String> REPORTS=List.of("build.json","attempt.json","selftest.json","owner-attempt.json","owner-demo.json","java-attempt.json","java-demo.json");
 record Checked(Path directory,String sha,String helperSha,String runtimeSourceSha,String runtimeJavaSha,Map<String,String> fields){Checked{fields=Map.copyOf(fields);}}
 static Checked read(Path directory,String expected,Path helper)throws IOException {
  directory=directory.toAbsolutePath().normalize();ServerFiles.safe(directory);if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("EVIDENCE_DIRECTORY_MISSING");
  Path manifest=directory.resolve("evidence.properties");byte[] bytes=ServerNativeLaunchCapsule.bounded(manifest,ServerFiles.MAX_PROPERTIES);
  if(!hash(expected)||!MessageDigest.isEqual(ServerNativeLaunchCapsule.digest(bytes),HexFormat.of().parseHex(expected)))throw new IOException("EVIDENCE_MANIFEST_HASH");
  Properties p=ServerFiles.read(manifest);Set<String> keys=new HashSet<>(List.of("schema","runtime.schema","state","source.sha256","binary.sha256","java.sha256","owner.java.sha256","host.sha256","job.attempt","owner.attempt","java.attempt","compiler","evidence.sha256","install.authority","production.launch.allowed","authorization.verified"));
  for(String name:REPORTS)keys.add("raw."+name+".sha256");
  if(!p.stringPropertyNames().equals(keys)||!p.getProperty("schema").equals("47")||!p.getProperty("runtime.schema").equals("46")||!p.getProperty("state").equals("READY_FOR_REVIEW")||!p.getProperty("install.authority").equals("false")||!p.getProperty("production.launch.allowed").equals("false")||!p.getProperty("authorization.verified").equals("false")||!Set.of("msvc","mingw").contains(p.getProperty("compiler")))throw new IOException("EVIDENCE_SHAPE");
  for(String key:keys)if(key.endsWith(".sha256")&&!hash(p.getProperty(key)))throw new IOException("EVIDENCE_HASH_FORMAT");
  for(String key:List.of("job.attempt","owner.attempt","java.attempt"))if(!ServerFiles.canonicalUuid(p.getProperty(key)))throw new IOException("EVIDENCE_ATTEMPT");
  var fileFields=new TreeMap<String,String>();for(String name:REPORTS){checkHash(directory.resolve("raw").resolve(name),p.getProperty("raw."+name+".sha256"),ServerFiles.MAX_PROPERTIES);fileFields.put(name,ServerJson.quote(p.getProperty("raw."+name+".sha256")));}
  checkHash(directory.resolve("evidence.json"),p.getProperty("evidence.sha256"),ServerFiles.MAX_PROPERTIES);
  var json=new TreeMap<String,String>();json.put("schema","47");json.put("runtime_schema","46");json.put("state",ServerJson.quote("READY_FOR_REVIEW"));json.put("installAuthority","false");json.put("productionLaunchAllowed","false");json.put("authorizationVerified","false");json.put("files",object(fileFields));
  for(var key:Map.of("source_sha256","source.sha256","binary_sha256","binary.sha256","java_sha256","java.sha256","owner_java_sha256","owner.java.sha256","host_sha256","host.sha256","job_attempt","job.attempt","owner_attempt","owner.attempt","java_attempt","java.attempt","compiler","compiler").entrySet())json.put(key.getKey(),ServerJson.quote(p.getProperty(key.getValue())));
  byte[] summary=ServerNativeLaunchCapsule.bounded(directory.resolve("evidence.json"),ServerFiles.MAX_PROPERTIES);if(!Arrays.equals(summary,(object(json)+"\n").getBytes(StandardCharsets.US_ASCII)))throw new IOException("EVIDENCE_SUMMARY_MISMATCH");
  helper=helper.toAbsolutePath().normalize();String helperSha=HexFormat.of().formatHex(ServerNativeLaunchCapsule.hash(helper,32L*1024*1024));if(!helperSha.equals(p.getProperty("binary.sha256")))throw new IOException("EVIDENCE_HELPER_CHANGED");
  if(!Arrays.equals(bytes,ServerNativeLaunchCapsule.bounded(manifest,ServerFiles.MAX_PROPERTIES)))throw new IOException("EVIDENCE_CHANGED_DURING_READ");
  var fields=new TreeMap<String,String>();for(String key:keys)fields.put(key,p.getProperty(key));return new Checked(directory,expected,helperSha,p.getProperty("source.sha256"),p.getProperty("java.sha256"),fields);
 }
 private static void checkHash(Path file,String expected,int max)throws IOException{if(!HexFormat.of().formatHex(ServerNativeLaunchCapsule.hash(file,max)).equals(expected))throw new IOException("EVIDENCE_FILE_CHANGED");}
 static boolean hash(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
 private static String object(SortedMap<String,String> fields){var out=new StringBuilder("{");boolean first=true;for(var entry:fields.entrySet()){if(!first)out.append(',');first=false;out.append(ServerJson.quote(entry.getKey())).append(':').append(entry.getValue());}return out.append('}').toString();}
}
