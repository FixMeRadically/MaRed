package com.fixmer.genesis.supervisor;

import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.channels.FileChannel;import java.nio.file.*;import java.security.*;import java.util.*;

/** Local recovery admission for previously guarded launches. OS locks, HMAC binding and journal CAS are distinct checks. */
public final class ColdOwner94 {
 private static final Set<String> FIELDS=Set.of("schema","guard","config","configPath","game","instance","launch","transaction","common","identity","record","created","mac");
 public record Proof(String launch,String record,String transaction){}
 private ColdOwner94(){}
 static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
 private static String pathHash(Path p){return sha(p.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));}
 private static Path recordPath(SupervisorConfig config)throws IOException{Path p=config.gameDir().resolve("config/mared/dev/cold-owner94.properties");RuntimeFiles.safe(p);return p;}
 static void configUnchanged(SupervisorConfig config)throws IOException {
  Path p=config.configPath();RuntimeFiles.safe(p);
  if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("COLD94_CONFIG_CHANGED");
  byte[] bytes;
  try(var input=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){bytes=input.readNBytes(65537);}
  if(bytes.length>65536||!sha(bytes).equals(config.configSha94()))throw new IOException("COLD94_CONFIG_CHANGED");
 }
 private static String canonical(Properties p){StringBuilder b=new StringBuilder("GENESIS_COLD_OWNER94\n");for(String k:new TreeSet<>(FIELDS))if(!k.equals("mac"))b.append(k).append('\0').append(p.getProperty(k,"")).append('\0');return b.toString();}
 private static String uuid(String value)throws IOException{try{if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException();return value;}catch(RuntimeException e){throw new IOException("COLD94_RECORD_INVALID");}}
 static OwnerLease94.Guard acquireCold(SupervisorConfig config)throws IOException {
  // Activation may rewrite config only after obtaining its own guard and checking the owner lock.
  try(var activation=SupervisorRecovery.activationGuard(config.configPath())) {
   OwnerLease94.Guard owner=OwnerLease94.acquire(config.configPath());
   try{configUnchanged(config);return owner;}catch(IOException|RuntimeException failure){
    try{owner.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}
    throw failure;
   }
  }
 }
 static String record(SupervisorConfig config,String launch,String trial)throws IOException {
  if(!OwnerLease94.owned(config.configPath()))throw new IOException("COLD94_NO_OWNER_LEASE");
  configUnchanged(config);uuid(launch);
  var binding=new ModDeployment(config.gameDir()).coldBinding94();var view=binding.view();
  if((view.needsChoice()||view.phase().equals("RESTORE_REQUESTED"))&&!view.transaction().equals(trial))throw new IOException("COLD94_TRIAL_MISMATCH");
  Properties p=new Properties();p.setProperty("schema","94");p.setProperty("guard","93");p.setProperty("config",config.configSha94());p.setProperty("configPath",pathHash(config.configPath()));p.setProperty("game",pathHash(config.gameDir()));p.setProperty("instance",config.instanceId());p.setProperty("launch",launch);p.setProperty("transaction",view.transaction());p.setProperty("common",binding.common());p.setProperty("identity",binding.identity());p.setProperty("record",UUID.randomUUID().toString());p.setProperty("created",Long.toString(System.currentTimeMillis()));p.setProperty("mac",RecoveryAuth92.mac(config.token(),canonical(p)));
  Path target=recordPath(config);Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),".cold-owner94-",".tmp");
  try{
   try(var out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Guarded local launch binding. Not an executable command or a world backup.");}
   try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
   try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
  }finally{Files.deleteIfExists(temp);}
  return p.getProperty("record");
 }
 static Proof verify(SupervisorConfig config,String expectedRecord)throws IOException {
  if(!OwnerLease94.owned(config.configPath())||!ProfileLease92.ownsWriter92(config.gameDir()))throw new IOException("COLD94_ADMISSION_REQUIRED");
  configUnchanged(config);Path file=recordPath(config);
  if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>16384)throw new IOException("COLD94_NO_GUARDED_HISTORY");
  Properties p=RecoveryConnection92.parse(Files.readString(file,StandardCharsets.UTF_8));
  if(!p.stringPropertyNames().equals(FIELDS)||!"94".equals(p.getProperty("schema"))||!"93".equals(p.getProperty("guard"))||!RecoveryAuth92.equal(p.getProperty("mac"),RecoveryAuth92.mac(config.token(),canonical(p))))throw new IOException("COLD94_RECORD_INVALID");
  if(!config.configSha94().equals(p.getProperty("config"))||!pathHash(config.configPath()).equals(p.getProperty("configPath"))||!pathHash(config.gameDir()).equals(p.getProperty("game"))||!config.instanceId().equals(p.getProperty("instance")))throw new IOException("COLD94_OWNER_BINDING_CHANGED");
  uuid(p.getProperty("launch"));uuid(p.getProperty("record"));
  if(expectedRecord!=null&&!expectedRecord.equals(p.getProperty("record")))throw new IOException("COLD94_RECORD_CHANGED");
  com.fixmer.genesis.admission.AdmissionReceipt94.verify(config.gameDir(),config.token(),p.getProperty("record"),p.getProperty("launch"));
  if(!p.getProperty("created","").matches("[0-9]{1,16}"))throw new IOException("COLD94_RECORD_INVALID");
  var binding=new ModDeployment(config.gameDir()).coldBinding94();var v=binding.view();
  if(!v.blocked())throw new IOException("COLD94_NO_PENDING_RECOVERY");
  if(!v.transaction().equals(p.getProperty("transaction"))||!binding.common().equals(p.getProperty("common"))||!v.phase().equals("RESTORED_REVIEW")&&!binding.identity().equals(p.getProperty("identity")))throw new IOException("COLD94_JOURNAL_CHANGED");
  return new Proof(p.getProperty("launch"),p.getProperty("record"),v.transaction());
 }
 public static void main(String[] args){
  try {
   if(args.length!=2||!args[0].startsWith("--config=")||!args[1].equals("--check"))throw new IOException("COLD94_ARGUMENTS");
   SupervisorConfig config=SupervisorConfig.load(args);
   try(var owner=acquireCold(config);var writer=ProfileLease92.writer(config.gameDir())){
    Proof proof=verify(config,null);RecoveryView91 v=new ModDeployment(config.gameDir()).recovery91();
    System.out.println("COLD94_ELIGIBLE_NOW\nphase="+v.phase()+"\nlaunch="+proof.launch()+"\nNo JAR changed; this check grants no later mutation authority.");
   }
  }catch(Exception failure){String code=failure.getMessage();System.err.println(code!=null&&code.matches("(?:COLD94|PROFILE92)_[A-Z0-9_]+")?code:"COLD94_CHECK_REFUSED");System.exit(2);}
 }
}
